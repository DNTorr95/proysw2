package bo.edu.uagrm.psicologia.seguridad.servicio;

import java.util.Set;

/** Principal almacenado en el SecurityContext para la petición en curso. */
public record UsuarioAutenticado(Integer id, String nombreUsuario, String rol, Set<String> permisos) {

    public boolean tienePermiso(String codigo) {
        return permisos.contains(codigo);
    }
}
