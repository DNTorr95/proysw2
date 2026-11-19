package bo.edu.uagrm.psicologia.rpa.trabajador;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Contrato REST interno con el trabajador RPA (POST /interno/rpa/ejecuciones).
 * El backend envía solo los datos mínimos; el trabajador nunca accede a la base de datos.
 */
public final class ContratoTrabajador {

    private ContratoTrabajador() {
    }

    public record Registro(String codigo, String tipo, String asunto, String fechaDocumento, String estado,
                           String dependencia, String destinatario) {
    }

    public record Solicitud(int idEjecucion, String proceso, String periodoDesde, String periodoHasta,
                            String tipoCorrespondencia, String plantillaHtml, String plantillaNombre, String revisor,
                            List<String> destinatariosCorreo, int tiempoMaximoSegundos, List<Registro> registros) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resumen(int registrosProcesados, int registrosValidos, int erroresDetectados, int totalRecibidos,
                          int totalEmitidos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detalle(String categoria, String clave, int cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Informe(String ruta, String hashSha256, long tamanoBytes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Borrador(String asunto, String cuerpo, List<String> destinatarios, String adjunto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Incidencia(String tipo, String descripcion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paso(String paso, String descripcion, String estado, String inicio, String fin, long duracionMs,
                       String detalle) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resultado(int idEjecucion, String estado, Resumen resumen, List<Detalle> detalle, Informe informe,
                            Borrador borrador, List<Incidencia> incidencias, List<Paso> pasos, String mensajeError) {

        public boolean completada() {
            return "COMPLETADA".equals(estado);
        }
    }
}
