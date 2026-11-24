package bo.edu.uagrm.psicologia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas de integración: PostgreSQL 16 real con las migraciones Flyway V1..V3.
 * <ul>
 *   <li>Por defecto levanta PostgreSQL con Testcontainers (requiere Docker).</li>
 *   <li>Si se define PRUEBAS_DB_URL (y PRUEBAS_DB_USER / PRUEBAS_DB_PASSWORD) usa esa base vacía.</li>
 *   <li>Sin Docker ni PRUEBAS_DB_URL, las pruebas se omiten (ver {@link #disponible()}).</li>
 * </ul>
 */
@SpringBootTest(properties = "logging.structured.format.console=")
@AutoConfigureMockMvc
public abstract class PruebaIntegracionBase {

    public static final String CONDICION = "bo.edu.uagrm.psicologia.PruebaIntegracionBase#disponible";

    private static final String URL_EXTERNA = System.getenv("PRUEBAS_DB_URL");
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (URL_EXTERNA == null && dockerDisponible()) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    public static boolean disponible() {
        return URL_EXTERNA != null || POSTGRES != null;
    }

    private static boolean dockerDisponible() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry r) throws java.io.IOException {
        String almacen = java.nio.file.Files.createTempDirectory("p25-documentos-").toString();
        r.add("app.almacenamiento.directorio", () -> almacen);
        if (URL_EXTERNA != null) {
            r.add("spring.datasource.url", () -> URL_EXTERNA);
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("PRUEBAS_DB_USER", "postgres"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("PRUEBAS_DB_PASSWORD", ""));
        } else if (POSTGRES != null) {
            r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            r.add("spring.datasource.username", POSTGRES::getUsername);
            r.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    /** Inicia sesión y devuelve el token JWT. */
    protected String token(String usuario, String contrasena) throws Exception {
        String cuerpo = json.writeValueAsString(java.util.Map.of("nombreUsuario", usuario, "contrasena", contrasena));
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        JsonNode nodo = json.readTree(respuesta);
        if (!nodo.has("token")) {
            throw new IllegalStateException("No se pudo iniciar sesión como " + usuario + ": " + respuesta);
        }
        return nodo.get("token").asText();
    }

    protected String tokenDirector() throws Exception {
        return token("director", "Director2026*");
    }

    protected String tokenCoordinador() throws Exception {
        return token("coordinador", "Coordinador2026*");
    }

    protected String tokenApoyo() throws Exception {
        return token("apoyo", "Apoyo2026*");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
