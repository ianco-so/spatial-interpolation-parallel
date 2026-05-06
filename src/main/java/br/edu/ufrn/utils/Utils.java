package br.edu.ufrn.utils;

import java.util.Locale;

public class Utils {
    public static final String DEFAULT_INPUT_FILE = "data/input_sensors.csv";
    public static final double DEFAULT_TARGET_LAT = 0.0;
    public static final double DEFAULT_TARGET_LON = 0.0;
    public static final double DEFAULT_POWER = 2.0;

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

    public record Sensor(long id, double lat, double lon, double temp) {
    }
}
