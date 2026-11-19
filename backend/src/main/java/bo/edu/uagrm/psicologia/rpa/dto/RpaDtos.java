package bo.edu.uagrm.psicologia.rpa.dto;

import bo.edu.uagrm.psicologia.rpa.modelo.IncidenciaRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.ProcesoRpa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTOs del módulo RPA (CU-31 a CU-37). */
public final class RpaDtos {

    private RpaDtos() {
    }

    // ---------- Catálogos ----------

    public record ProcesoDto(Integer id, String nombre, String descripcion, String version, String estado,
                             int tiempoMaxSegundos, int tiempoManualMinutos) {
        public static ProcesoDto de(ProcesoRpa p) {
            return new ProcesoDto(p.getId(), p.getNombre(), p.getDescripcion(), p.getVersion(), p.getEstado(),
                    p.getTiempoMaxSegundos(), p.getTiempoManualMinutos());
        }
    }

    public record RevisorDto(Integer id, String nombreCompleto, String rol, String correo) {
    }

    public record PrevisualizacionDto(long autorizados, long noAutorizados, Integer idInformeVigente,
                                      String estadoInformeVigente, boolean ejecucionEnCurso) {
    }

    // ---------- CU-31 Iniciar / CU-37 Reejecutar ----------

    public record IniciarEjecucionRequest(
            @NotNull(message = "El procedimiento es obligatorio") Integer idProceso,
            @NotNull(message = "La fecha inicial es obligatoria") LocalDate periodoDesde,
            @NotNull(message = "La fecha final es obligatoria") LocalDate periodoHasta,
            @NotNull @Pattern(regexp = "TODAS|RECIBIDA|EMITIDA", message = "Tipo inválido (TODAS, RECIBIDA o EMITIDA)") String tipoCorrespondencia,
            @NotNull(message = "El revisor es obligatorio") Integer idRevisor,
            @NotEmpty(message = "Indique al menos un destinatario del correo")
            @Size(max = 10, message = "Máximo 10 destinatarios") List<@Email(message = "Correo inválido") @NotBlank String> destinatariosCorreo) {
    }

    public record ReejecutarRequest(
            @Size(max = 10) List<@Email(message = "Correo inválido") @NotBlank String> destinatariosCorreo,
            Integer idRevisor,
            @Size(max = 300) String motivo) {
    }

    // ---------- CU-36 Consulta ----------

    public record EjecucionDto(Integer id, String proceso, LocalDate periodoDesde, LocalDate periodoHasta,
                               String tipoCorrespondencia, String estado, String situacion, Integer idUsuario,
                               String iniciadaPor, Integer idRevisor, String revisor, LocalDateTime fechaInicio,
                               LocalDateTime fechaFin, Long duracionMs, int registrosProcesados, int registrosValidos,
                               int erroresDetectados, Integer idEjecucionOrigen, Integer idReejecucion,
                               Integer idInforme, String estadoInforme, String estadoCorreo, long incidencias) {
    }

    public record IncidenciaDto(Integer id, Integer idEjecucion, String tipo, String descripcion, String estado,
                                Integer idReejecucion, LocalDateTime fechaRegistro) {
        public static IncidenciaDto de(IncidenciaRpa i) {
            return new IncidenciaDto(i.getId(), i.getIdEjecucion(), i.getTipo(), i.getDescripcion(), i.getEstado(),
                    i.getIdReejecucion(), i.getFechaRegistro());
        }
    }

    public record EjecucionDetalleDto(EjecucionDto ejecucion, List<Map<String, Object>> pasos,
                                      List<IncidenciaDto> incidencias, String mensajeError, boolean puedeReejecutar,
                                      String motivoNoReejecutable) {
    }

    public record MetricasDto(long totalEjecuciones, long completadas, long fallidas, long enCurso, double tasaExito,
                              Double duracionPromedioMs, Long duracionMaximaMs, long registrosProcesados,
                              long erroresDetectados, int tiempoManualMinutos, double tiempoAutomatizadoPromedioMin,
                              double ahorroEstimadoHoras, long informesPendientes, long informesAprobados,
                              long informesDevueltos, long correosEnviados, long reejecuciones) {
    }

    public record CerrarIncidenciaRequest(@NotBlank(message = "El comentario es obligatorio") @Size(max = 300) String comentario) {
    }

    // ---------- CU-32 Revisión ----------

    public record InformeDto(Integer id, Integer idEjecucion, LocalDate periodoDesde, LocalDate periodoHasta,
                             String tipoCorrespondencia, int totalRecibidos, int totalEmitidos, String hashSha256,
                             LocalDateTime fechaGeneracion, String estadoRevision, String observacionesRevision,
                             LocalDateTime fechaRevision, Integer idRevisor, String revisor, String estadoCorreo) {
    }

    public record DetalleDto(String categoria, String clave, int cantidad) {
    }

    public record CorreoDto(Integer id, List<String> destinatarios, String asunto, String cuerpo, String estado,
                            Integer idUsuarioAutoriza, String autorizadoPor, LocalDateTime fechaAutorizacion,
                            LocalDateTime fechaEnvio, String mensajeError, String adjunto) {
    }

    public record InformeDetalleDto(InformeDto informe, List<DetalleDto> detalles, CorreoDto correo,
                                    List<IncidenciaDto> incidencias, boolean puedeAprobar, boolean puedeDevolver,
                                    boolean puedeEditarBorrador, boolean puedeReintentarEnvio) {
    }

    public record EditarBorradorRequest(
            @NotEmpty(message = "Indique al menos un destinatario") @Size(max = 10)
            List<@Email(message = "Correo inválido") @NotBlank String> destinatarios,
            @NotBlank(message = "El asunto es obligatorio") @Size(max = 300) String asunto,
            @NotBlank(message = "El cuerpo es obligatorio") @Size(max = 10000) String cuerpo) {
    }

    public record DevolverRequest(
            @NotBlank(message = "Las observaciones son obligatorias") @Size(min = 10, max = 2000,
                    message = "Las observaciones deben tener entre 10 y 2000 caracteres") String observaciones) {
    }

    // ---------- Notificaciones de la cabecera ----------

    public record NotificacionDto(String tipo, String titulo, String detalle, String ruta, LocalDateTime fecha) {
    }
}
