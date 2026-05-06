package br.edu.ufrn.idw;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static br.edu.ufrn.utils.Utils.Sensor;
import static br.edu.ufrn.utils.Utils.InterpolatedTarget;
import static br.edu.ufrn.utils.Utils.Target;

import static br.edu.ufrn.utils.Utils.DEFAULT_INPUT_FILE;
import static br.edu.ufrn.utils.Utils.DEFAULT_TARGETS_FILE;
import static br.edu.ufrn.utils.Utils.DEFAULT_POWER;
import static br.edu.ufrn.utils.Utils.printInterpolatedTargets;
import static br.edu.ufrn.utils.Utils.readTargets;
import static br.edu.ufrn.utils.Utils.getStringArg;
import static br.edu.ufrn.utils.Utils.getDoubleArg;
import static br.edu.ufrn.utils.Utils.calculateDistance;
import static br.edu.ufrn.utils.Utils.parseSensor;
import static br.edu.ufrn.utils.Utils.looksLikeHeader;
import static br.edu.ufrn.utils.Utils.shouldPrintTargetsInTerminal;

public final class SerialIDWApp {

    private SerialIDWApp() {
    }

    public static void main(String[] args) {
        var inputFile   = Path.of(getStringArg(args, 0, DEFAULT_INPUT_FILE));

        var startNanos = System.nanoTime();

        try {
            var sensors     = readSensors(inputFile);
            var targetFile  = Path.of(getStringArg(args, 1, DEFAULT_TARGETS_FILE));
            var power       = getDoubleArg(args, 2, DEFAULT_POWER, "power");
            var targets     = readTargets(targetFile);
            var results     = interpolateTargets(sensors, targets, power);

            if (shouldPrintTargetsInTerminal(results.size())) {
                printInterpolatedTargets(results);
            }

            var elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000.0;

            System.out.printf(Locale.US, "Modo: serial%n");
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

        List<InterpolatedTarget> results = new ArrayList<>(targets.size());

        for (Target target : targets) {
            double temperature = interpolate(sensors, target.lat(), target.lon(), power);
            results.add(new InterpolatedTarget(target.id(), target.lat(), target.lon(), temperature));
        }

        return results;
    }

    private static double interpolate(List<Sensor> sensors, double targetLat, double targetLon, double power) {
        if (sensors.isEmpty()) {
            throw new IllegalArgumentException("A lista de sensores nao pode estar vazia.");
        }
        if (power <= 0.0) {
            throw new IllegalArgumentException("A potencia IDW precisa ser maior que zero.");
        }

        var weightedTemperatureSum = 0.0;
        var weightSum = 0.0;

        for (var sensor : sensors) {
            var distance = calculateDistance(sensor.lat(), sensor.lon(), targetLat, targetLon);
            if (distance == 0.0) {
                return sensor.temp();
            }

            var weight = 1.0 / Math.pow(distance, power);
            weightedTemperatureSum += weight * sensor.temp();
            weightSum += weight;
        }

        return weightedTemperatureSum / weightSum;
    }

    private static List<Sensor> readSensors(Path inputFile) throws IOException {
        List<Sensor> sensors = new ArrayList<>();

        try (var reader = Files.newBufferedReader(inputFile)) {
            var line = reader.readLine();
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
}