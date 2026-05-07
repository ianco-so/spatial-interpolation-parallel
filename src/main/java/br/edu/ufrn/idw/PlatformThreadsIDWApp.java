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
        var inputFile = Path.of(getStringArg(args, 0, DEFAULT_INPUT_FILE));
        var targetFile = Path.of(getStringArg(args, 1, DEFAULT_TARGETS_FILE));
        var power = getDoubleArg(args, 2, DEFAULT_POWER, "power");

        var startNanos = System.nanoTime();

        try {
            var sensors = readSensors(inputFile);
            var targets = readTargets(targetFile);
            var results = interpolateTargets(sensors, targets, power);

            if (shouldPrintTargetsInTerminal(results.size())) {
                printInterpolatedTargets(results);
            }

            var elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

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

    private static List<InterpolatedTarget> interpolateTargets(List<Sensor> sensors, List<Target> targets, double power) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        var workerCount = Math.min(Runtime.getRuntime().availableProcessors(), targets.size());
        var workers = new Thread[workerCount];
        var results = new InterpolatedTarget[targets.size()];
        var chunkSize = (targets.size() + workerCount - 1) / workerCount;

        for (var i = 0; i < workerCount; i++) {
            final var fromIdx = i * chunkSize;
            final var toIdx = Math.min(targets.size(), fromIdx + chunkSize);
            workers[i] = Thread.ofPlatform().start(() -> {
                for (var targetIndex = fromIdx; targetIndex < toIdx; targetIndex++) {
                    var target = targets.get(targetIndex);
                    var temperature = interpolateSequential(sensors, target.lat(), target.lon(), power);
                    results[targetIndex] = new InterpolatedTarget(target.id(), target.lat(), target.lon(), temperature);
                }
            });
        }

        for (var worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("A interpolacao concorrente de targets foi interrompida.", e);
            }
        }

        return new ArrayList<>(java.util.Arrays.asList(results));
    }

    static List<Sensor> readSensors(Path inputFile) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(inputFile, StandardOpenOption.READ)) {
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
        var fileSize = fileChannel.size();
        var segmentSize = (fileSize + numberOfChunks - 1) / numberOfChunks;
        var chunks = new long[numberOfChunks + 1];
        chunks[0] = 0;

        var probe = ByteBuffer.allocate(8192);

        for (int i = 1; i < numberOfChunks; i++) {
            var approxBorder = i * segmentSize;
            if (approxBorder >= fileSize) {
                chunks[i] = fileSize;
                continue;
            }

            var pos = approxBorder;
            var found = false;
            while (pos < fileSize) {
                probe.clear();
                var toRead = (int) Math.min(probe.capacity(), fileSize - pos);
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
}