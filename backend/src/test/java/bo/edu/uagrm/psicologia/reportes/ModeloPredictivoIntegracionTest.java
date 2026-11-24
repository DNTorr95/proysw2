package bo.edu.uagrm.psicologia.reportes;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-29: el modelo predictivo se guarda con versión y se reutiliza; solo se reentrena cuando hace falta. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class ModeloPredictivoIntegracionTest extends PruebaIntegracionBase {

    @MockitoBean
    private ClienteServiciosIa ia;

    @Autowired
    private JdbcTemplate jdbc;

    /** Versión que servicios-ia dice tener cargada en cada predicción (simula su volumen de modelos). */
    private final AtomicInteger versionEnIa = new AtomicInteger();

    @BeforeEach
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM modelo_prediccion");
    }

    @BeforeEach
    void dobleServiciosIa() {
        when(ia.post(eq("/interno/prediccion/entrenamiento"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> cuerpo = (Map<String, Object>) inv.getArgument(1);
            int version = (Integer) cuerpo.get("version");
            versionEnIa.set(version);
            return json.valueToTree(Map.of("ejemplos", 40, "positivos", 18, "exactitud", 0.8 + version / 100.0,
                    "modelo", "RandomForest", "version", version, "sha256", "a".repeat(63) + version));
        });
        when(ia.post(eq("/interno/prediccion"), any())).thenAnswer(inv -> {
            if (versionEnIa.get() == 0) throw new ClienteServiciosIa.RespuestaIa(HttpStatus.CONFLICT, 409, "El modelo no está entrenado");
            @SuppressWarnings("unchecked")
            List<Object> filas = (List<Object>) ((Map<String, Object>) inv.getArgument(1)).get("tramites");
            ObjectNode res = json.createObjectNode();
            ArrayNode p = res.putArray("probabilidades");
            filas.forEach(f -> p.add(0.1));
            res.putObject("metricas").put("version", versionEnIa.get());
            return res;
        });
    }

    private JsonNode analizar(String token, boolean reentrenar) throws Exception {
        String r = mvc.perform(post("/api/prediccion/analisis").param("reentrenar", String.valueOf(reentrenar))
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    @Test
    void reutilizaLaVersionVigenteYReentrenaSoloAPedido() throws Exception {
        String director = tokenDirector();

        JsonNode primero = analizar(director, false);
        assertThat(primero.get("version").asInt()).isEqualTo(1);
        assertThat(primero.get("reentrenado").asBoolean()).isTrue();
        assertThat(primero.get("exactitud").asDouble()).isEqualTo(0.81);
        assertThat(primero.get("fechaEntrenamiento").isTextual()).isTrue();

        // Sin pedir reentrenamiento se usa la versión guardada
        JsonNode segundo = analizar(director, false);
        assertThat(segundo.get("version").asInt()).isEqualTo(1);
        assertThat(segundo.get("reentrenado").asBoolean()).isFalse();
        verify(ia, times(1)).post(eq("/interno/prediccion/entrenamiento"), any());

        JsonNode tercero = analizar(director, true);
        assertThat(tercero.get("version").asInt()).isEqualTo(2);
        assertThat(tercero.get("reentrenado").asBoolean()).isTrue();
        assertThat(tercero.get("exactitud").asDouble()).isEqualTo(0.82);

        mvc.perform(get("/api/prediccion/modelos").header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[0].vigente").value(true))
                .andExpect(jsonPath("$[0].hashSha256").value("a".repeat(63) + "2"))
                .andExpect(jsonPath("$[0].idUsuario").isNumber())
                .andExpect(jsonPath("$[1].version").value(1)).andExpect(jsonPath("$[1].vigente").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM modelo_prediccion WHERE vigente", Integer.class)).isEqualTo(1);
    }

    @Test
    void reentrenaSiServiciosIaPerdioElModelo() throws Exception {
        String director = tokenDirector();
        analizar(director, false);

        // servicios-ia se reinició sin su volumen: responde 409 al predecir
        versionEnIa.set(0);
        JsonNode r = analizar(director, false);
        assertThat(r.get("version").asInt()).isEqualTo(2);
        assertThat(r.get("reentrenado").asBoolean()).isTrue();

        // servicios-ia tiene otra versión distinta de la vigente en la base
        versionEnIa.set(7);
        r = analizar(director, false);
        assertThat(r.get("version").asInt()).isEqualTo(3);
        verify(ia, times(3)).post(eq("/interno/prediccion/entrenamiento"), any());
    }

    @Test
    void elHistorialDeModelosExigeElPermiso() throws Exception {
        mvc.perform(get("/api/prediccion/modelos").header("Authorization", bearer(tokenCoordinador()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/prediccion/analisis").header("Authorization", bearer(tokenCoordinador()))).andExpect(status().isForbidden());
        verify(ia, never()).post(eq("/interno/prediccion/entrenamiento"), any());
    }
}
