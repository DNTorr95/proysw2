package bo.edu.uagrm.psicologia.academico;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de CU-13 a CU-16 sobre los datos del seed (periodo 2026-2 abierto, cierre de inscripción 2026-08-21). */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class AcademicoIntegracionTest extends PruebaIntegracionBase {

    @Autowired
    private EstudianteRepository estudiantes;

    private ResultActions enviar(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        var req = switch (metodo) {
            case "PUT" -> put(ruta);
            case "PATCH" -> patch(ruta);
            default -> post(ruta);
        };
        return mvc.perform(req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private int id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asInt();
    }

    /** CU-13: planificar un periodo copiando la oferta y controlar su apertura. */
    @Test
    void planificacionSemestral() throws Exception {
        String coord = tokenCoordinador();
        enviar("POST", "/api/periodos", tokenApoyo(), Map.of("gestion", 2027, "semestre", 1,
                "fechaInicio", "2027-02-15", "fechaFin", "2027-07-02")).andExpect(status().isForbidden());
        enviar("POST", "/api/periodos", coord, Map.of("gestion", 2027, "semestre", 1,
                "fechaInicio", "2027-07-15", "fechaFin", "2027-02-02")).andExpect(status().isBadRequest());
        int nuevo = id(enviar("POST", "/api/periodos", coord, Map.of("gestion", 2027, "semestre", 1,
                "fechaInicio", "2027-02-15", "fechaFin", "2027-07-02", "fechaCierreInscripcion", "2027-03-05"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PLANIFICADO")));
        enviar("POST", "/api/periodos", coord, Map.of("gestion", 2027, "semestre", 1,
                "fechaInicio", "2027-02-15", "fechaFin", "2027-07-02")).andExpect(status().isConflict());

        // Sin grupos no se abre; se copia la oferta del 2026-2
        enviar("PATCH", "/api/periodos/" + nuevo + "/estado", coord, Map.of("estado", "ABIERTO")).andExpect(status().isConflict());
        enviar("POST", "/api/periodos/" + nuevo + "/copia-grupos", coord, Map.of("idPeriodoOrigen", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grupos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)));
        enviar("POST", "/api/periodos/" + nuevo + "/copia-grupos", coord, Map.of("idPeriodoOrigen", 2)).andExpect(status().isConflict());
        // Ya hay un periodo abierto (2026-2)
        enviar("PATCH", "/api/periodos/" + nuevo + "/estado", coord, Map.of("estado", "ABIERTO")).andExpect(status().isConflict());

        mvc.perform(get("/api/periodos/2/resumen").header("Authorization", bearer(tokenDirector())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)))
                .andExpect(jsonPath("$.ocupacion").isNumber());
    }

    /** CU-14: grupos, cupos e inscripciones con todas sus reglas. */
    @Test
    void gruposCuposEInscripciones() throws Exception {
        String coord = tokenCoordinador();
        // Grupo con cupo 1 para PSI-103 (plan 148-1) en 2026-2
        int grupo = id(enviar("POST", "/api/grupos", coord, Map.of("idMateria", 3, "idPeriodo", 2, "codigo", "T1",
                "cupo", 1, "turno", "TARDE")).andExpect(status().isCreated()));
        enviar("POST", "/api/grupos", coord, Map.of("idMateria", 3, "idPeriodo", 2, "codigo", "T1",
                "cupo", 5, "turno", "TARDE")).andExpect(status().isConflict());

        // Inscripciones cerradas (2026-08-21): solo caso especial con motivo
        String ins = "/api/grupos/" + grupo + "/inscripciones";
        enviar("POST", ins, coord, Map.of("idEstudiante", 10, "casoEspecial", false)).andExpect(status().isConflict());
        enviar("POST", ins, coord, Map.of("idEstudiante", 10, "casoEspecial", true)).andExpect(status().isBadRequest());
        // Plan distinto (estudiante 11 está en 148-2)
        enviar("POST", ins, coord, Map.of("idEstudiante", 11, "casoEspecial", true, "motivo", "TRM adición")).andExpect(status().isBadRequest());
        int inscripcion = id(enviar("POST", ins, coord, Map.of("idEstudiante", 10, "casoEspecial", true, "motivo", "Trámite de adición fuera de fecha"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.casoEspecial").value(true)));
        // Sin cupo
        enviar("POST", ins, coord, Map.of("idEstudiante", 12, "casoEspecial", true, "motivo", "Adición")).andExpect(status().isConflict());
        // El cupo no baja de los inscritos
        enviar("PUT", "/api/grupos/" + grupo, coord, Map.of("cupo", 0, "turno", "TARDE")).andExpect(status().isBadRequest());
        enviar("PUT", "/api/grupos/" + grupo, coord, Map.of("cupo", 3, "turno", "TARDE")).andExpect(status().isOk());

        // Cambio de grupo a otro grupo de la misma materia (grupo 3 del seed = PSI-103)
        enviar("POST", "/api/inscripciones/" + inscripcion + "/cambio-grupo", coord, Map.of("idGrupoDestino", 3, "motivo", "Trámite de cambio de grupo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("INSCRITO"));
        mvc.perform(get(ins).header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$[0].estado").value("RETIRADO"));
        // Cambio a otra materia: rechazado
        String r = mvc.perform(get("/api/grupos/3/inscripciones").header("Authorization", bearer(coord))).andReturn().getResponse().getContentAsString();
        int nueva = json.readTree(r).findValues("id").stream().mapToInt(n -> n.asInt()).max().orElseThrow();
        enviar("POST", "/api/inscripciones/" + nueva + "/cambio-grupo", coord, Map.of("idGrupoDestino", 4, "motivo", "x"))
                .andExpect(status().isBadRequest());

        // El Apoyo consulta pero no gestiona
        mvc.perform(get("/api/grupos").param("idPeriodo", "2").header("Authorization", bearer(tokenApoyo()))).andExpect(status().isOk());
        enviar("POST", "/api/grupos", tokenApoyo(), Map.of("idMateria", 3, "idPeriodo", 2, "codigo", "X9", "cupo", 5, "turno", "TARDE"))
                .andExpect(status().isForbidden());
    }

    /** CU-15: la migración actualiza el plan del estudiante. */
    @Test
    void migracionEntrePlanes() throws Exception {
        String coord = tokenCoordinador();
        enviar("POST", "/api/migraciones", coord, Map.of("idEstudiante", 14, "planDestino", "148-1", "fecha", "2026-11-01",
                "observacion", "Mismo plan")).andExpect(status().isConflict());
        enviar("POST", "/api/migraciones", coord, Map.of("idEstudiante", 14, "planDestino", "148-2", "fecha", "2026-11-01",
                "observacion", "Convalidación de 8 materias")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.planOrigen").value("148-1"));
        assertThat(estudiantes.findById(14).orElseThrow().getPlan()).isEqualTo("148-2");
        enviar("POST", "/api/migraciones", coord, Map.of("idEstudiante", 14, "planDestino", "148-9", "fecha", "2026-11-01",
                "observacion", "x")).andExpect(status().isBadRequest());
        enviar("POST", "/api/migraciones", tokenApoyo(), Map.of("idEstudiante", 18, "planDestino", "148-2", "fecha", "2026-11-01",
                "observacion", "x")).andExpect(status().isForbidden());
    }

    /** CU-16: un responsable por grupo, coberturas con antecedente y límite de carga horaria. */
    @Test
    void cargaDocenteYCoberturas() throws Exception {
        String coord = tokenCoordinador();
        int grupo = id(enviar("POST", "/api/grupos", coord, Map.of("idMateria", 6, "idPeriodo", 2, "codigo", "C7",
                "cupo", 30, "turno", "NOCHE")).andExpect(status().isCreated()));
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 4, "idGrupo", grupo, "tipo", "TITULAR", "horas", 64,
                "fechaInicio", "2026-08-03")).andExpect(status().isCreated());
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 5, "idGrupo", grupo, "tipo", "TITULAR", "horas", 64,
                "fechaInicio", "2026-08-03")).andExpect(status().isConflict());
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 5, "idGrupo", grupo, "tipo", "COBERTURA", "horas", 16,
                "fechaInicio", "2026-10-01")).andExpect(status().isBadRequest());
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 5, "idGrupo", grupo, "tipo", "COBERTURA", "horas", 16,
                "fechaInicio", "2026-10-01", "fechaFin", "2026-10-15", "observacion", "Cobertura por licencia médica del titular"))
                .andExpect(status().isCreated());
        // Horas superiores a las de la materia
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 8, "idGrupo", grupo, "tipo", "COBERTURA", "horas", 200,
                "fechaInicio", "2026-10-01", "observacion", "x")).andExpect(status().isBadRequest());
        // Docente 9 (HORARIO, 280 h) ya tiene 272 h: excede el límite
        int otro = id(enviar("POST", "/api/grupos", coord, Map.of("idMateria", 8, "idPeriodo", 2, "codigo", "C8",
                "cupo", 30, "turno", "NOCHE")).andExpect(status().isCreated()));
        enviar("POST", "/api/asignaciones", coord, Map.of("idDocente", 9, "idGrupo", otro, "tipo", "TITULAR", "horas", 64,
                "fechaInicio", "2026-08-03")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("carga horaria")));

        mvc.perform(get("/api/docentes/5/antecedentes").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$[?(@.tipo == 'COBERTURA')]").exists());
        mvc.perform(get("/api/docentes").param("idPeriodo", "2").header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$[0].limiteHoras").isNumber());
    }
}
