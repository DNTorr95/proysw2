package bo.edu.uagrm.psicologia.academico.dto;

import bo.edu.uagrm.psicologia.academico.modelo.AsignacionDocente;
import bo.edu.uagrm.psicologia.academico.modelo.Materia;
import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import bo.edu.uagrm.psicologia.academico.modelo.RegistroMigracion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** DTOs del módulo académico (CU-13 a CU-16). */
public final class AcademicoDtos {

    private AcademicoDtos() {
    }

    public static final String PLANES = "148-1|148-2";

    // ---------- CU-13 Periodos ----------

    public record PeriodoDto(Integer id, int gestion, int semestre, String nombre, LocalDate fechaInicio, LocalDate fechaFin,
                             LocalDate fechaCierreInscripcion, String estado, long grupos) {
        public static PeriodoDto de(PeriodoAcademico p, long grupos) {
            return new PeriodoDto(p.getId(), p.getGestion(), p.getSemestre(), p.nombre(), p.getFechaInicio(), p.getFechaFin(),
                    p.getFechaCierreInscripcion(), p.getEstado(), grupos);
        }
    }

    public record PeriodoRequest(
            @NotNull @Min(value = 2000, message = "Gestión inválida") @Max(value = 2100, message = "Gestión inválida") Integer gestion,
            @NotNull @Min(value = 1, message = "Semestre inválido") @Max(value = 2, message = "Semestre inválido") Integer semestre,
            @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
            @NotNull(message = "La fecha de fin es obligatoria") LocalDate fechaFin,
            LocalDate fechaCierreInscripcion) {
    }

    public record EstadoPeriodoRequest(@NotBlank @Pattern(regexp = "PLANIFICADO|ABIERTO|CERRADO", message = "Estado inválido") String estado) {
    }

    public record CopiarGruposRequest(@NotNull(message = "Indique el periodo de origen") Integer idPeriodoOrigen) {
    }

    public record ResumenPlanificacionDto(PeriodoDto periodo, long materias, long grupos, long cupoTotal, long inscritos,
                                          long gruposSinDocente, long gruposLlenos, double ocupacion) {
    }

    // ---------- CU-14 Materias, grupos, cupos e inscripciones ----------

    public record MateriaDto(Integer id, String sigla, String nombre, String plan, int nivel, int horas) {
        public static MateriaDto de(Materia m) {
            return new MateriaDto(m.getId(), m.getSigla(), m.getNombre(), m.getPlan(), m.getNivel(), m.getHoras());
        }
    }

    public record MateriaRequest(
            @NotBlank(message = "La sigla es obligatoria") @Pattern(regexp = "^[A-Z]{2,5}-\\d{3}$", message = "Sigla con formato AAA-000") String sigla,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @NotBlank @Pattern(regexp = PLANES, message = "Plan inválido (148-1 o 148-2)") String plan,
            @NotNull @Min(1) @Max(10) Integer nivel,
            @NotNull @Min(value = 16, message = "Mínimo 16 horas") @Max(value = 400, message = "Máximo 400 horas") Integer horas) {
    }

    public record GrupoDto(Integer id, Integer idMateria, String sigla, String materia, String plan, int nivel, Integer idPeriodo,
                           String periodo, String codigo, int cupo, int inscritos, int disponibles, String turno,
                           String horario, String aula, List<String> docentes) {
    }

    public record GrupoRequest(
            @NotNull(message = "La materia es obligatoria") Integer idMateria,
            @NotNull(message = "El periodo es obligatorio") Integer idPeriodo,
            @NotBlank @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Código de grupo inválido") String codigo,
            @NotNull @Min(value = 1, message = "El cupo debe ser mayor a cero") @Max(value = 200, message = "Cupo máximo 200") Integer cupo,
            @NotBlank @Pattern(regexp = "MANANA|TARDE|NOCHE", message = "Turno inválido") String turno,
            @Size(max = 100) String horario,
            @Size(max = 30) String aula) {
    }

    public record ActualizarGrupoRequest(
            @NotNull @Min(1) @Max(200) Integer cupo,
            @NotBlank @Pattern(regexp = "MANANA|TARDE|NOCHE", message = "Turno inválido") String turno,
            @Size(max = 100) String horario,
            @Size(max = 30) String aula) {
    }

    public record InscripcionDto(Integer id, Integer idEstudiante, String registro, String estudiante, String plan,
                                 LocalDate fechaInscripcion, String estado, boolean casoEspecial) {
    }

    public record InscribirRequest(
            @NotNull(message = "El estudiante es obligatorio") Integer idEstudiante,
            boolean casoEspecial,
            @Size(max = 300) String motivo) {
    }

    public record CambioGrupoRequest(@NotNull Integer idGrupoDestino, @NotBlank @Size(max = 300) String motivo) {
    }

    // ---------- CU-15 Migraciones ----------

    public record MigracionDto(Integer id, Integer idEstudiante, String registro, String estudiante, String planOrigen,
                               String planDestino, LocalDate fecha, String observacion) {
        public static MigracionDto de(RegistroMigracion m) {
            return new MigracionDto(m.getId(), m.getEstudiante().getId(), m.getEstudiante().getRegistro(),
                    m.getEstudiante().nombreCompleto(), m.getPlanOrigen(), m.getPlanDestino(), m.getFecha(), m.getObservacion());
        }
    }

    public record MigracionRequest(
            @NotNull(message = "El estudiante es obligatorio") Integer idEstudiante,
            @NotBlank @Pattern(regexp = PLANES, message = "Plan de destino inválido") String planDestino,
            @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
            @NotBlank(message = "La observación (convalidaciones, motivo) es obligatoria") @Size(max = 2000) String observacion) {
    }

    // ---------- CU-16 Carga docente ----------

    public record DocenteDto(Integer id, String codigo, String nombreCompleto, String categoria, String dedicacion,
                             String especialidad, int limiteHoras, long horasAsignadas) {
    }

    public record AsignacionDto(Integer id, Integer idDocente, String docente, Integer idGrupo, String grupo, String materia,
                                String periodo, String tipo, int horas, LocalDate fechaInicio, LocalDate fechaFin,
                                String observacion, boolean vigente) {
        public static AsignacionDto de(AsignacionDocente a) {
            var g = a.getGrupo();
            return new AsignacionDto(a.getId(), a.getDocente().getId(), a.getDocente().getPersona().nombreCompleto(),
                    g.getId(), g.getMateria().getSigla() + " " + g.getCodigo(), g.getMateria().getNombre(),
                    g.getPeriodo().nombre(), a.getTipo(), a.getHorasAsignadas(), a.getFechaInicio(), a.getFechaFin(),
                    a.getObservacion(), a.vigente(LocalDate.now()));
        }
    }

    public record AsignacionRequest(
            @NotNull(message = "El docente es obligatorio") Integer idDocente,
            @NotNull(message = "El grupo es obligatorio") Integer idGrupo,
            @NotBlank @Pattern(regexp = "TITULAR|INTERINO|COBERTURA", message = "Tipo inválido") String tipo,
            @NotNull @Min(value = 1, message = "Horas inválidas") @Max(value = 400) Integer horas,
            @NotNull LocalDate fechaInicio,
            LocalDate fechaFin,
            @Size(max = 300) String observacion) {
    }

    public record FinalizarAsignacionRequest(@NotNull LocalDate fecha, @NotBlank @Size(max = 300) String observacion) {
    }
}
