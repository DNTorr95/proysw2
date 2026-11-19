package bo.edu.uagrm.psicologia.tramites.dto;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.tramites.modelo.Dictamen;
import bo.edu.uagrm.psicologia.tramites.modelo.RequisitoTipoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.TipoTramite;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs del módulo de trámites (CU-09 a CU-12). */
public final class TramitesDtos {

    private TramitesDtos() {
    }

    // ---------- Catálogos ----------

    public record RequisitoDto(Integer id, String descripcion, boolean obligatorio) {
        public static RequisitoDto de(RequisitoTipoTramite r) {
            return new RequisitoDto(r.getId(), r.getDescripcion(), r.isObligatorio());
        }
    }

    public record TipoTramiteDto(Integer id, String nombre, String descripcion, int plazoReferenciaDias,
                                 boolean requiereDictamen, List<RequisitoDto> requisitos) {
        public static TipoTramiteDto de(TipoTramite t) {
            return new TipoTramiteDto(t.getId(), t.getNombre(), t.getDescripcion(), t.getPlazoReferenciaDias(),
                    t.isRequiereDictamen(), t.getRequisitos().stream().map(RequisitoDto::de).toList());
        }
    }

    public record EstudianteDto(Integer id, String registro, String nombreCompleto, String ci, String plan, String estado) {
        public static EstudianteDto de(Estudiante e) {
            return new EstudianteDto(e.getId(), e.getRegistro(), e.nombreCompleto(), e.getPersona().getCi(),
                    e.getPlan(), e.getEstado());
        }
    }

    // ---------- Registro (CU-09 / CU-10) ----------

    public record RegistrarTramiteRequest(
            @NotNull(message = "El estudiante es obligatorio") Integer idEstudiante,
            @NotNull(message = "El tipo de trámite es obligatorio") Integer idTipoTramite,
            @Pattern(regexp = "ALTA|MEDIA|BAJA", message = "Prioridad inválida (ALTA, MEDIA o BAJA)") String prioridad,
            @Size(max = 2000) String observaciones,
            List<Integer> requisitosPresentados,
            Integer idExpediente) {
    }

    // ---------- Consulta (CU-12) ----------

    public record TramiteDto(Integer id, String numero, Integer idTipoTramite, String tipo, Integer idEstudiante,
                             String estudiante, String registro, Integer idExpediente, String expediente,
                             LocalDateTime fechaRecepcion, LocalDate fechaLimite, LocalDateTime fechaCierre,
                             String estadoActual, String prioridad, String observaciones, boolean vencido,
                             long diasRestantes, String registradoPor) {
    }

    public record DocumentoTramiteDto(Integer id, Integer idRequisito, String descripcion, boolean obligatorio,
                                      boolean esRequisito, boolean presentado, boolean verificado,
                                      String observacion, ArchivoDto archivo) {
    }

    public record HistorialDto(Integer id, String estadoOrigen, String estadoDestino, String evento, Integer idUsuario,
                               String usuario, LocalDateTime fechaHora, String observacion) {
    }

    public record DictamenDto(Integer id, LocalDate fecha, String resultado, String fundamento) {
        public static DictamenDto de(Dictamen d) {
            return d == null ? null : new DictamenDto(d.getId(), d.getFecha(), d.getResultado(), d.getFundamento());
        }
    }

    /** Acción disponible para el usuario actual sobre el trámite. */
    public record AccionDto(String evento, String etiqueta, String destino, boolean requiereJustificacion) {
    }

    public record TramiteDetalleDto(TramiteDto tramite, List<DocumentoTramiteDto> documentos,
                                    List<HistorialDto> historial, DictamenDto dictamen, List<AccionDto> acciones,
                                    int requisitosPendientes, boolean requiereDictamen) {
    }

    // ---------- Actuaciones (CU-11) ----------

    public record EventoRequest(@Size(max = 2000) String justificacion) {
    }

    public record RequisitoRequest(Boolean presentado, Boolean verificado, @Size(max = 300) String observacion) {
    }

    public record DictamenRequest(
            @NotBlank @Pattern(regexp = "FAVORABLE|DESFAVORABLE", message = "Resultado inválido (FAVORABLE o DESFAVORABLE)") String resultado,
            @NotBlank(message = "El fundamento es obligatorio") @Size(max = 4000) String fundamento) {
    }
}
