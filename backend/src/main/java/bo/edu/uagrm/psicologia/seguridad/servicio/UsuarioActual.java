package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Acceso al usuario autenticado de la petición actual. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static Optional<UsuarioAutenticado> opcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static Optional<Integer> idOpcional() {
        return opcional().map(UsuarioAutenticado::id);
    }

    public static UsuarioAutenticado obtener() {
        return opcional().orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "No hay una sesión activa"));
    }

    public static Integer id() {
        return obtener().id();
    }
}
