package bo.edu.uagrm.psicologia.tramites;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.auditoria.BitacoraAuditoriaRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.HistorialEstadoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de CU-09 a CU-12: registro, requisitos y actuaciones con la máquina de estados. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class TramitesIntegracionTest extends PruebaIntegracionBase {

    @Autowired
    private HistorialEstadoRepository historial;

    @Autowired
    private BitacoraAuditoriaRepository bitacora;

    private static final int TIPO_RECORD = 5;   // "Récord académico": 2 requisitos obligatorios

    private JsonNode registrar(String token, List<Integer> presentados) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("idEstudiante", 15, "idTipoTramite", TIPO_RECORD,
                "prioridad", "ALTA", "observaciones", "Solicitud de prueba", "requisitosPresentados", presentados));
        String r = mvc.perform(post("/api/tramites").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    private ResultActions evento(String token, int id, String evento, String justificacion) throws Exception {
        var req = post("/api/tramites/" + id + "/eventos/" + evento).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON);
        if (justificacion != null) req.content(json.writeValueAsString(Map.of("justificacion", justificacion)));
        return mvc.perform(req);
    }

    private static List<Integer> idsRequisitos(JsonNode detalle) {
        List<Integer> ids = new ArrayList<>();
        detalle.get("documentos").forEach(d -> ids.add(d.get("id").asInt()));
        return ids;
    }

    @Test
    void flujoCompletoConGuardasPermisosHistorialYAuditoria() throws Exception {
        String apoyo = tokenApoyo();
        String coord = tokenCoordinador();
        String director = tokenDirector();

        // CU-09: registro con 1 de 2 requisitos presentados
        JsonNode reg = registrar(apoyo, List.of());
        int id = reg.get("tramite").get("id").asInt();
        assertThat(reg.get("tramite").get("numero").asText()).matches("TRM-\\d{4}-\\d{4}");
        assertThat(reg.get("tramite").get("estadoActual").asText()).isEqualTo("RECEPCIONADO");
        assertThat(reg.get("requisitosPendientes").asInt()).isEqualTo(2);
        List<Integer> docs = idsRequisitos(reg);

        // Guarda "requisitos registrados"
        evento(coord, id, "derivar", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("faltan 2")));

        // CU-10: el Apoyo registra un requisito y adjunta el archivo del otro
        mvc.perform(patch("/api/tramites/" + id + "/documentos/" + docs.get(0)).header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"presentado\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.presentado").value(true));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "pago.pdf", "application/pdf",
                "%PDF-1.4 comprobante".getBytes(StandardCharsets.US_ASCII));
        mvc.perform(multipart("/api/tramites/" + id + "/documentos/" + docs.get(1) + "/archivo").file(pdf)
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentado").value(true))
                .andExpect(jsonPath("$.archivo.hashSha256").isNotEmpty());

        // El Apoyo no puede ejecutar actuaciones
        evento(apoyo, id, "derivar", null).andExpect(status().isForbidden());

        // CU-11: derivar -> observar (sin y con justificación) -> subsanar
        evento(coord, id, "derivar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("EN_REVISION"));
        evento(coord, id, "observar", "corta").andExpect(status().isUnprocessableEntity());
        evento(coord, id, "observar", "El comprobante de pago no es legible").andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("OBSERVADO"));
        evento(coord, id, "subsanar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("EN_REVISION"));

        // Solo el Director aprueba
        evento(coord, id, "aprobar", null).andExpect(status().isForbidden());
        evento(director, id, "aprobar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("APROBADO"))
                .andExpect(jsonPath("$.tramite.fechaCierre").isNotEmpty());

        // Transición inexistente
        evento(director, id, "rechazar", "Justificación suficientemente larga").andExpect(status().isConflict());

        // Archivar (Coordinador)
        evento(coord, id, "archivar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("ARCHIVADO"))
                .andExpect(jsonPath("$.acciones.length()").value(0));

        // Ya no admite cambios de documentación
        mvc.perform(patch("/api/tramites/" + id + "/documentos/" + docs.get(0)).header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"presentado\":false}"))
                .andExpect(status().isConflict());

        // Historial: registrar + 5 transiciones, cada una con usuario y transición del catálogo
        var h = historial.findByIdTramiteOrderByFechaHoraAscIdAsc(id);
        assertThat(h).extracting("evento").containsExactly("registrar", "derivar", "observar", "subsanar", "aprobar", "archivar");
        assertThat(h).extracting("estadoDestino")
                .containsExactly("RECEPCIONADO", "EN_REVISION", "OBSERVADO", "EN_REVISION", "APROBADO", "ARCHIVADO");
        assertThat(h.subList(1, h.size())).allMatch(x -> x.getIdTransicion() != null && x.getIdUsuario() != null);
        assertThat(h.get(4).getIdUsuario()).isEqualTo(1);   // aprobó el Director
        assertThat(h.get(2).getObservacion()).contains("no es legible");

        // Auditoría de cada actuación
        var acciones = bitacora.findAll().stream().filter(b -> "TRAMITE".equals(b.getEntidad())
                && String.valueOf(id).equals(b.getIdEntidad())).map(b -> b.getAccion()).toList();
        assertThat(acciones).contains("REGISTRAR", "TRAMITE_DERIVAR", "TRAMITE_OBSERVAR", "TRAMITE_SUBSANAR",
                "TRAMITE_APROBAR", "TRAMITE_ARCHIVAR", "ADJUNTAR_REQUISITO");

        // CU-12: el detalle expone la línea de tiempo con nombres
        mvc.perform(get("/api/tramites/" + id).header("Authorization", bearer(coord)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.length()").value(6))
                .andExpect(jsonPath("$.historial[4].usuario").value("Mariela Rosales Vaca"));
    }

    @Test
    void rechazoRequiereJustificacionDelDirector() throws Exception {
        String apoyo = tokenApoyo();
        JsonNode reg = registrar(apoyo, List.of());
        int id = reg.get("tramite").get("id").asInt();
        for (int doc : idsRequisitos(reg)) {
            mvc.perform(patch("/api/tramites/" + id + "/documentos/" + doc).header("Authorization", bearer(apoyo))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"presentado\":true}")).andExpect(status().isOk());
        }
        evento(tokenCoordinador(), id, "derivar", null).andExpect(status().isOk());
        String director = tokenDirector();
        evento(director, id, "rechazar", null).andExpect(status().isUnprocessableEntity());
        evento(director, id, "rechazar", "No corresponde según el reglamento vigente").andExpect(status().isOk())
                .andExpect(jsonPath("$.tramite.estadoActual").value("RECHAZADO"));
    }

    @Test
    void accionesDisponiblesDependenDelPermiso() throws Exception {
        // Trámite 41 del seed está EN_REVISION
        mvc.perform(get("/api/tramites/41").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(jsonPath("$.acciones[*].evento").value(org.hamcrest.Matchers.containsInAnyOrder("observar")));
        mvc.perform(get("/api/tramites/41").header("Authorization", bearer(tokenDirector())))
                .andExpect(jsonPath("$.acciones[*].evento").value(org.hamcrest.Matchers.containsInAnyOrder("aprobar", "rechazar")));
    }

    @Test
    void filtrosDeSeguimiento() throws Exception {
        String t = tokenCoordinador();
        mvc.perform(get("/api/tramites").param("estado", "OBSERVADO").header("Authorization", bearer(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].estadoActual").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("OBSERVADO"))));
        mvc.perform(get("/api/tramites").param("vencidos", "true").header("Authorization", bearer(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].vencido").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void requisitoAjenoAlTipoEsRechazado() throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("idEstudiante", 15, "idTipoTramite", TIPO_RECORD,
                "requisitosPresentados", List.of(1)));   // requisito 1 pertenece a otro tipo
        mvc.perform(post("/api/tramites").header("Authorization", bearer(tokenApoyo()))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }
}
