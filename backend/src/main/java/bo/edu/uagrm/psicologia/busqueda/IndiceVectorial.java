package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU-24 Índice semántico persistente en PostgreSQL (pgvector, tabla embedding_documento).
 *
 * servicios-ia calcula los vectores; aquí se guardan y se consultan por distancia coseno con el índice HNSW.
 * Si el motor activo de servicios-ia no da vectores de dimensión fija (TF-IDF), los métodos devuelven null y
 * {@link BusquedaService} usa el índice en memoria de servicios-ia como antes.
 */
@Component
public class IndiceVectorial {

    private static final Logger log = LoggerFactory.getLogger(IndiceVectorial.class);
    /** Dimensión de la columna vector(384): paraphrase-multilingual-MiniLM-L12-v2. */
    static final int DIMENSION = 384;
    static final double SIMILITUD_MINIMA = 0.15;
    private static final int LOTE = 64;
    private static final int LARGO_MAXIMO = 4000;

    private final JdbcTemplate jdbc;
    private final ClienteServiciosIa ia;

    public IndiceVectorial(JdbcTemplate jdbc, ClienteServiciosIa ia) {
        this.jdbc = jdbc;
        this.ia = ia;
    }

    public record Sincronizacion(int indexados, int calculados, int eliminados, String motor) {
    }

    public record Coincidencias(Map<String, Double> relevancia, String motor, boolean indiceVacio) {
    }

    /** Vectores de los textos, o null si servicios-ia no tiene un motor de dimensión {@value #DIMENSION}. */
    private JsonNode vectores(List<String> textos) {
        JsonNode r;
        try {
            r = ia.post("/interno/embeddings", Map.of("textos", textos));
        } catch (ClienteServiciosIa.RespuestaIa e) {
            if (e.getCodigo() == 409) return null;   // motor TF-IDF
            throw e;
        }
        if (r == null || !r.path("vectores").isArray()) return null;
        if (r.path("dimension").asInt() != DIMENSION) {
            log.warn("El motor {} da vectores de dimensión {} (se esperaba {}); se usa el índice en memoria",
                    r.path("motor").asText(), r.path("dimension").asInt(), DIMENSION);
            return null;
        }
        return r;
    }

    /**
     * Calcula los vectores solo de los textos nuevos o modificados (o generados con otro motor) y borra los de
     * registros que ya no existen. Va en su propia transacción porque puede llamarse desde una búsqueda de solo lectura.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Sincronizacion sincronizar(List<Map<String, String>> corpus) {
        JsonNode sonda = vectores(List.of("indice"));
        if (sonda == null) return null;
        String motor = sonda.path("motor").asText();

        Map<String, String> textos = new LinkedHashMap<>();
        corpus.forEach(d -> {
            String t = d.get("texto") == null ? "" : d.get("texto").strip();
            if (!t.isEmpty()) textos.put(d.get("id"), t.length() > LARGO_MAXIMO ? t.substring(0, LARGO_MAXIMO) : t);
        });
        Map<String, String> guardados = new HashMap<>();
        jdbc.query("SELECT clave, hash_texto, motor FROM embedding_documento",
                rs -> { guardados.put(rs.getString(1), rs.getString(2) + "|" + rs.getString(3)); });

        List<String> pendientes = new ArrayList<>();
        Map<String, String> hashes = new HashMap<>();
        textos.forEach((clave, texto) -> {
            String h = sha256(texto);
            hashes.put(clave, h);
            if (!(h + "|" + motor).equals(guardados.get(clave))) pendientes.add(clave);
        });
        for (int i = 0; i < pendientes.size(); i += LOTE) {
            List<String> lote = pendientes.subList(i, Math.min(i + LOTE, pendientes.size()));
            JsonNode r = vectores(lote.stream().map(textos::get).toList());
            if (r == null) return null;   // el motor cambió a mitad de camino
            List<Object[]> filas = new ArrayList<>();
            for (int j = 0; j < lote.size(); j++) {
                filas.add(new Object[]{lote.get(j), hashes.get(lote.get(j)), r.path("motor").asText(), r.path("vectores").get(j).toString()});
            }
            jdbc.batchUpdate("""
                    INSERT INTO embedding_documento (clave, hash_texto, motor, embedding, fecha_actualizacion)
                    VALUES (?, ?, ?, CAST(? AS vector), CURRENT_TIMESTAMP)
                    ON CONFLICT (clave) DO UPDATE SET hash_texto = EXCLUDED.hash_texto, motor = EXCLUDED.motor,
                        embedding = EXCLUDED.embedding, fecha_actualizacion = EXCLUDED.fecha_actualizacion""", filas);
        }
        List<Object[]> sobrantes = guardados.keySet().stream().filter(c -> !textos.containsKey(c)).map(c -> new Object[]{c}).toList();
        if (!sobrantes.isEmpty()) jdbc.batchUpdate("DELETE FROM embedding_documento WHERE clave = ?", sobrantes);
        log.info("indice vectorial sincronizado documentos={} calculados={} eliminados={} motor={}",
                textos.size(), pendientes.size(), sobrantes.size(), motor);
        return new Sincronizacion(textos.size(), pendientes.size(), sobrantes.size(), motor);
    }

    /**
     * Claves más parecidas a la consulta (similitud coseno, de mayor a menor), o null si no hay motor vectorial.
     * Debe llamarse dentro de una transacción (usa SET LOCAL).
     */
    @Transactional(readOnly = true)
    public Coincidencias buscar(String consulta, int limite) {
        JsonNode r = vectores(List.of(consulta));
        if (r == null) return null;
        String motor = r.path("motor").asText();
        String q = r.path("vectores").get(0).toString();
        Integer guardados = jdbc.queryForObject("SELECT count(*) FROM embedding_documento WHERE motor = ?", Integer.class, motor);
        if (guardados == null || guardados == 0) return new Coincidencias(Map.of(), motor, true);
        // ef_search por defecto (40) limitaría los vecinos que devuelve HNSW
        jdbc.execute("SET LOCAL hnsw.ef_search = " + Math.max(40, limite * 2));
        Map<String, Double> relevancia = new LinkedHashMap<>();
        jdbc.query("""
                SELECT clave, 1 - (embedding <=> CAST(? AS vector)) AS similitud
                FROM embedding_documento WHERE motor = ?
                ORDER BY embedding <=> CAST(? AS vector) LIMIT ?""",
                rs -> {
                    double s = rs.getDouble(2);
                    if (s >= SIMILITUD_MINIMA) relevancia.put(rs.getString(1), Math.round(s * 10000) / 10000.0);
                }, q, motor, q, limite);
        return new Coincidencias(relevancia, motor, false);
    }

    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
