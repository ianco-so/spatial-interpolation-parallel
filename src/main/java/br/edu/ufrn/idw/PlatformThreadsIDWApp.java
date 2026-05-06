package br.edu.ufrn.idw;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import br.edu.ufrn.utils.Utils.Sensor;
import br.edu.ufrn.utils.Utils.InterpolatedTarget;
import br.edu.ufrn.utils.Utils.Target;

import static br.edu.ufrn.utils.Utils.DEFAULT_INPUT_FILE;
import static br.edu.ufrn.utils.Utils.DEFAULT_TARGETS_FILE;
import static br.edu.ufrn.utils.Utils.DEFAULT_POWER;

import static br.edu.ufrn.utils.Utils.getStringArg;
import static br.edu.ufrn.utils.Utils.getDoubleArg;
import static br.edu.ufrn.utils.Utils.calculateDistance;
import static br.edu.ufrn.utils.Utils.parseSensor;
import static br.edu.ufrn.utils.Utils.looksLikeHeader;
import static br.edu.ufrn.utils.Utils.printInterpolatedTargets;
import static br.edu.ufrn.utils.Utils.readTargets;
import static br.edu.ufrn.utils.Utils.shouldPrintTargetsInTerminal;


public final class PlatformThreadsIDWApp {

    private PlatformThreadsIDWApp() {
    }

    public static void main(String[] args) {
        Path inputFile = Path.of(getStringArg(args, 0, DEFAULT_INPUT_FILE));
        Path targetFile = Path.of(getStringArg(args, 1, DEFAULT_TARGETS_FILE));
        double power = getDoubleArg(args, 2, DEFAULT_POWER, "power");

        long startNanos = System.nanoTime();

        try {
            List<Sensor> sensors = readSensors(inputFile);
            List<Target> targets = readTargets(targetFile);
            List<InterpolatedTarget> results = interpolateTargets(sensors, targets, power);

            if (shouldPrintTargetsInTerminal(results.size())) {
                printInterpolatedTargets(results);
            }

            double elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

            System.out.printf(Locale.US, "Modo: platform-threads%n");
            System.out.printf(Locale.US, "Arquivo: %s%n", inputFile);
            System.out.printf(Locale.US, "Sensores lidos: %,d%n", sensors.size());
            System.out.printf(Locale.US, "Arquivo de targets: %s%n", targetFile);
            System.out.printf(Locale.US, "Targets lidos: %,d%n", targets.size());
            System.out.printf(Locale.US, "Potencia IDW: %.2f%n", power);
            System.out.printf(Locale.US, "Temperaturas interpoladas: %,d%n", results.size());
            System.out.printf(Locale.US, "Tempo total: %.3f ms%n", elapsedMillis);
        } catch (IOException e) {
            System.err.printf(Locale.US, "Falha ao ler '%s': %s%n", inputFile, e.getMessage());
            System.exit(1);
        }
    }

