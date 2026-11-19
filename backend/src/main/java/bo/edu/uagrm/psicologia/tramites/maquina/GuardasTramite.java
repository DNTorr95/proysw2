package bo.edu.uagrm.psicologia.tramites.maquina;

import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Guardas de la máquina de estados del trámite, por evento:
 * <pre>
 * derivar   RECEPCIONADO -> EN_REVISION   requisitos registrados
 * observar  EN_REVISION  -> OBSERVADO     justificación obligatoria
 * subsanar  OBSERVADO    -> EN_REVISION   documentación completa
 * aprobar   EN_REVISION  -> APROBADO      rol DIRECTOR_CARRERA
 * rechazar  EN_REVISION  -> RECHAZADO     rol DIRECTOR_CARRERA y justificación
 * archivar  APROBADO/RECHAZADO -> ARCHIVADO   (sin guarda)
 * </pre>
 * Cada guarda devuelve el motivo del rechazo, o vacío si la transición se permite.
 */
public final class GuardasTramite {

    public static final int LARGO_MINIMO_JUSTIFICACION = 10;

    private GuardasTramite() {
    }

    private static final Map<String, Function<ContextoTransicion, Optional<String>>> GUARDAS = Map.of(
            "derivar", c -> c.requisitosObligatoriosPendientes() > 0
                    ? Optional.of("No se puede derivar: faltan " + c.requisitosObligatoriosPendientes()
                    + " requisito(s) obligatorio(s) por registrar")
                    : Optional.empty(),
            "observar", c -> c.tieneJustificacion()
                    ? Optional.empty()
                    : Optional.of("La observación requiere una justificación de al menos " + LARGO_MINIMO_JUSTIFICACION + " caracteres"),
            "subsanar", c -> c.requisitosObligatoriosPendientes() > 0
                    ? Optional.of("No se puede subsanar: la documentación está incompleta ("
                    + c.requisitosObligatoriosPendientes() + " requisito(s) obligatorio(s) pendiente(s))")
                    : Optional.empty(),
            "aprobar", c -> Rol.DIRECTOR.equals(c.rolUsuario())
                    ? Optional.empty()
                    : Optional.of("Solo el Director de Carrera puede aprobar trámites"),
            "rechazar", c -> !Rol.DIRECTOR.equals(c.rolUsuario())
                    ? Optional.of("Solo el Director de Carrera puede rechazar trámites")
                    : c.tieneJustificacion() ? Optional.empty()
                    : Optional.of("El rechazo requiere una justificación de al menos " + LARGO_MINIMO_JUSTIFICACION + " caracteres"));

    /** Evalúa la guarda del evento; los eventos sin guarda definida se permiten. */
    public static Optional<String> evaluar(String evento, ContextoTransicion contexto) {
        return GUARDAS.getOrDefault(evento, c -> Optional.empty()).apply(contexto);
    }
}
