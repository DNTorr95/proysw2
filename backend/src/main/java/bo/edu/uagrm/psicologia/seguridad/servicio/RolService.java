package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.PermisoDto;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.RolDto;
import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.repositorio.PermisoRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.RolRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * CU-02 Gestionar roles y permisos institucionales.
 * Los tres roles son fijos; lo que el Director ajusta es qué permisos tiene cada uno.
 */
@Service
public class RolService {

    /** Permisos que el rol Director nunca puede perder (evita quedar sin administración del sistema). */
    static final Set<String> PERMISOS_PROTEGIDOS_DIRECTOR = Set.of("GESTIONAR_ROLES_PERMISOS", "GESTIONAR_USUARIOS");

    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public RolService(RolRepository roles, PermisoRepository permisos, UsuarioRepository usuarios,
                      AuditoriaService auditoria) {
        this.roles = roles;
        this.permisos = permisos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RolDto> listarRoles() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(Rol::getId))
                .map(r -> RolDto.de(r, usuarios.countByRolNombreAndEstado(r.getNombre(), EstadoCuenta.ACTIVO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermisoDto> listarPermisos() {
        return permisos.findAllByOrderByModuloAscCodigoAsc().stream().map(PermisoDto::de).toList();
    }

    @Transactional
    public RolDto actualizarDescripcion(Integer idRol, String descripcion) {
        Rol rol = buscar(idRol);
        rol.setDescripcion(descripcion.trim());
        auditoria.registrar("ACTUALIZAR", "ROL", rol.getId(), "Descripción actualizada");
        return RolDto.de(rol, usuarios.countByRolNombreAndEstado(rol.getNombre(), EstadoCuenta.ACTIVO));
    }

    @Transactional
    public RolDto actualizarPermisos(Integer idRol, Set<String> codigos) {
        Rol rol = buscar(idRol);
        List<Permiso> nuevos = permisos.findByCodigoIn(codigos);
        if (nuevos.size() != codigos.size()) {
            Set<String> validos = nuevos.stream().map(Permiso::getCodigo).collect(Collectors.toSet());
            Set<String> invalidos = new TreeSet<>(codigos);
            invalidos.removeAll(validos);
            throw new NegocioException("Permisos inexistentes: " + invalidos);
        }
        if (Rol.DIRECTOR.equals(rol.getNombre()) && !codigos.containsAll(PERMISOS_PROTEGIDOS_DIRECTOR)) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El rol DIRECTOR_CARRERA debe conservar los permisos " + PERMISOS_PROTEGIDOS_DIRECTOR);
        }

        Set<String> anteriores = rol.getPermisos().stream().map(Permiso::getCodigo).collect(Collectors.toSet());
        Set<String> agregados = new TreeSet<>(codigos);
        agregados.removeAll(anteriores);
        Set<String> quitados = new TreeSet<>(anteriores);
        quitados.removeAll(codigos);

        rol.reemplazarPermisos(new HashSet<>(nuevos));
        auditoria.registrar("ACTUALIZAR_PERMISOS", "ROL", rol.getId(),
                rol.getNombre() + " | agregados: " + agregados + " | quitados: " + quitados);
        return RolDto.de(rol, usuarios.countByRolNombreAndEstado(rol.getNombre(), EstadoCuenta.ACTIVO));
    }

    private Rol buscar(Integer id) {
        return roles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Rol", id));
    }
}
