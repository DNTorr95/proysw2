package bo.edu.uagrm.psicologia.rpa.trabajador;

import java.time.Duration;

/** Puerto hacia el trabajador RPA. La implementación real es HTTP; las pruebas usan un doble. */
public interface TrabajadorRpa {

    ContratoTrabajador.Resultado ejecutar(ContratoTrabajador.Solicitud solicitud, Duration limite);

    /** Fallo de comunicación con el trabajador, clasificado como tipo de incidencia. */
    class FalloTrabajador extends RuntimeException {
        private final String tipoIncidencia;

        public FalloTrabajador(String tipoIncidencia, String mensaje, Throwable causa) {
            super(mensaje, causa);
            this.tipoIncidencia = tipoIncidencia;
        }

        public String getTipoIncidencia() {
            return tipoIncidencia;
        }
    }
}
