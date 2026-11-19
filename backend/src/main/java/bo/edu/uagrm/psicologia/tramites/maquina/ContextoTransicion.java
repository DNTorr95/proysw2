package bo.edu.uagrm.psicologia.tramites.maquina;

/**
 * Datos que las guardas necesitan para decidir una transición.
 *
 * @param rolUsuario                  rol del usuario que dispara el evento
 * @param justificacion               texto ingresado por el usuario (obligatorio en observar/rechazar)
 * @param requisitosObligatoriosPendientes requisitos obligatorios aún no presentados
 */
public record ContextoTransicion(String rolUsuario, String justificacion, int requisitosObligatoriosPendientes) {

    public boolean tieneJustificacion() {
        return justificacion != null && justificacion.trim().length() >= GuardasTramite.LARGO_MINIMO_JUSTIFICACION;
    }
}
