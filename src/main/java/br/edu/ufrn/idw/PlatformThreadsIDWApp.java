package br.edu.ufrn.idw;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class PlatformThreadsIDWApp {

    private static final String DEFAULT_INPUT_FILE = "data/sensors_1gb.csv";
    private static final double DEFAULT_TARGET_LAT = 0.0;
    private static final double DEFAULT_TARGET_LON = 0.0;
    private static final double DEFAULT_POWER = 2.0;

    private PlatformThreadsIDWApp() {
    }

    public static void main(String[] args) {
        Path inputFile = Path.of(getStringArg(args, 0, DEFAULT_INPUT_FILE));
        double targetLat = getDoubleArg(args, 1, DEFAULT_TARGET_LAT, "latitude");
        double targetLon = getDoubleArg(args, 2, DEFAULT_TARGET_LON, "longitude");
        double power = getDoubleArg(args, 3, DEFAULT_POWER, "power");

        long startNanos = System.nanoTime();

        try {
            List<Sensor> sensors = readSensors(inputFile);
            double result = interpolate(sensors, targetLat, targetLon, power);
            double elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

            System.out.printf(Locale.US, "Modo: platform-threads%n");
            System.out.printf(Locale.US, "Arquivo: %s%n", inputFile);
            System.out.printf(Locale.US, "Sensores lidos: %,d%n", sensors.size());
            System.out.printf(Locale.US, "Ponto alvo: lat=%.6f lon=%.6f%n", targetLat, targetLon);
            System.out.printf(Locale.US, "Potencia IDW: %.2f%n", power);
            System.out.printf(Locale.US, "Temperatura interpolada: %.6f%n", result);
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

    static List<Sensor> readSensors(Path inputFile) throws IOException {
        List<Sensor> sensors = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            String line = reader.readLine();
            if (line == null) {
                return sensors;
            }

            if (!looksLikeHeader(line)) {
                sensors.add(parseSensor(line));
            }

            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    sensors.add(parseSensor(line));
                }
            }
        }

        return sensors;
    }

    private static double interpolateSequential(List<Sensor> sensors, double targetLat, double targetLon, double power) {
        double weightedTemperatureSum = 0.0;
        double weightSum = 0.0;

        for (Sensor sensor : sensors) {
            double distance = distance(sensor.lat(), sensor.lon(), targetLat, targetLon);
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
            double distance = distance(sensor.lat(), sensor.lon(), targetLat, targetLon);
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

    private static boolean looksLikeHeader(String line) {
        return line.trim().toLowerCase(Locale.ROOT).startsWith("id;");
    }

    static Sensor parseSensor(String line) {
        int firstSeparator = line.indexOf(';');
        int secondSeparator = line.indexOf(';', firstSeparator + 1);
        int thirdSeparator = line.indexOf(';', secondSeparator + 1);

        if (firstSeparator < 0 || secondSeparator < 0 || thirdSeparator < 0) {
            throw new IllegalArgumentException("Linha de sensor invalida: " + line);
        }

        long id = Long.parseLong(line.substring(0, firstSeparator).trim());
        double lat = Double.parseDouble(line.substring(firstSeparator + 1, secondSeparator).trim());
        double lon = Double.parseDouble(line.substring(secondSeparator + 1, thirdSeparator).trim());
        double temp = Double.parseDouble(line.substring(thirdSeparator + 1).trim());

        return new Sensor(id, lat, lon, temp);
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double deltaLat = lat1 - lat2;
        double deltaLon = lon1 - lon2;
        return Math.sqrt(deltaLat * deltaLat + deltaLon * deltaLon);
    }

    private static String getStringArg(String[] args, int index, String defaultValue) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }
        return args[index];
    }

    private static double getDoubleArg(String[] args, int index, double defaultValue, String label) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido para " + label + ": " + args[index], e);
        }
    }

    public record Sensor(long id, double lat, double lon, double temp) {
    }
}