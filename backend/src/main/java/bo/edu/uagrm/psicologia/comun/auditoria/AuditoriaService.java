package bo.edu.uagrm.psicologia.comun.auditoria;

import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra las operaciones sensibles en bitacora_auditoria
 * (usuario, acción, entidad, id, fecha y hora, IP y detalle).
 * La escritura es independiente de la transacción de negocio (pool propio con autocommit, ver
 * {@link EscritorAuditoria}): los intentos fallidos quedan registrados aunque la operación se revierta.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final EscritorAuditoria escritor;

    public AuditoriaService(EscritorAuditoria escritor) {
        this.escritor = escritor;
    }

    /** Registra la acción a nombre del usuario autenticado. */
    public void registrar(String accion, String entidad, Object idEntidad, String detalle) {
        guardar(UsuarioActual.idOpcional().orElse(null), accion, entidad, idEntidad, detalle);
    }

    /** Registra la acción a nombre de un usuario explícito (por ejemplo, en el inicio de sesión o en procesos del sistema). */
    public void registrarComo(Integer idUsuario, String accion, String entidad, Object idEntidad, String detalle) {
        guardar(idUsuario, accion, entidad, idEntidad, detalle);
    }

    private void guardar(Integer idUsuario, String accion, String entidad, Object idEntidad, String detalle) {
        String ip = ipCliente();
        escritor.escribir(idUsuario, recortar(accion, 80), recortar(entidad, 80),
                idEntidad == null ? null : recortar(String.valueOf(idEntidad), 50), ip, recortar(detalle, 4000));
        log.info("auditoria accion={} entidad={} id={} usuario={} ip={}", accion, entidad, idEntidad, idUsuario, ip);
    }

    private static String ipCliente() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            // Con server.forward-headers-strategy=framework, getRemoteAddr ya refleja X-Forwarded-For de nginx
            return req.getRemoteAddr();
        }
        return "sistema";
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() > max ? texto.substring(0, max) : texto;
    }
}
