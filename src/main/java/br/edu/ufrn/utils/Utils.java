package br.edu.ufrn.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Utils {
    public static final String DEFAULT_INPUT_FILE     = "data/input_sensors.csv";
    public static final String DEFAULT_TARGETS_FILE   = "data/input_targets.csv";
    public static final int    TARGET_PRINT_THRESHOLD = 500;
    public static final double DEFAULT_TARGET_LAT     = 0.0;
    public static final double DEFAULT_TARGET_LON     = 0.0;
    public static final double DEFAULT_POWER          = 2.0;

    public static String getStringArg(String[] args, int index, String defaultValue) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }
        return args[index];
    }

    public static double getDoubleArg(String[] args, int index, double defaultValue, String label) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(args[index]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido para " + label + ": " + args[index], e);
        }
    }

    public static int getIntArg(String[] args, int index, int defaultValue, String label) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido para " + label + ": " + args[index], e);
        }
    }

    public static long getLongArg(String[] args, int index, long defaultValue, String label) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor invalido para " + label + ": " + args[index], e);
        }
    }

    public static boolean getBooleanArg(String[] args, int index, boolean defaultValue, String label) {
        if (args.length <= index || args[index].isBlank()) {
            return defaultValue;
        }

        String value = args[index].trim().toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }

        throw new IllegalArgumentException("Valor invalido para " + label + ": " + args[index]);
    }

    public static double calculateDistance(double x1, double y1, double x2, double y2) {
        var deltaX = x1 - x2;
        var deltaY = y1 - y2;
        return Math.sqrt(Math.pow(deltaX, 2)+ Math.pow(deltaY, 2));
    }

    public static Sensor parseSensor(String line) {
        var firstSeparator  = line.indexOf(';');
        var secondSeparator = line.indexOf(';', firstSeparator + 1);
        var thirdSeparator  = line.indexOf(';', secondSeparator + 1);

        if (firstSeparator < 0 || secondSeparator < 0 || thirdSeparator < 0) {
            throw new IllegalArgumentException("Linha de sensor invalida: " + line);
        }
        try {
            var id = Long.parseLong(line.substring(0, firstSeparator).trim());
            var lat = Double.parseDouble(line.substring(firstSeparator + 1, secondSeparator).trim());
            var lon = Double.parseDouble(line.substring(secondSeparator + 1, thirdSeparator).trim());
            var temp = Double.parseDouble(line.substring(thirdSeparator + 1).trim());

            return new Sensor(id, lat, lon, temp);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Linha de sensor com formato numerico invalido: " + line, e);
        }
    }

    public static boolean looksLikeHeader(String line) {
        return line.trim().toLowerCase(Locale.ROOT).startsWith("id;");
    }

    public static boolean shouldPrintTargetsInTerminal(int targetCount) {
        return targetCount < TARGET_PRINT_THRESHOLD;
    }

    public static String coordinateKey(double lat, double lon) {
        return String.format(Locale.US, "%.6f;%.6f", lat, lon);
    }

    public static List<Target> readTargets(Path targetFile) throws IOException {
        List<Target> targets = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(targetFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || looksLikeHeader(line)) {
                    continue;
                }

                String[] parts = line.split(";");
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Linha de target invalida: " + line);
                }

                long id = Long.parseLong(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                double lon = Double.parseDouble(parts[2].trim());
                targets.add(new Target(id, lat, lon));
            }
        }

        return targets;
    }

    public static void printInterpolatedTargets(List<InterpolatedTarget> targets) {
        for (InterpolatedTarget target : targets) {
            System.out.printf(
                    Locale.US,
                    "Target %d: lat=%.6f lon=%.6f temp=%.6f%n",
                    target.id(),
                    target.lat(),
                    target.lon(),
                    target.temperature()
            );
        }
    }

    public static Set<String> readTargetCoordinateKeys(Path targetFile) throws IOException {
        Set<String> targetKeys = new HashSet<>();

        if (!Files.exists(targetFile)) {
            return targetKeys;
        }

        try (BufferedReader reader = Files.newBufferedReader(targetFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || looksLikeHeader(line)) {
                    continue;
                }

                String[] parts = line.split(";");
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Linha de target invalida: " + line);
                }

                double lat = Double.parseDouble(parts[1].trim());
                double lon = Double.parseDouble(parts[2].trim());
                targetKeys.add(coordinateKey(lat, lon));
            }
        }

        return targetKeys;
    }

    public record Target(long id, double lat, double lon) {
    }

    public record InterpolatedTarget(long id, double lat, double lon, double temperature) {
    }

    public record Sensor(long id, double lat, double lon, double temp) {
    }
}
