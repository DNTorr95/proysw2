package bo.edu.uagrm.psicologia.comun;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de arquitectura que no se pueden romper (verificación (a) del punto 12 y envío centralizado). */
class ArquitecturaTest {

    private static final Path RAIZ = Path.of("..").toAbsolutePath().normalize();

    /** docker-compose.yml normalizado a LF (en Windows puede tener CRLF). */
    private static String compose() throws IOException {
        return Files.readString(RAIZ.resolve("docker-compose.yml")).replace("\r\n", "\n");
    }

    private static String bloqueServicio(String compose, String servicio) {
        int inicio = compose.indexOf("\n  " + servicio + ":");
        assertThat(inicio).as("servicio %s en docker-compose.yml", servicio).isPositive();
        int fin = compose.length();
        var m = java.util.regex.Pattern.compile("\n  [a-z0-9-]+:\n").matcher(compose);
        if (m.find(inicio + 3)) fin = m.start();
        return compose.substring(inicio, fin);
    }

    @Test
    void rpaYServiciosInteligentesNoTienenCredencialesDeBaseDeDatos() throws IOException {
        String compose = compose();
        for (String servicio : List.of("rpa-worker", "servicios-ia")) {
            String bloque = bloqueServicio(compose, servicio);
            assertThat(bloque).as(servicio).doesNotContain("DB_", "POSTGRES", "jdbc:", "postgres:");
            assertThat(bloque).as(servicio).contains("SERVICE_KEY");
        }
        assertThat(bloqueServicio(compose, "backend")).contains("DB_PASSWORD");
    }

    @Test
    void serviciosInternosNoSePublicanAlExterior() throws IOException {
        String compose = compose();
        for (String servicio : List.of("rpa-worker", "servicios-ia", "backend", "postgres")) {
            assertThat(bloqueServicio(compose, servicio)).as(servicio).doesNotContain("ports:");
        }
    }

    @Test
    void soloElServicioDeEnvioUsaElCorreo() throws IOException {
        Path fuentes = Path.of("src/main/java");
        try (Stream<Path> archivos = Files.walk(fuentes)) {
            List<String> usan = archivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("JavaMailSender");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(p -> p.getFileName().toString()).toList();
            assertThat(usan).containsExactly("EnvioCorreoService.java");
        }
    }
}