    public static double interpolate(List<Sensor> sensors, double targetLat, double targetLon, double power) {
        if (sensors.isEmpty()) {
            throw new IllegalArgumentException("A lista de sensores nao pode estar vazia.");
        }
        if (power <= 0.0) {
            throw new IllegalArgumentException("A potencia IDW precisa ser maior que zero.");
        }

        int workerCount = Math.min(Runtime.getRuntime().availableProcessors(), sensors.size());
        if (workerCount <= 1) {
            return interpolateSequential(sensors, targetLat, targetLon, power);
        }

        double[] partialWeightedTemperatureSums = new double[workerCount];
        double[] partialWeightSums = new double[workerCount];
        AtomicReference<Double> exactTemperature = new AtomicReference<>();
        Thread[] workers = new Thread[workerCount];

        int chunkSize = (sensors.size() + workerCount - 1) / workerCount;

        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
            final int slot = workerIndex;
            final int fromIndex = workerIndex * chunkSize;
            final int toIndex = Math.min(sensors.size(), fromIndex + chunkSize);

            workers[workerIndex] = Thread.ofPlatform().start(() -> processChunk(
                    sensors,
                    fromIndex,
                    toIndex,
                    targetLat,
                    targetLon,
                    power,
                    partialWeightedTemperatureSums,
                    partialWeightSums,
                    exactTemperature,
                    slot
            ));
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("A interpolacao concorrente foi interrompida.", e);
            }
        }

        Double exact = exactTemperature.get();
        if (exact != null) {
            return exact;
        }

        double weightedTemperatureSum = 0.0;
        double weightSum = 0.0;

        for (int i = 0; i < workerCount; i++) {
            weightedTemperatureSum += partialWeightedTemperatureSums[i];
            weightSum += partialWeightSums[i];
        }

        return weightedTemperatureSum / weightSum;
    }

    private static List<InterpolatedTarget> interpolateTargets(List<Sensor> sensors, List<Target> targets, double power) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        int workerCount = Math.min(Runtime.getRuntime().availableProcessors(), targets.size());
        if (workerCount <= 1) {
            List<InterpolatedTarget> results = new ArrayList<>(targets.size());

            for (Target target : targets) {
                double temperature = interpolate(sensors, target.lat(), target.lon(), power);
                results.add(new InterpolatedTarget(target.id(), target.lat(), target.lon(), temperature));
            }

            return results;
        }

        int chunkSize = (targets.size() + workerCount - 1) / workerCount;
        Thread[] workers = new Thread[workerCount];
        @SuppressWarnings("unchecked")
        List<InterpolatedTarget>[] partialResults = new ArrayList[workerCount];

        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
            final int slot = workerIndex;
            final int fromIndex = workerIndex * chunkSize;
            final int toIndex = Math.min(targets.size(), fromIndex + chunkSize);

            workers[workerIndex] = Thread.ofPlatform().start(() -> {
                List<InterpolatedTarget> chunkResults = new ArrayList<>(Math.max(0, toIndex - fromIndex));

                for (int i = fromIndex; i < toIndex; i++) {
                    Target target = targets.get(i);
                    double temperature = interpolate(sensors, target.lat(), target.lon(), power);
                    chunkResults.add(new InterpolatedTarget(target.id(), target.lat(), target.lon(), temperature));
                }

                partialResults[slot] = chunkResults;
            });
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("A interpolacao concorrente de targets foi interrompida.", e);
            }
        }

        List<InterpolatedTarget> results = new ArrayList<>(targets.size());
        for (List<InterpolatedTarget> chunkResults : partialResults) {
            if (chunkResults != null) {
                results.addAll(chunkResults);
            }
        }

        return results;
    }

    static List<Sensor> readSensors(Path inputFile) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(inputFile, StandardOpenOption.READ)) {
            var fileSize = fileChannel.size();
            if (fileSize == 0) {
                return new ArrayList<>();
            }

            var numberOfChunks = Runtime.getRuntime().availableProcessors();
            var chunks = getFileSegments(fileChannel, numberOfChunks);

            var readers = new Thread[chunks.length - 1];
            @SuppressWarnings("unchecked")
            List<Sensor>[] results = new ArrayList[chunks.length - 1];

            for (int i = 0; i < chunks.length - 1; i++) {
                final int chunkIdx = i;
                final long chunkStart = chunks[i];
                final long chunkEnd = chunks[i + 1];

                readers[i] = Thread.ofPlatform().start(() -> {
                    try {
                        results[chunkIdx] = readChunk(fileChannel, chunkStart, chunkEnd);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            for (Thread reader : readers) {
                try {
                    reader.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Leitura de arquivo interrompida.", e);
                }
            }

            List<Sensor> allSensors = new ArrayList<>();
            for (List<Sensor> chunk : results) {
                if (chunk != null) {
                    allSensors.addAll(chunk);
                }
            }
            return allSensors;
        }
    }

    private static List<Sensor> readChunk(FileChannel fileChannel, long chunkStart, long chunkEnd) throws IOException {
        int size = (int) (chunkEnd - chunkStart);
        List<Sensor> sensors = new ArrayList<>();

        ByteBuffer bb = fileChannel.map(MapMode.READ_ONLY, chunkStart, size);
        int pos = 0;
        int limit = bb.limit();

        while (pos < limit) {
            int lineStart = pos;
            while (pos < limit && bb.get(pos) != '\n') {
                pos++;
            }
            int lineEnd = pos;
            int lineLength = lineEnd - lineStart;
            if (lineLength > 0) {
                byte[] bytes = new byte[lineLength];
                bb.position(lineStart);
                bb.get(bytes, 0, lineLength);
                String line = new String(bytes, StandardCharsets.UTF_8).trim();
                if (!line.isBlank() && !looksLikeHeader(line)) {
                    sensors.add(parseSensor(line));
                }
            }
            pos++;
        }

        return sensors;
    }

    private static long[] getFileSegments(FileChannel fileChannel, int numberOfChunks) throws IOException {
        long fileSize = fileChannel.size();
        long segmentSize = (fileSize + numberOfChunks - 1) / numberOfChunks;
        long[] chunks = new long[numberOfChunks + 1];
        chunks[0] = 0;

        ByteBuffer probe = ByteBuffer.allocate(8192);

        for (int i = 1; i < numberOfChunks; i++) {
            long approx = i * segmentSize;
            if (approx >= fileSize) {
                chunks[i] = fileSize;
                continue;
            }

            long pos = approx;
            boolean found = false;
            while (pos < fileSize) {
                probe.clear();
                int toRead = (int) Math.min(probe.capacity(), fileSize - pos);
                probe.limit(toRead);
                fileChannel.read(probe, pos);
                probe.flip();
                for (int j = 0; j < toRead; j++) {
                    if (probe.get(j) == '\n') {
                        chunks[i] = pos + j + 1;
                        found = true;
                        break;
                    }
                }
                if (found) break;
                pos += toRead;
            }
            if (!found) {
                chunks[i] = fileSize;
            }
        }

        chunks[numberOfChunks] = fileSize;
        return chunks;
    }

    private static double interpolateSequential(List<Sensor> sensors, double targetLat, double targetLon, double power) {
        double weightedTemperatureSum = 0.0;
        double weightSum = 0.0;

        for (Sensor sensor : sensors) {
            double distance = calculateDistance(sensor.lat(), sensor.lon(), targetLat, targetLon);
            if (distance == 0.0) {
                return sensor.temp();
            }

            double weight = 1.0 / Math.pow(distance, power);
            weightedTemperatureSum += weight * sensor.temp();
            weightSum += weight;
        }

        return weightedTemperatureSum / weightSum;
    }

    private static void processChunk(
            List<Sensor> sensors,
            int fromIndex,
            int toIndex,
            double targetLat,
            double targetLon,
            double power,
            double[] partialWeightedTemperatureSums,
            double[] partialWeightSums,
            AtomicReference<Double> exactTemperature,
            int slot
    ) {
        double weightedTemperatureSum = 0.0;
        double weightSum = 0.0;

        for (int i = fromIndex; i < toIndex; i++) {
            if (exactTemperature.get() != null) {
                break;
            }

            Sensor sensor = sensors.get(i);
            double distance = calculateDistance(sensor.lat(), sensor.lon(), targetLat, targetLon);
            if (distance == 0.0) {
                exactTemperature.compareAndSet(null, sensor.temp());
                return;
            }

            double weight = 1.0 / Math.pow(distance, power);
            weightedTemperatureSum += weight * sensor.temp();
            weightSum += weight;
        }

        partialWeightedTemperatureSums[slot] = weightedTemperatureSum;
        partialWeightSums[slot] = weightSum;
    }
}