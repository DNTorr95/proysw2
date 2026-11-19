package bo.edu.uagrm.psicologia.institucional.dto;

import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import bo.edu.uagrm.psicologia.institucional.modelo.Evento;
import bo.edu.uagrm.psicologia.institucional.modelo.SeguimientoActividad;
import bo.edu.uagrm.psicologia.institucional.modelo.Titulado;
import bo.edu.uagrm.psicologia.institucional.modelo.TrabajoGrado;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs del módulo institucional (CU-17 a CU-20). */
public final class InstitucionalDtos {

    private InstitucionalDtos() {
    }

    // ---------- CU-17 Titulados ----------

    public record TituladoDto(Integer id, Integer idEstudiante, String registro, String estudiante, Integer idTrabajo,
                              String tituloTrabajo, int gestion, String modalidad, LocalDate fechaDefensa, String nroDiploma) {
        public static TituladoDto de(Titulado t) {
            return new TituladoDto(t.getId(), t.getEstudiante().getId(), t.getEstudiante().getRegistro(),
                    t.getEstudiante().nombreCompleto(), t.getTrabajo() == null ? null : t.getTrabajo().getId(),
                    t.getTrabajo() == null ? null : t.getTrabajo().getTitulo(), t.getGestion(), t.getModalidad(),
                    t.getFechaDefensa(), t.getNroDiploma());
        }
    }

    public record TituladoRequest(
            @NotNull(message = "El estudiante es obligatorio") Integer idEstudiante,
            Integer idTrabajo,
            @NotNull @Min(2000) @Max(2100) Integer gestion,
            @NotBlank @Pattern(regexp = "TESIS|PROYECTO_GRADO|EXCELENCIA|TRABAJO_DIRIGIDO", message = "Modalidad inválida") String modalidad,
            @NotNull(message = "La fecha de defensa es obligatoria") @PastOrPresent(message = "La fecha de defensa no puede ser futura") LocalDate fechaDefensa,
            @Size(max = 30) String nroDiploma) {
    }

    // ---------- CU-18 Trabajos de grado ----------

    public record TrabajoGradoDto(Integer id, Integer idEstudiante, String registro, String estudiante, String titulo,
                                  String tutor, int gestion, String palabrasClave, ArchivoDto archivo) {
        public static TrabajoGradoDto de(TrabajoGrado t) {
            return new TrabajoGradoDto(t.getId(), t.getEstudiante().getId(), t.getEstudiante().getRegistro(),
                    t.getEstudiante().nombreCompleto(), t.getTitulo(), t.getTutor(), t.getGestion(), t.getPalabrasClave(),
                    t.getArchivo() == null ? null : ArchivoDto.de(t.getArchivo()));
        }
    }

    public record TrabajoGradoRequest(
            @NotNull(message = "El estudiante es obligatorio") Integer idEstudiante,
            @NotBlank(message = "El título es obligatorio") @Size(max = 300) String titulo,
            @Size(max = 150) String tutor,
            @NotNull @Min(2000) @Max(2100) Integer gestion,
            @Size(max = 300) String palabrasClave) {
    }

    // ---------- CU-19 POA ----------

    public record ActividadDto(Integer id, String descripcion, String meta, String responsable, LocalDate fechaInicio,
                               LocalDate fechaFin, BigDecimal avance, String estado, long eventos) {
        public static ActividadDto de(ActividadPoa a, long eventos) {
            return new ActividadDto(a.getId(), a.getDescripcion(), a.getMeta(), a.getResponsable(), a.getFechaInicio(),
                    a.getFechaFin(), a.getAvance(), a.getEstado(), eventos);
        }
    }

    public record PoaResumenDto(Integer id, int gestion, String objetivo, String estado, BigDecimal avance, int actividades,
                                long concluidas, long retrasadas) {
    }

    public record PoaDto(PoaResumenDto poa, List<ActividadDto> actividades) {
    }

    public record PoaRequest(
            @NotNull @Min(2000) @Max(2100) Integer gestion,
            @NotBlank(message = "El objetivo es obligatorio") @Size(max = 4000) String objetivo,
            @Pattern(regexp = "FORMULADO|EN_EJECUCION|CERRADO", message = "Estado inválido") String estado) {
    }

    public record ActividadRequest(
            @NotBlank(message = "La descripción es obligatoria") @Size(max = 300) String descripcion,
            @NotBlank(message = "La meta es obligatoria") @Size(max = 200) String meta,
            @Size(max = 150) String responsable,
            @NotNull LocalDate fechaInicio,
            @NotNull LocalDate fechaFin) {
    }

    public record SeguimientoDto(Integer id, LocalDate fecha, BigDecimal avance, String observacion, String evidencia,
                                 Integer idEvento) {
        public static SeguimientoDto de(SeguimientoActividad s) {
            return new SeguimientoDto(s.getId(), s.getFecha(), s.getAvance(), s.getObservacion(), s.getEvidencia(), s.getIdEvento());
        }
    }

    public record SeguimientoRequest(
            @NotNull @PastOrPresent(message = "La fecha no puede ser futura") LocalDate fecha,
            @NotNull @DecimalMin(value = "0", message = "Avance mínimo 0") @DecimalMax(value = "100", message = "Avance máximo 100") BigDecimal avance,
            @NotBlank(message = "La observación es obligatoria") @Size(max = 2000) String observacion,
            @Size(max = 500) String evidencia,
            Integer idEvento) {
    }

    // ---------- CU-20 Eventos ----------

    public record EventoDto(Integer id, String nombre, String tipo, LocalDate fecha, String responsable, Integer idActividad,
                            String actividad) {
        public static EventoDto de(Evento e) {
            return new EventoDto(e.getId(), e.getNombre(), e.getTipo(), e.getFecha(), e.getResponsable(),
                    e.getActividad() == null ? null : e.getActividad().getId(),
                    e.getActividad() == null ? null : e.getActividad().getDescripcion());
        }
    }

    public record EventoRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @NotBlank @Pattern(regexp = "TALLER|SEMINARIO|EVENTO", message = "Tipo inválido (TALLER, SEMINARIO o EVENTO)") String tipo,
            @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
            @Size(max = 150) String responsable,
            Integer idActividad) {
    }
}
