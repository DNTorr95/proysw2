package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.modelo.TransferenciaAutoridad;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.RolRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.TransferenciaAutoridadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula el rol y los permisos EFECTIVOS de un usuario: los de su rol más, si tiene una transferencia
 * de autoridad vigente (CU-04), los de DIRECTOR_CARRERA. Se evalúa en cada petición.
 */
@Service
public class AutoridadService {

    private final TransferenciaAutoridadRepository transferencias;
    private final RolRepository roles;

    public AutoridadService(TransferenciaAutoridadRepository transferencias, RolRepository roles) {
        this.transferencias = transferencias;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public Optional<TransferenciaAutoridad> delegacionActiva(Integer idUsuario) {
        LocalDate hoy = LocalDate.now();
        return transferencias.findByDestinoIdAndEstado(idUsuario, TransferenciaAutoridad.VIGENTE).stream()
                .filter(t -> t.activaEn(hoy)).findFirst();
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticado principalDe(Usuario u) {
        Set<String> permisos = u.getRol().getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toCollection(HashSet::new));
        String rol = u.getRol().getNombre();
        if (!Rol.DIRECTOR.equals(rol) && delegacionActiva(u.getId()).isPresent()) {
            roles.findByNombre(Rol.DIRECTOR).ifPresent(r -> r.getPermisos().forEach(p -> permisos.add(p.getCodigo())));
            // La autoridad delegada no incluye volver a delegarla
            permisos.remove("TRANSFERIR_AUTORIDAD");
            rol = Rol.DIRECTOR;
        }
        return new UsuarioAutenticado(u.getId(), u.getNombreUsuario(), rol, Set.copyOf(permisos));
    }
}
