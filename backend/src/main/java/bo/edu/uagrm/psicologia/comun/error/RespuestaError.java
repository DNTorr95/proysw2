package bo.edu.uagrm.psicologia.comun.error;

import java.time.LocalDateTime;
import java.util.Map;

/** Cuerpo estándar de error de la API. */
public record RespuestaError(LocalDateTime fecha, int estado, String error, String mensaje, String ruta,
                             Map<String, String> detalles) {
}
