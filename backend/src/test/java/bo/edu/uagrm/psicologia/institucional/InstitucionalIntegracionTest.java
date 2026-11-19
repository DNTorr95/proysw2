package bo.edu.uagrm.psicologia.institucional;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.BitacoraAuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de CU-17 a CU-20 y CU-04. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class InstitucionalIntegracionTest extends PruebaIntegracionBase {

    @Autowired private EstudianteRepository estudiantes;
    @Autowired private BitacoraAuditoriaRepository bitacora;

    private ResultActions enviar(String ruta, String token, Object cuerpo) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    /** CU-17 y CU-18: trabajo de grado con documento y registro de titulación. */
    @Test
    void trabajosDeGradoYTitulados() throws Exception {
        String apoyo = tokenApoyo();
        // CU-18
        String r = enviar("/api/trabajos-grado", apoyo, Map.of("idEstudiante", 46, "titulo", "Resiliencia en estudiantes universitarios",
                "tutor", "Lic. Carla Ortiz Melgar", "gestion", 2026, "palabrasClave", "Resiliencia; Estudiantes, resiliencia"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.palabrasClave").value("resiliencia, estudiantes"))
                .andReturn().getResponse().getContentAsString();
        int trabajo = json.readTree(r).get("id").asInt();
        mvc.perform(multipart("/api/trabajos-grado/" + trabajo + "/archivo")
                        .file(new MockMultipartFile("archivo", "tesis.pdf", "application/pdf", "%PDF-1.4 tesis".getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archivo.hashSha256").isNotEmpty());
        mvc.perform(get("/api/trabajos-grado").param("texto", "autoestima").header("Authorization", bearer(tokenDirector())))
                .andExpect(jsonPath("$[0].titulo").value(org.hamcrest.Matchers.containsString("autoestima")));

        // CU-17: estudiante 49 (EGRESADO) con su trabajo 3
        enviar("/api/titulados", apoyo, Map.of("idEstudiante", 49, "gestion", 2026, "modalidad", "TESIS",
                "fechaDefensa", "2026-10-20")).andExpect(status().isBadRequest());               // tesis sin trabajo
        enviar("/api/titulados", apoyo, Map.of("idEstudiante", 49, "idTrabajo", 4, "gestion", 2026, "modalidad", "TESIS",
                "fechaDefensa", "2026-10-20")).andExpect(status().isBadRequest());               // trabajo de otro estudiante
        enviar("/api/titulados", apoyo, Map.of("idEstudiante", 49, "idTrabajo", 3, "gestion", 2026, "modalidad", "TESIS",
                "fechaDefensa", LocalDate.now().plusDays(3).toString())).andExpect(status().isBadRequest());   // fecha futura
        enviar("/api/titulados", tokenCoordinador(), Map.of("idEstudiante", 49, "idTrabajo", 3, "gestion", 2026,
                "modalidad", "TESIS", "fechaDefensa", "2026-10-20")).andExpect(status().isForbidden());
        enviar("/api/titulados", apoyo, Map.of("idEstudiante", 49, "idTrabajo", 3, "gestion", 2026, "modalidad", "TESIS",
                "fechaDefensa", "2026-10-20", "nroDiploma", "DIP-PSI-2026-001")).andExpect(status().isCreated());
        assertThat(estudiantes.findById(49).orElseThrow().getEstado()).isEqualTo("TITULADO");
        enviar("/api/titulados", apoyo, Map.of("idEstudiante", 49, "gestion", 2026, "modalidad", "EXCELENCIA",
                "fechaDefensa", "2026-10-20")).andExpect(status().isConflict());
    }

    /** CU-19 y CU-20: seguimiento del POA y eventos vinculados. */
    @Test
    void seguimientoDelPoaYEventos() throws Exception {
        String director = tokenDirector();
        String coord = tokenCoordinador();
        mvc.perform(get("/api/poa/2026").header("Authorization", bearer(coord)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actividades.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.poa.avance").isNumber());

        // CU-20: el Coordinador registra un evento vinculado a la actividad 5
        String r = enviar("/api/eventos", coord, Map.of("nombre", "Conversatorio de investigación", "tipo", "SEMINARIO",
                "fecha", "2026-11-10", "idActividad", 5)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int evento = json.readTree(r).get("id").asInt();
        enviar("/api/eventos", director, Map.of("nombre", "x", "tipo", "TALLER", "fecha", "2026-11-10")).andExpect(status().isForbidden());
        enviar("/api/eventos", coord, Map.of("nombre", "x", "tipo", "FIESTA", "fecha", "2026-11-10")).andExpect(status().isBadRequest());

        // CU-19: el Director registra avance (actividad 5 vence el 2026-10-31 sin concluir -> RETRASADA)
        enviar("/api/actividades-poa/5/seguimientos", coord, Map.of("fecha", "2026-11-10", "avance", 60,
                "observacion", "Avance")).andExpect(status().isForbidden());
        enviar("/api/actividades-poa/5/seguimientos", director, Map.of("fecha", "2026-11-10", "avance", 60,
                "observacion", "Seminario preparatorio realizado", "evidencia", "Lista de asistencia", "idEvento", evento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.avance").value(60))
                .andExpect(jsonPath("$.estado").value("RETRASADA"));
        // Bajar el avance exige justificación
        enviar("/api/actividades-poa/5/seguimientos", director, Map.of("fecha", "2026-11-11", "avance", 30,
                "observacion", "Corrección")).andExpect(status().isBadRequest());
        enviar("/api/actividades-poa/5/seguimientos", director, Map.of("fecha", "2026-11-11", "avance", 100,
                "observacion", "Seminario concluido")).andExpect(jsonPath("$.estado").value("CONCLUIDA"));
        // Evento de otra actividad: rechazado
        enviar("/api/actividades-poa/1/seguimientos", director, Map.of("fecha", "2026-11-11", "avance", 75,
                "observacion", "Avance", "idEvento", evento)).andExpect(status().isBadRequest());
        // Actividad fuera de la gestión
        enviar("/api/poa/1/actividades", director, Map.of("descripcion", "x", "meta", "y",
                "fechaInicio", "2027-01-10", "fechaFin", "2027-02-10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/actividades-poa/5/seguimientos").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$[0].avance").value(100));
    }

    /** CU-04: la autoridad delegada permite aprobar trámites y caduca al revocarse. */
    @Test
    void transferenciaDeAutoridad() throws Exception {
        String director = tokenDirector();
        String coord = tokenCoordinador();
        // Sin delegación, el Coordinador no aprueba (trámite 42 está EN_REVISION en el seed)
        mvc.perform(post("/api/tramites/42/eventos/aprobar").header("Authorization", bearer(coord))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        enviar("/api/transferencias-autoridad", coord, Map.of("idUsuarioDestino", 3, "motivo", "Viaje oficial del Director",
                "fechaInicio", LocalDate.now().toString())).andExpect(status().isForbidden());
        enviar("/api/transferencias-autoridad", director, Map.of("idUsuarioDestino", 1, "motivo", "Viaje oficial del Director",
                "fechaInicio", LocalDate.now().toString())).andExpect(status().isBadRequest());

        String r = enviar("/api/transferencias-autoridad", director, Map.of("idUsuarioDestino", 2, "motivo", "Viaje oficial del Director",
                "resolucion", "RES-DC-0099/2026", "fechaInicio", LocalDate.now().toString(), "fechaFin", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.activaHoy").value(true))
                .andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();
        try {
            enviar("/api/transferencias-autoridad", director, Map.of("idUsuarioDestino", 3, "motivo", "Otra delegación simultánea",
                    "fechaInicio", LocalDate.now().toString())).andExpect(status().isConflict());
            // El mismo token del Coordinador ahora actúa como Director
            mvc.perform(get("/api/auth/perfil").header("Authorization", bearer(coord)))
                    .andExpect(jsonPath("$.rol").value("DIRECTOR_CARRERA"))
                    .andExpect(jsonPath("$.rolPropio").value("COORDINADOR_CARRERA"))
                    .andExpect(jsonPath("$.autoridadDelegada").isNotEmpty());
            mvc.perform(post("/api/tramites/42/eventos/aprobar").header("Authorization", bearer(coord))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.tramite.estadoActual").value("APROBADO"));
            // La autoridad delegada no se puede volver a delegar
            mvc.perform(get("/api/transferencias-autoridad").header("Authorization", bearer(coord))).andExpect(status().isForbidden());
        } finally {
            enviar("/api/transferencias-autoridad/" + id + "/revocacion", director, Map.of("motivo", "Retorno del Director"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVOCADA"));
        }
        mvc.perform(post("/api/tramites/43/eventos/aprobar").header("Authorization", bearer(coord))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        assertThat(bitacora.findAll()).extracting("accion").contains("TRANSFERIR_AUTORIDAD", "REVOCAR_AUTORIDAD");
    }
}
