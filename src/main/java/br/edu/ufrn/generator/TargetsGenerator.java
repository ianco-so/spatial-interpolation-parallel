package br.edu.ufrn.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static br.edu.ufrn.utils.Utils.DEFAULT_TARGETS_FILE;
import static br.edu.ufrn.utils.Utils.coordinateKey;
import static br.edu.ufrn.utils.Utils.getIntArg;
import static br.edu.ufrn.utils.Utils.getStringArg;

public final class TargetsGenerator {

    private static final int DEFAULT_TARGET_COUNT = 20;
    private static final Path TARGETS_FILE = Path.of(DEFAULT_TARGETS_FILE);

    private TargetsGenerator() {
    }

    public static void main(String[] args) {
        int targetCount = getIntArg(args, 0, DEFAULT_TARGET_COUNT, "quantidade de targets");
        Path outputFile = Path.of(getStringArg(args, 1, TARGETS_FILE.toString()));

        if (targetCount <= 0) {
            throw new IllegalArgumentException("A quantidade de targets precisa ser maior que zero.");
        }

        long start = System.currentTimeMillis();

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório: " + e.getMessage());
            return;
        }

        System.out.printf("Gerando %,d targets em %s...%n", targetCount, outputFile);

        Set<String> usedCoordinates = new HashSet<>(Math.max(16, targetCount * 2));

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write("id;lat;lon\n");

            for (int i = 1; i <= targetCount; i++) {
                double lat;
                double lon;
                String key;

                do {
                    lat = ThreadLocalRandom.current().nextDouble(-90.0, 90.0);
                    lon = ThreadLocalRandom.current().nextDouble(-180.0, 180.0);
                    key = coordinateKey(lat, lon);
                } while (!usedCoordinates.add(key));

                writer.write(String.format(Locale.US, "%d;%.6f;%.6f%n", i, lat, lon));

                if (i % 1_000_000 == 0) {
                    System.out.printf("Gravados %,d targets em %d ms%n", i, (System.currentTimeMillis() - start));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar o arquivo: " + e.getMessage());
        }

        System.out.printf("Processo concluído. Arquivo criado em %d ms%n", (System.currentTimeMillis() - start));
    }
}