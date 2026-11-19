package bo.edu.uagrm.psicologia.documental.dto;

import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.modelo.Destinatario;
import bo.edu.uagrm.psicologia.documental.modelo.SeguimientoCorrespondencia;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs del módulo documental (CU-05 a CU-08). */
public final class DocumentalDtos {

    private DocumentalDtos() {
    }

    // ---------- Destinatarios ----------

    public record DestinatarioDto(Integer id, String nombre, String cargo, String unidad, String institucion,
                                  String correo, boolean activo) {
        public static DestinatarioDto de(Destinatario d) {
            return new DestinatarioDto(d.getId(), d.getNombre(), d.getCargo(), d.getUnidad(), d.getInstitucion(),
                    d.getCorreo(), d.isActivo());
        }
    }

    public record DestinatarioRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @Size(max = 100) String cargo,
            @NotBlank(message = "La unidad o dependencia es obligatoria") @Size(max = 150) String unidad,
            @Size(max = 150) String institucion,
            @Email(message = "Correo inválido") @Size(max = 150) String correo,
            Boolean activo) {
    }

    // ---------- Correspondencia ----------

    public record CorrespondenciaDto(Integer id, String codigo, Correspondencia.Tipo tipo, String asunto,
                                     String descripcion, LocalDate fechaDocumento, LocalDateTime fechaRegistro,
                                     Integer idDestinatario, String destinatario, String unidad,
                                     Correspondencia.Estado estado, boolean autorizado, String registradoPor) {
        public static CorrespondenciaDto de(Correspondencia c) {
            return new CorrespondenciaDto(c.getId(), c.getCodigo(), c.getTipo(), c.getAsunto(), c.getDescripcion(),
                    c.getFechaDocumento(), c.getFechaRegistro(), c.getDestinatario().getId(),
                    c.getDestinatario().getNombre(), c.getDestinatario().getUnidad(), c.getEstado(), c.isAutorizado(),
                    c.getUsuarioRegistro().getNombreUsuario());
        }
    }

    public record CorrespondenciaDetalleDto(CorrespondenciaDto correspondencia, List<ArchivoDto> adjuntos,
                                            List<SeguimientoDto> seguimiento) {
    }

    public record CrearCorrespondenciaRequest(
            @NotNull(message = "El tipo (RECIBIDA o EMITIDA) es obligatorio") Correspondencia.Tipo tipo,
            @NotBlank(message = "El asunto es obligatorio") @Size(max = 300) String asunto,
            @Size(max = 4000) String descripcion,
            @NotNull(message = "La fecha del documento es obligatoria")
            @PastOrPresent(message = "La fecha del documento no puede ser futura") LocalDate fechaDocumento,
            @NotNull(message = "El origen/destino es obligatorio") Integer idDestinatario) {
    }

    public record ActualizarCorrespondenciaRequest(
            @NotBlank(message = "El asunto es obligatorio") @Size(max = 300) String asunto,
            @Size(max = 4000) String descripcion,
            @NotNull @PastOrPresent(message = "La fecha del documento no puede ser futura") LocalDate fechaDocumento,
            @NotNull Integer idDestinatario) {
    }

    public record AutorizarRequest(@NotNull Boolean autorizado) {
    }

    public record SeguimientoDto(Integer id, LocalDateTime fecha, String estado, String observacion) {
        public static SeguimientoDto de(SeguimientoCorrespondencia s) {
            return new SeguimientoDto(s.getId(), s.getFecha(), s.getEstado(), s.getObservacion());
        }
    }

    public record SeguimientoRequest(
            @NotNull(message = "El estado es obligatorio") Correspondencia.Estado estado,
            @NotBlank(message = "La observación es obligatoria") @Size(max = 2000) String observacion) {
    }

    // ---------- Documentos digitales ----------

    public record ArchivoDto(Integer id, String nombreArchivo, String tipoMime, long tamanoBytes, String hashSha256,
                             LocalDateTime fechaCarga, Integer idUsuarioCarga, Integer idCorrespondencia,
                             Integer idExpediente, String descripcion) {
        public static ArchivoDto de(ArchivoAdjunto a) {
            return new ArchivoDto(a.getId(), a.getNombreArchivo(), a.getTipoMime(), a.getTamanoBytes(),
                    a.getHashSha256(), a.getFechaCarga(), a.getIdUsuarioCarga(), a.getIdCorrespondencia(),
                    a.getIdExpediente(), a.getDescripcion());
        }
    }

    // ---------- Expedientes ----------

    public record ExpedienteDto(Integer id, String codigo, Integer idEstudiante, String estudiante, String registro,
                                String titulo, String descripcion, String estado, LocalDate fechaApertura) {
    }

    public record TramiteResumenDto(Integer id, String numero, String tipo, String estadoActual,
                                    LocalDateTime fechaRecepcion, LocalDate fechaLimite) {
    }

    public record ExpedienteDetalleDto(ExpedienteDto expediente, List<TramiteResumenDto> tramites,
                                       List<ArchivoDto> documentos) {
    }

    public record CrearExpedienteRequest(
            Integer idEstudiante,
            @NotBlank(message = "El título es obligatorio") @Size(max = 200) String titulo,
            @Size(max = 4000) String descripcion) {
    }

    public record ActualizarExpedienteRequest(
            @NotBlank(message = "El título es obligatorio") @Size(max = 200) String titulo,
            @Size(max = 4000) String descripcion) {
    }

    public record EstadoExpedienteRequest(@NotBlank String estado, @NotBlank @Size(max = 300) String motivo) {
    }

    public record VincularTramiteRequest(@NotNull Integer idTramite) {
    }
}
