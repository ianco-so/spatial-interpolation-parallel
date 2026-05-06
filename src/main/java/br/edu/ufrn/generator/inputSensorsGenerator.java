package br.edu.ufrn.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static br.edu.ufrn.utils.Utils.DEFAULT_INPUT_FILE;
import static br.edu.ufrn.utils.Utils.DEFAULT_TARGETS_FILE;
import static br.edu.ufrn.utils.Utils.coordinateKey;
import static br.edu.ufrn.utils.Utils.getBooleanArg;
import static br.edu.ufrn.utils.Utils.getLongArg;
import static br.edu.ufrn.utils.Utils.getStringArg;
import static br.edu.ufrn.utils.Utils.readTargetCoordinateKeys;

/**
 * Utilitário para geração de um dataset massivo (aprox. 1GB) de coordenadas espaciais e temperaturas.
 * <p>
 * Este gerador cria um arquivo CSV contendo dados simulados de sensores espaciais, 
 * projetado especificamente para ser utilizado como carga de trabalho em algoritmos de 
 * Interpolação Espacial concorrentes.
 * </p>
 * <p>
 * Os dados gerados seguem um padrão geográfico simplificado onde a temperatura 
 * decai conforme a latitude se afasta do equador, acrescida de um ruído gaussiano 
 * para simular variações climáticas locais.
 * </p>
 *
 * @author <a href="https://github.com/ianco-so">Ianco</a>
 * @version 1.0
 */
public class inputSensorsGenerator {

    /**
     * Caminho do arquivo de saída onde o dataset gerado será gravado.
     */
    private static final Path MEASUREMENT_FILE = Path.of(DEFAULT_INPUT_FILE);

    /**
     * Tamanho alvo do arquivo gerado em bytes (1 GB).
     */
    private static final long TARGET_SIZE_BYTES = 1L * 1024 * 1024 * 1024;

    /**
     * Estimativa do tamanho médio de uma linha em bytes, utilizada para calcular
     * o total aproximado de registros necessários para atingir o tamanho alvo.
     * <p>Exemplo de linha: {@code 1000000;-23.550520;-46.633308;22.50\n} (aprox. 35 bytes).</p>
     */
    private static final int BYTES_PER_LINE_ESTIMATE = 35;

    private static final long DEFAULT_TOTAL_RECORDS = TARGET_SIZE_BYTES / BYTES_PER_LINE_ESTIMATE;

    private static final boolean DEFAULT_CONSIDER_TARGETS = false;

    /**
     * Ponto de entrada do gerador de dataset.
     * <p>
     * Args opcionais:
     * considerTargets, targetsFile, totalRecords, outputFile
     * </p>
     */
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        boolean considerTargets = getBooleanArg(args, 0, DEFAULT_CONSIDER_TARGETS, "considerar targets");
        Path targetsFile = Path.of(getStringArg(args, 1, DEFAULT_TARGETS_FILE));
        long totalRecords = getLongArg(args, 2, DEFAULT_TOTAL_RECORDS, "quantidade de registros");
        Path measurementFile = Path.of(getStringArg(args, 3, MEASUREMENT_FILE.toString()));
        Set<String> targetKeys = Set.of();

        try {
            Path parent = measurementFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório: " + e.getMessage());
            return;
        }

        if (considerTargets) {
            try {
                targetKeys = readTargetCoordinateKeys(targetsFile);
                System.out.printf("Considerando %,d targets de %s%n", targetKeys.size(), targetsFile);
            } catch (IOException e) {
                System.err.printf("Nao foi possivel ler targets em '%s': %s%n", targetsFile, e.getMessage());
                System.err.println("Seguindo sem filtrar os sensores pelos targets.");
                targetKeys = Set.of();
            }
        }

        System.out.printf("Iniciando geração de aproximadamente %,d registros (~1GB)...%n", totalRecords);

        try (BufferedWriter bw = Files.newBufferedWriter(measurementFile)) {
            bw.write("id;lat;lon;temp\n");

            for (int i = 1; i <= totalRecords; i++) {
                double lat;
                double lon;
                String key;

                do {
                    lat = ThreadLocalRandom.current().nextDouble(-90.0, 90.0);
                    lon = ThreadLocalRandom.current().nextDouble(-180.0, 180.0);
                    key = coordinateKey(lat, lon);
                } while (considerTargets && targetKeys.contains(key));

                double baseTemp = 30.0 - (Math.abs(lat) / 90.0) * 50.0;
                double temp = ThreadLocalRandom.current().nextGaussian(baseTemp, 5.0);

                String line = String.format(Locale.US, "%d;%.6f;%.6f;%.2f%n", i, lat, lon, temp);
                bw.write(line);

                if (i % 5_000_000 == 0) {
                    System.out.printf("Gravados %,d registros em %d ms%n", i, (System.currentTimeMillis() - start));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar o arquivo: " + e.getMessage());
        }

        System.out.printf("Processo concluído. Arquivo criado em %d ms%n", (System.currentTimeMillis() - start));
    }
}