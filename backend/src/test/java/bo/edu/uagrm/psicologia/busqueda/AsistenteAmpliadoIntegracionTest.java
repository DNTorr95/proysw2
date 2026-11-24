package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU-25: intenciones nuevas del asistente (estado de un oficio, trámites de un estudiante, resumen académico
 * y próximos eventos). servicios-ia se reemplaza por un doble que devuelve la interpretación; el backend
 * responde con los datos que el usuario puede ver.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class AsistenteAmpliadoIntegracionTest extends PruebaIntegracionBase {

    @MockitoBean
    private ClienteServiciosIa ia;

    private void interpreta(String intencion, Map<String, Object> entidades) {
        JsonNode r = json.valueToTree(Map.of("intencion", intencion, "confianza", 0.8, "entidades", entidades));
        when(ia.post(eq("/interno/asistente"), any())).thenReturn(r);
    }

    private ResultActions preguntar(String token, String pregunta) throws Exception {
        return mvc.perform(post("/api/asistente").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("pregunta", pregunta)))).andExpect(status().isOk());
    }

    @Test
    void estadoDeUnOficio() throws Exception {
        String apoyo = tokenApoyo();
        String r = mvc.perform(post("/api/correspondencia").header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "RECIBIDA", "asunto", "Solicitud de aulas para el taller",
                                "fechaDocumento", LocalDate.now().toString(), "idDestinatario", 3))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String codigo = json.readTree(r).get("codigo").asText();
        // el código sin ceros a la izquierda también se reconoce (DCP-REC-2026-7 = DCP-REC-2026-0007)
        String corto = codigo.replaceFirst("-0+(\\d)", "-$1");
        interpreta("ESTADO_CORRESPONDENCIA", Map.of("codigoCorrespondencia", corto));
        preguntar(tokenCoordinador(), "estado del oficio " + corto)
                .andExpect(jsonPath("$.texto").value(containsString("El oficio " + codigo)))
                .andExpect(jsonPath("$.texto").value(containsString("Solicitud de aulas para el taller")))
                .andExpect(jsonPath("$.texto").value(containsString("REGISTRADA")))
                .andExpect(jsonPath("$.texto").value(containsString("sin autorizar")));

        String director = tokenDirector();
        interpreta("ESTADO_CORRESPONDENCIA", Map.of());
        preguntar(director, "estado del oficio").andExpect(jsonPath("$.texto").value(containsString("Indique el código del oficio")));
        interpreta("ESTADO_CORRESPONDENCIA", Map.of("codigoCorrespondencia", "DCP-EMI-1999-9"));
        preguntar(director, "estado del oficio DCP-EMI-1999-9").andExpect(jsonPath("$.texto").value("No encontré el oficio DCP-EMI-1999-9."));
    }

    @Test
    void tramitesDeUnEstudiante() throws Exception {
        String coord = tokenCoordinador();
        JsonNode t = json.readTree(mvc.perform(get("/api/tramites").param("tamano", "1").header("Authorization", bearer(coord)))
                .andReturn().getResponse().getContentAsString()).get("contenido").get(0);
        String registro = t.get("registro").asText();
        interpreta("TRAMITES_ESTUDIANTE", Map.of("registro", registro));
        preguntar(coord, "trámites del estudiante " + registro)
                .andExpect(jsonPath("$.texto").value(containsString("registro " + registro)))
                .andExpect(jsonPath("$.texto").value(containsString(t.get("numero").asText())))
                .andExpect(jsonPath("$.enlaces[*].ruta").value(hasItem("/tramites/" + t.get("id").asInt())));

        interpreta("TRAMITES_ESTUDIANTE", Map.of("registro", "00000000"));
        preguntar(coord, "trámites del estudiante 00000000")
                .andExpect(jsonPath("$.texto").value("No encontré trámites del estudiante con registro 00000000."));
    }

    @Test
    void resumenDelPeriodoAbierto() throws Exception {
        interpreta("RESUMEN_ACADEMICO", Map.of());
        preguntar(tokenCoordinador(), "¿cuántos grupos sin docente hay?")
                .andExpect(jsonPath("$.texto").value(containsString("En el periodo 2026-2 hay")))
                .andExpect(jsonPath("$.texto").value(containsString("sin docente")))
                .andExpect(jsonPath("$.enlaces[0].ruta").value("/academico"));
    }

    @Test
    void proximosEventos() throws Exception {
        String coord = tokenCoordinador();
        mvc.perform(post("/api/eventos").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "Taller de primeros auxilios psicológicos", "tipo", "TALLER",
                                "fecha", LocalDate.now().plusDays(3).toString(), "responsable", "Lic. Prueba"))))
                .andExpect(status().isCreated());
        interpreta("EVENTOS_PROXIMOS", Map.of());
        preguntar(coord, "¿qué eventos vienen?")
                .andExpect(jsonPath("$.texto").value(containsString("Taller de primeros auxilios psicológicos (taller, ")))
                .andExpect(jsonPath("$.enlaces[0].ruta").value("/eventos"));
    }

    @Test
    void laAyudaMencionaLasNuevasCapacidadesSegunElRol() throws Exception {
        interpreta("AYUDA", Map.of());
        preguntar(tokenCoordinador(), "hola")
                .andExpect(jsonPath("$.texto").value(containsString("estado de un oficio")))
                .andExpect(jsonPath("$.texto").value(containsString("trámites de un estudiante")))
                .andExpect(jsonPath("$.texto").value(containsString("grupos, cupos y docentes")))
                .andExpect(jsonPath("$.sugerencias").value(hasItem("¿Cuántos grupos sin docente hay?")));
    }
}
