package br.edu.ufrn.dsgenerator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

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
    private static final Path MEASUREMENT_FILE = Path.of("./data/input_sensors.csv");

    /**
     * Estimativa do tamanho médio de uma linha em bytes, utilizada para calcular 
     * o total aproximado de registros necessários para atingir o tamanho alvo.
     * <p>Exemplo de linha: {@code 1000000;-23.550520;-46.633308;22.50\n} (aprox. 35 bytes).</p>
     */
    private static final int BYTES_PER_LINE_ESTIMATE = 35; 

    /**
     * Tamanho alvo do arquivo gerado em bytes (1 GB).
     */
    private static final long TARGET_SIZE_BYTES = 1L * 1024 * 1024 * 1024;

    /**
     * Ponto de entrada do gerador de dataset.
     * <p>
     * Executa a criação do diretório de destino (caso não exista) e realiza a 
     * gravação sequencial das linhas no arquivo CSV até que o tamanho alvo 
     * estimado seja atingido.
     * </p>
     *
     * @param args Argumentos de linha de comando (não utilizados nesta versão).
     */
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        
        try {
            Files.createDirectories(MEASUREMENT_FILE.getParent());
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório: " + e.getMessage());
            return;
        }

        long totalRecords = TARGET_SIZE_BYTES / BYTES_PER_LINE_ESTIMATE;
        System.out.printf("Iniciando geração de aproximadamente %,d registros (~1GB)...%n", totalRecords);

        try (BufferedWriter bw = Files.newBufferedWriter(MEASUREMENT_FILE)) {
            bw.write("id;lat;lon;temp\n");
            
            for (int i = 1; i <= totalRecords; i++) {
                /*
                 * Sorteia lat (-90 a 90) e lon (-180 a 180).
                 * ThreadLocalRandom é preferível aqui por sua alta performance 
                 * na geração de números pseudoaleatórios.
                 */
                double lat = ThreadLocalRandom.current().nextDouble(-90.0, 90.0);
                double lon = ThreadLocalRandom.current().nextDouble(-180.0, 180.0);
                
                /*
                 * Simula uma temperatura crível: 30 graus no equador, decaindo 
                 * linearmente para -20 graus nos polos. Aplica-se um desvio 
                 * padrão de 5 graus para criar ruído.
                 */
                double baseTemp = 30.0 - (Math.abs(lat) / 90.0) * 50.0;
                double temp = ThreadLocalRandom.current().nextGaussian(baseTemp, 5.0);
                
                // Formatação garantindo ponto decimal (evita quebra do separador CSV)
                String line = String.format(Locale.US, "%d;%.6f;%.6f;%.2f\n", i, lat, lon, temp);
                
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