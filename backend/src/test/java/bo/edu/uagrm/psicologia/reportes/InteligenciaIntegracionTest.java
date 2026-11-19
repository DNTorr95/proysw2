package bo.edu.uagrm.psicologia.reportes;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de CU-21 a CU-30 con un doble de servicios-ia (el servicio Python tiene sus propias pruebas). */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class InteligenciaIntegracionTest extends PruebaIntegracionBase {

    @MockitoBean
    private ClienteServiciosIa ia;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private JsonNode nodo(Object valor) {
        return json.valueToTree(valor);
    }

    @BeforeEach
    void preparar() {
        when(ia.post(eq("/interno/busqueda/indice"), any())).thenReturn(nodo(Map.of("indexados", 10, "motor", "tfidf")));
    }

    private int subirImagen(String token, String nombre) throws Exception {
        String r = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", nombre, "image/png", PNG))
                .header("Authorization", bearer(token))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asInt();
    }

    /** CU-21/CU-22 y CU-23: OCR pendiente de validación, validación humana y búsqueda en el texto según permisos. */
    @Test
    void ocrValidacionYBusquedaAvanzadaPorPermisos() throws Exception {
        String apoyo = tokenApoyo();
        String coord = tokenCoordinador();
        int id = subirImagen(apoyo, "oficio-escaneado.png");
        when(ia.post(eq("/interno/ocr"), any())).thenReturn(nodo(Map.of("texto", "Solicitud de zarzaparrilla para el laboratorio", "confianza", 87.456)));

        mvc.perform(post("/api/ocr/documentos/" + id + "/procesamiento").header("Authorization", bearer(coord))).andExpect(status().isForbidden());
        mvc.perform(post("/api/ocr/documentos/" + id + "/procesamiento").header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validado").value(false))
                .andExpect(jsonPath("$.confianza").value(87.46));
        mvc.perform(put("/api/ocr/documentos/" + id + "/validacion").header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Solicitud de zarzaparrilla para el laboratorio de psicología\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validado").value(true)).andExpect(jsonPath("$.idUsuarioValidacion").value(3));
        // Reprocesar un texto validado exige confirmación
        mvc.perform(post("/api/ocr/documentos/" + id + "/procesamiento").header("Authorization", bearer(apoyo))).andExpect(status().isConflict());
        mvc.perform(get("/api/ocr/documentos").param("estado", "VALIDADO").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.contenido[?(@.archivo.id == " + id + ")]").exists());

        // CU-23: el texto OCR es buscable, pero solo para quien puede ver documentos
        mvc.perform(get("/api/busqueda").param("texto", "zarzaparrilla").header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].tipo").value("DOCUMENTO"))
                .andExpect(jsonPath("$.resultados[0].detalle").value(org.hamcrest.Matchers.containsString("OCR validado")));
        mvc.perform(get("/api/busqueda").param("texto", "zarzaparrilla").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$.resultados.length()").value(0))
                .andExpect(jsonPath("$.tiposConsultados").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("DOCUMENTO"))));
        mvc.perform(get("/api/busqueda").param("texto", "aulas").param("tipos", "CORRESPONDENCIA").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$.resultados[0].tipo").value("CORRESPONDENCIA"));
    }

    /** CU-24: los resultados semánticos se filtran por permisos y el índice vacío se regenera solo. */
    @Test
    void busquedaSemanticaFiltraPorPermisos() throws Exception {
        String coord = tokenCoordinador();
        when(ia.post(eq("/interno/busqueda"), any()))
                .thenThrow(new ClienteServiciosIa.RespuestaIa(HttpStatus.CONFLICT, 409, "El índice está vacío"))
                .thenReturn(nodo(Map.of("motor", "tfidf", "resultados", List.of(
                        Map.of("id", "DOC-1", "relevancia", 0.91), Map.of("id", "CORR-3", "relevancia", 0.80),
                        Map.of("id", "TRAB-1", "relevancia", 0.55), Map.of("id", "TRAM-2", "relevancia", 0.40)))));
        mvc.perform(get("/api/busqueda/semantica").param("consulta", "ansiedad en estudiantes").header("Authorization", bearer(coord)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(3))
                .andExpect(jsonPath("$.resultados[0].tipo").value("CORRESPONDENCIA"))
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'DOCUMENTO')]").doesNotExist());
        verify(ia, atLeastOnce()).post(eq("/interno/busqueda/indice"), any());
        // El Apoyo no tiene búsqueda semántica
        mvc.perform(get("/api/busqueda/semantica").param("consulta", "ansiedad").header("Authorization", bearer(tokenApoyo())))
                .andExpect(status().isForbidden());
    }

    /** CU-25: el asistente responde con datos autorizados; si la IA cae, el error es controlado. */
    @Test
    void asistente() throws Exception {
        String coord = tokenCoordinador();
        when(ia.post(eq("/interno/asistente"), any()))
                .thenReturn(nodo(Map.of("intencion", "ESTADO_TRAMITE", "confianza", 0.9, "entidades", Map.of("numeroTramite", "TRM-2026-1"))))
                .thenReturn(nodo(Map.of("intencion", "TRAMITES_VENCIDOS", "confianza", 0.8, "entidades", Map.of())))
                .thenThrow(new ClienteServiciosIa.RespuestaIa(HttpStatus.SERVICE_UNAVAILABLE, 503, "El servicio inteligente no está disponible"));
        mvc.perform(post("/api/asistente").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pregunta\":\"estado del tramite TRM-2026-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("TRM-2026-0001")))
                .andExpect(jsonPath("$.enlaces[0].ruta").value("/tramites/1"));
        mvc.perform(post("/api/asistente").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pregunta\":\"cuantos tramites vencidos\"}"))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("vencido")));
        mvc.perform(post("/api/asistente").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"hola\"}")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/asistente").header("Authorization", bearer(tokenApoyo())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pregunta\":\"hola\"}")).andExpect(status().isForbidden());
    }

    /** CU-28/CU-30: alertas de plazo sin duplicados y atención con comentario; CU-29 predicción sin datos personales. */
    @Test
    void alertasPorPlazoYPrediccion() throws Exception {
        String coord = tokenCoordinador();
        String director = tokenDirector();
        String r = mvc.perform(post("/api/alertas/generacion").header("Authorization", bearer(coord))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(r).get("creadas").asInt()).isPositive();
        mvc.perform(post("/api/alertas/generacion").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$.creadas").value(0));
        String lista = mvc.perform(get("/api/alertas").param("estado", "ABIERTAS").param("origen", "PLAZO").header("Authorization", bearer(director)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idAlerta = json.readTree(lista).get("contenido").get(0).get("id").asInt();
        mvc.perform(patch("/api/alertas/" + idAlerta + "/atencion").header("Authorization", bearer(director))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ATENDIDA\",\"comentario\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/alertas/" + idAlerta + "/atencion").header("Authorization", bearer(coord))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ATENDIDA\",\"comentario\":\"Se comunicó al estudiante\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATENDIDA")).andExpect(jsonPath("$.atendidaPor").isNotEmpty());

        // CU-29
        when(ia.post(eq("/interno/prediccion/entrenamiento"), any()))
                .thenReturn(nodo(Map.of("ejemplos", 40, "positivos", 18, "exactitud", 0.81, "modelo", "RandomForest")));
        when(ia.post(eq("/interno/prediccion"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<Object> filas = (List<Object>) ((Map<String, Object>) inv.getArgument(1)).get("tramites");
            ObjectNode res = json.createObjectNode();
            ArrayNode p = res.putArray("probabilidades");
            for (int i = 0; i < filas.size(); i++) p.add(i == 0 ? 0.92 : 0.1);
            return res;
        });
        mvc.perform(post("/api/prediccion/analisis").header("Authorization", bearer(coord))).andExpect(status().isForbidden());
        mvc.perform(post("/api/prediccion/analisis").header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertasCreadas").value(1))
                .andExpect(jsonPath("$.exactitud").value(0.81))
                .andExpect(jsonPath("$.riesgos[0].nivel").value("ALTA"));
        mvc.perform(post("/api/prediccion/analisis").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.alertasCreadas").value(0)).andExpect(jsonPath("$.alertasActualizadas").value(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ia, atLeastOnce()).post(eq("/interno/prediccion/entrenamiento"), captor.capture());
        String enviado = json.writeValueAsString(captor.getValue());
        assertThat(enviado).contains("demorado", "plazoDias").doesNotContain("\"estudiante\"", "\"registro\"", "\"nombres\"", "\"ci\"", "\"numero\"");
        mvc.perform(get("/api/alertas").param("origen", "PREDICCION").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.contenido[0].probabilidad").value(92.0));
    }

    /** CU-26 y CU-27. */
    @Test
    void dashboardYReportes() throws Exception {
        String coord = tokenCoordinador();
        mvc.perform(get("/api/dashboard").header("Authorization", bearer(tokenDirector())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tramites.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(60)))
                .andExpect(jsonPath("$.tramites.porEstado.length()").value(6))
                .andExpect(jsonPath("$.correspondencia.porMes.length()").value(6))
                .andExpect(jsonPath("$.academico.periodo").value("2026-2"));
        mvc.perform(get("/api/dashboard").header("Authorization", bearer(tokenApoyo()))).andExpect(status().isForbidden());

        byte[] pdf = mvc.perform(get("/api/reportes/tramites").param("formato", "pdf").param("desde", "2026-03-01").param("hasta", "2026-11-30")
                .header("Authorization", bearer(coord))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        for (String tipo : List.of("correspondencia", "carga-docente", "poa", "rpa")) {
            byte[] xlsx = mvc.perform(get("/api/reportes/" + tipo).param("formato", "xlsx").header("Authorization", bearer(coord)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(xlsx[0]).as(tipo).isEqualTo((byte) 'P');
            assertThat(xlsx[1]).as(tipo).isEqualTo((byte) 'K');
        }
        mvc.perform(get("/api/reportes/inventado").header("Authorization", bearer(coord))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/tramites").param("formato", "doc").header("Authorization", bearer(coord))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reportes/tramites").header("Authorization", bearer(tokenApoyo()))).andExpect(status().isForbidden());
    }
}
