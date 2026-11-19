package bo.edu.uagrm.psicologia.rpa;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.auditoria.BitacoraAuditoriaRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.CorrespondenciaRepository;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.repositorio.IncidenciaRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import bo.edu.uagrm.psicologia.rpa.trabajador.TrabajadorRpa;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo RPA de punta a punta en el backend (CU-31 a CU-37) con verificaciones del punto 12:
 * (b) ningún correo sin aprobación y (c) una reejecución no duplica informes ni envíos.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class RpaIntegracionTest extends PruebaIntegracionBase {

    @TestConfiguration
    static class Configuracion {
        @Bean
        @Primary
        TrabajadorRpa trabajadorFalso(AlmacenamientoService almacenamiento) {
            return new TrabajadorRpaFalso(almacenamiento);
        }
    }

    @MockitoBean
    private JavaMailSender correo;

    @Autowired private InformeMensualRepository informes;
    @Autowired private NotificacionCorreoRepository correos;
    @Autowired private IncidenciaRpaRepository incidencias;
    @Autowired private CorrespondenciaRepository correspondencias;
    @Autowired private BitacoraAuditoriaRepository bitacora;

    @BeforeEach
    void preparar() {
        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.NORMAL;
        TrabajadorRpaFalso.SOLICITUDES.clear();
        when(correo.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    @AfterEach
    void limpiar() {
        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.NORMAL;
    }

    // ------------------------------------------------------------------ utilidades

    private int iniciar(String token, String desde, String hasta) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("idProceso", 1, "periodoDesde", desde, "periodoHasta", hasta,
                "tipoCorrespondencia", "TODAS", "idRevisor", 1, "destinatariosCorreo", List.of("decanato.demo@ejemplo.edu.bo")));
        String r = mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asInt();
    }

    private JsonNode esperarFin(String token, int id) throws Exception {
        final JsonNode[] ultimo = new JsonNode[1];
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(150)).until(() -> {
            ultimo[0] = json.readTree(mvc.perform(get("/api/rpa/ejecuciones/" + id).header("Authorization", bearer(token)))
                    .andReturn().getResponse().getContentAsString());
            String e = ultimo[0].get("ejecucion").get("estado").asText();
            return e.equals("COMPLETADA") || e.equals("FALLIDA");
        });
        return ultimo[0];
    }

    private ResultActions accion(String token, int idInforme, String accion, Object cuerpo) throws Exception {
        var req = post("/api/rpa/informes/" + idInforme + "/" + accion).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) req.content(json.writeValueAsString(cuerpo));
        return mvc.perform(req);
    }

    // ------------------------------------------------------------------ pruebas

    /** Punta a punta: Apoyo ejecuta → informe PENDIENTE_REVISION + BORRADOR → Director aprueba → envío único. */
    @Test
    void flujoCompletoSinEnvioHastaLaAprobacion() throws Exception {
        String apoyo = tokenApoyo();
        String director = tokenDirector();
        int id = iniciar(apoyo, "2026-09-01", "2026-09-30");
        JsonNode det = esperarFin(apoyo, id);

        // CU-31/34/35: completada, con evidencias y la incidencia de datos
        assertThat(det.get("ejecucion").get("estado").asText()).isEqualTo("COMPLETADA");
        assertThat(det.get("ejecucion").get("situacion").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(det.get("pasos").size()).isGreaterThanOrEqualTo(5);
        assertThat(det.get("incidencias").get(0).get("tipo").asText()).isEqualTo("DUPLICADO");

        // Solo se enviaron al trabajador los oficios autorizados del periodo
        var solicitud = TrabajadorRpaFalso.SOLICITUDES.get(0);
        long autorizados = correspondencias.contarDelPeriodo(true, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertThat(solicitud.registros()).hasSize((int) autorizados);
        assertThat(solicitud.plantillaHtml()).contains("{{ secciones }}");

        int idInforme = det.get("ejecucion").get("idInforme").asInt();
        assertThat(informes.findById(idInforme).orElseThrow().getEstadoRevision()).isEqualTo("PENDIENTE_REVISION");
        assertThat(correos.findByInformeId(idInforme).orElseThrow().getEstado()).isEqualTo("BORRADOR");
        // (b) Ningún correo sin aprobación
        verify(correo, never()).send(any(MimeMessage.class));

        // Vista previa del PDF y del borrador
        mvc.perform(get("/api/rpa/informes/" + idInforme + "/pdf").header("Authorization", bearer(director)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rpa/informes/" + idInforme).header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.correo.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.puedeAprobar").value(true));

        // El revisor asignado ve la notificación interna
        mvc.perform(get("/api/notificaciones").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$[?(@.ruta == '/rpa/revision/" + idInforme + "')]").exists());

        // Apoyo y Coordinador no pueden autorizar el envío
        accion(apoyo, idInforme, "aprobacion", null).andExpect(status().isForbidden());
        accion(tokenCoordinador(), idInforme, "aprobacion", null).andExpect(status().isForbidden());
        verify(correo, never()).send(any(MimeMessage.class));

        // El Director edita el borrador y aprueba: se envía una vez
        mvc.perform(put("/api/rpa/informes/" + idInforme + "/borrador").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "destinatarios", List.of("decanato.demo@ejemplo.edu.bo", "vicedecanato.demo@ejemplo.edu.bo"),
                                "asunto", "Informe de correspondencia - septiembre 2026", "cuerpo", "Se adjunta el informe."))))
                .andExpect(status().isOk());
        accion(director, idInforme, "aprobacion", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.informe.estadoRevision").value("APROBADO"))
                .andExpect(jsonPath("$.correo.estado").value("ENVIADO"))
                .andExpect(jsonPath("$.correo.idUsuarioAutoriza").value(1))
                .andExpect(jsonPath("$.correo.fechaAutorizacion").isNotEmpty());
        verify(correo, times(1)).send(any(MimeMessage.class));

        // Una segunda aprobación no reenvía
        accion(director, idInforme, "aprobacion", null).andExpect(status().isConflict());
        verify(correo, times(1)).send(any(MimeMessage.class));

        // No se puede reejecutar lo enviado ni generar otro informe del mismo periodo
        mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isConflict());
        String cuerpo = json.writeValueAsString(Map.of("idProceso", 1, "periodoDesde", "2026-09-01", "periodoHasta", "2026-09-30",
                "tipoCorrespondencia", "TODAS", "idRevisor", 1, "destinatariosCorreo", List.of("x@ejemplo.edu.bo")));
        mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(apoyo))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isConflict());

        // Auditoría registrada
        var acciones = bitacora.findAll().stream().map(b -> b.getAccion()).toList();
        assertThat(acciones).contains("RPA_INICIAR", "RPA_EJECUCION_COMPLETADA", "NOTIFICAR_REVISOR", "EDITAR_BORRADOR",
                "APROBAR_INFORME", "AUTORIZAR_ENVIO", "CORREO_ENVIADO");
    }

    /** (c) Devolver y reejecutar: la nueva ejecución referencia a la anterior y no duplica informes ni envíos. */
    @Test
    void reejecucionNoDuplicaInformesNiEnvios() throws Exception {
        String apoyo = tokenApoyo();
        String coord = tokenCoordinador();
        int id = iniciar(apoyo, "2026-08-01", "2026-08-31");
        int idInforme = esperarFin(apoyo, id).get("ejecucion").get("idInforme").asInt();

        // No se puede reejecutar mientras está pendiente de revisión
        mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(coord)))
                .andExpect(status().isConflict());

        // El Coordinador devuelve (observaciones obligatorias)
        accion(coord, idInforme, "devolucion", Map.of("observaciones", "corta")).andExpect(status().isBadRequest());
        accion(coord, idInforme, "devolucion", Map.of("observaciones", "Faltan oficios de la segunda quincena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.informe.estadoRevision").value("DEVUELTO"))
                .andExpect(jsonPath("$.correo.estado").value("ANULADO"));

        // El Apoyo no gestiona reejecuciones
        mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(apoyo)))
                .andExpect(status().isForbidden());

        String r = mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(coord))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Corrección de datos\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idEjecucionOrigen").value(id))
                .andReturn().getResponse().getContentAsString();
        int nueva = json.readTree(r).get("id").asInt();
        JsonNode det = esperarFin(coord, nueva);
        assertThat(det.get("ejecucion").get("estado").asText()).isEqualTo("COMPLETADA");

        // Los destinatarios se tomaron del borrador anterior
        assertThat(TrabajadorRpaFalso.SOLICITUDES.get(1).destinatariosCorreo()).containsExactly("decanato.demo@ejemplo.edu.bo");

        // Una segunda reejecución de la misma ejecución se rechaza
        mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(coord)))
                .andExpect(status().isConflict());

        // Exactamente un informe vigente y ningún envío para el periodo
        var vigentes = informes.vigentes(1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "TODAS",
                List.of(InformeMensual.PENDIENTE_REVISION, InformeMensual.APROBADO));
        assertThat(vigentes).hasSize(1);
        assertThat(vigentes.get(0).getEjecucion().getId()).isEqualTo(nueva);
        verify(correo, never()).send(any(MimeMessage.class));

        // La ejecución original muestra quién la reejecutó y sus incidencias quedaron cerradas
        mvc.perform(get("/api/rpa/ejecuciones/" + id).header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$.ejecucion.idReejecucion").value(nueva))
                .andExpect(jsonPath("$.ejecucion.situacion").value("DEVUELTA"))
                .andExpect(jsonPath("$.puedeReejecutar").value(false));
        assertThat(incidencias.findByIdEjecucionOrderByIdAsc(id)).allMatch(i -> "CERRADA".equals(i.getEstado()) && i.getIdReejecucion() == nueva);
    }

    /** Tiempo excedido: FALLIDA con incidencia TIEMPO_EXCEDIDO, sin informe ni correo; luego se reejecuta. */
    @Test
    void tiempoExcedidoYReejecucion() throws Exception {
        String apoyo = tokenApoyo();
        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.TIEMPO_EXCEDIDO;
        int id = iniciar(apoyo, "2026-07-01", "2026-07-31");
        JsonNode det = esperarFin(apoyo, id);
        assertThat(det.get("ejecucion").get("estado").asText()).isEqualTo("FALLIDA");
        assertThat(det.get("incidencias").get(0).get("tipo").asText()).isEqualTo("TIEMPO_EXCEDIDO");
        assertThat(det.get("ejecucion").get("idInforme").isNull()).isTrue();

        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.NORMAL;
        String coord = tokenCoordinador();
        String r = mvc.perform(post("/api/rpa/ejecuciones/" + id + "/reejecucion").header("Authorization", bearer(coord))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatariosCorreo\":[\"decanato.demo@ejemplo.edu.bo\"]}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        int nueva = json.readTree(r).get("id").asInt();
        assertThat(esperarFin(coord, nueva).get("ejecucion").get("estado").asText()).isEqualTo("COMPLETADA");
        assertThat(incidencias.findByIdEjecucionOrderByIdAsc(id)).allMatch(i -> "CERRADA".equals(i.getEstado()));
    }

    @Test
    void trabajadorCaidoOFallaDePlantillaNoAfectanAlResto() throws Exception {
        String apoyo = tokenApoyo();
        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.CAIDO;
        JsonNode caido = esperarFin(apoyo, iniciar(apoyo, "2026-06-01", "2026-06-30"));
        assertThat(caido.get("incidencias").get(0).get("tipo").asText()).isEqualTo("SERVICIO_NO_DISPONIBLE");

        TrabajadorRpaFalso.modo = TrabajadorRpaFalso.Modo.FALLA_PLANTILLA;
        JsonNode plantilla = esperarFin(apoyo, iniciar(apoyo, "2026-05-01", "2026-05-31"));
        assertThat(plantilla.get("ejecucion").get("estado").asText()).isEqualTo("FALLIDA");
        assertThat(plantilla.get("incidencias").get(0).get("tipo").asText()).isEqualTo("FALLA_PLANTILLA");

        // Disponibilidad: el resto del sistema sigue respondiendo
        mvc.perform(get("/api/tramites").header("Authorization", bearer(apoyo))).andExpect(status().isOk());
        verify(correo, never()).send(any(MimeMessage.class));
    }

    @Test
    void validacionesYPermisosDeInicio() throws Exception {
        String apoyo = tokenApoyo();
        // El Coordinador no ejecuta RPA
        String cuerpo = json.writeValueAsString(Map.of("idProceso", 1, "periodoDesde", "2026-04-01", "periodoHasta", "2026-04-30",
                "tipoCorrespondencia", "TODAS", "idRevisor", 1, "destinatariosCorreo", List.of("a@ejemplo.edu.bo")));
        mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(tokenCoordinador()))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isForbidden());
        // Periodo futuro
        String futuro = cuerpo.replace("2026-04-30", LocalDate.now().plusDays(5).toString());
        mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(apoyo))
                .contentType(MediaType.APPLICATION_JSON).content(futuro)).andExpect(status().isBadRequest());
        // Revisor sin permiso de revisión (el propio Apoyo, id 3)
        String sinPermiso = cuerpo.replace("\"idRevisor\":1", "\"idRevisor\":3");
        mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(apoyo))
                .contentType(MediaType.APPLICATION_JSON).content(sinPermiso)).andExpect(status().isBadRequest());
        // Correo inválido
        String malCorreo = cuerpo.replace("a@ejemplo.edu.bo", "no-es-correo");
        mvc.perform(post("/api/rpa/ejecuciones").header("Authorization", bearer(apoyo))
                .contentType(MediaType.APPLICATION_JSON).content(malCorreo)).andExpect(status().isBadRequest());
        // Previsualización
        mvc.perform(get("/api/rpa/previsualizacion").param("idProceso", "1").param("desde", "2026-10-01")
                        .param("hasta", "2026-10-31").header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizados").isNumber())
                .andExpect(jsonPath("$.noAutorizados").isNumber());
        // Métricas para el Coordinador
        mvc.perform(get("/api/rpa/metricas").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiempoManualMinutos").value(240));
        clearInvocations(correo);
    }
}
