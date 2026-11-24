package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU-24 con pgvector: los vectores se guardan en PostgreSQL, se consultan con el índice HNSW y solo se recalculan
 * los textos que cambian. servicios-ia se reemplaza por un doble que da vectores de 384 dimensiones (bolsa de
 * palabras con hash), suficiente para que textos con las mismas palabras queden cerca.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class IndiceVectorialIntegracionTest extends PruebaIntegracionBase {

    @MockitoBean
    private ClienteServiciosIa ia;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    /** Textos que se pidieron vectorizar, en orden. */
    private final List<String> vectorizados = new ArrayList<>();

    static double[] vector(String texto) {
        double[] v = new double[IndiceVectorial.DIMENSION];
        String plano = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String palabra : plano.split("[^a-z0-9]+")) {
            if (palabra.length() > 2) v[Math.floorMod(palabra.hashCode(), v.length)] += 1;
        }
        double norma = 0;
        for (double x : v) norma += x * x;
        if (norma == 0) {
            v[0] = 1;
            return v;
        }
        norma = Math.sqrt(norma);
        for (int i = 0; i < v.length; i++) v[i] /= norma;
        return v;
    }

    @BeforeEach
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM embedding_documento");
    }

    @BeforeEach
    void dobleServiciosIa() {
        vectorizados.clear();
        when(ia.post(eq("/interno/embeddings"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> textos = (List<String>) ((Map<String, Object>) inv.getArgument(1)).get("textos");
            vectorizados.addAll(textos);
            ObjectNode r = json.createObjectNode();
            ArrayNode vs = r.putArray("vectores");
            textos.forEach(t -> {
                ArrayNode fila = vs.addArray();
                for (double x : vector(t)) fila.add(x);
            });
            r.put("dimension", IndiceVectorial.DIMENSION).put("motor", "sentence-transformers:prueba");
            return r;
        });
    }

    @Test
    void buscaEnPostgresYFiltraPorPermisos() throws Exception {
        Map<String, Object> trabajo = jdbc.queryForMap("SELECT id_trabajo, titulo FROM trabajo_grado ORDER BY id_trabajo LIMIT 1");
        String coord = tokenCoordinador();

        mvc.perform(get("/api/busqueda/semantica").param("consulta", (String) trabajo.get("titulo")).header("Authorization", bearer(coord)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motor").value("sentence-transformers:prueba · pgvector"))
                .andExpect(jsonPath("$.resultados[0].tipo").value("TRABAJO_GRADO"))
                .andExpect(jsonPath("$.resultados[0].id").value(trabajo.get("id_trabajo")))
                // El Coordinador no ve documentos: aunque estén indexados, no aparecen
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'DOCUMENTO')]").doesNotExist());

        // La primera búsqueda generó el índice persistente; no se usó el índice en memoria de servicios-ia
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM embedding_documento", Integer.class);
        assertThat(filas).isPositive();
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM embedding_documento LIMIT 1", Integer.class)).isEqualTo(384);
        verify(ia, never()).post(eq("/interno/busqueda"), any());
        verify(ia, never()).post(eq("/interno/busqueda/indice"), any());
    }

    @Test
    void reindexarSoloRecalculaLoQueCambio() throws Exception {
        String director = tokenDirector();
        String r = mvc.perform(post("/api/busqueda/indice").header("Authorization", bearer(director)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int indexados = json.readTree(r).get("indexados").asInt();
        assertThat(indexados).isPositive();
        assertThat(vectorizados).hasSize(indexados + 1);   // + la sonda del motor

        // Sin cambios: solo la sonda
        vectorizados.clear();
        mvc.perform(post("/api/busqueda/indice").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.indexados").value(indexados));
        assertThat(vectorizados).containsExactly("indice");

        // Un texto modificado y un registro que ya no existe
        String clave = jdbc.queryForObject("SELECT clave FROM embedding_documento ORDER BY clave LIMIT 1", String.class);
        jdbc.update("UPDATE embedding_documento SET hash_texto = repeat('0', 64) WHERE clave = ?", clave);
        jdbc.update("INSERT INTO embedding_documento (clave, hash_texto, motor, embedding) SELECT 'TRAB-999999', hash_texto, motor, embedding "
                + "FROM embedding_documento WHERE clave = ?", clave);
        vectorizados.clear();
        mvc.perform(post("/api/busqueda/indice").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.indexados").value(indexados));
        assertThat(vectorizados).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_documento WHERE clave = 'TRAB-999999'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_documento WHERE hash_texto = repeat('0', 64)", Integer.class)).isZero();

        // Otro modelo en servicios-ia: se recalcula todo
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> textos = (List<String>) ((Map<String, Object>) inv.getArgument(1)).get("textos");
            vectorizados.addAll(textos);
            ObjectNode o = json.createObjectNode();
            ArrayNode vs = o.putArray("vectores");
            textos.forEach(t -> {
                ArrayNode fila = vs.addArray();
                for (double x : vector(t + " v2")) fila.add(x);
            });
            return o.put("dimension", 384).put("motor", "sentence-transformers:otro");
        }).when(ia).post(eq("/interno/embeddings"), any());
        vectorizados.clear();
        mvc.perform(post("/api/busqueda/indice").header("Authorization", bearer(director)));
        assertThat(vectorizados).hasSize(indexados + 1);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT motor) FROM embedding_documento", Integer.class)).isEqualTo(1);
    }

    @Test
    void laConsultaUsaElIndiceHnsw() throws Exception {
        mvc.perform(post("/api/busqueda/indice").header("Authorization", bearer(tokenDirector()))).andExpect(status().isOk());
        String q = json.writeValueAsString(vector("ansiedad en estudiantes"));
        List<String> plan = transaccion.execute(t -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            List<String> p = jdbc.queryForList("EXPLAIN SELECT clave FROM embedding_documento ORDER BY embedding <=> CAST(? AS vector) LIMIT 10",
                    String.class, q);
            t.setRollbackOnly();
            return p;
        });
        assertThat(String.join("\n", plan)).contains("ix_embedding_documento_hnsw");
    }

    @Test
    void conTfidfUsaElIndiceEnMemoria() throws Exception {
        doThrow(new ClienteServiciosIa.RespuestaIa(HttpStatus.CONFLICT, 409, "El motor tfidf no produce vectores de dimensión fija"))
                .when(ia).post(eq("/interno/embeddings"), any());
        when(ia.post(eq("/interno/busqueda"), any())).thenReturn(json.valueToTree(Map.of("motor", "tfidf",
                "resultados", List.of(Map.of("id", "TRAB-1", "relevancia", 0.7)))));
        JsonNode r = json.readTree(mvc.perform(get("/api/busqueda/semantica").param("consulta", "ansiedad")
                .header("Authorization", bearer(tokenCoordinador()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.get("motor").asText()).isEqualTo("tfidf");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM embedding_documento", Integer.class)).isZero();
    }
}
