package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.ActualizarUsuarioRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CambiarEstadoRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CrearUsuarioRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.RestablecerContrasenaRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.UsuarioDto;
import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Persona;
import bo.edu.uagrm.psicologia.seguridad.modelo.PersonalAdministrativo;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.PersonaRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.PersonalAdministrativoRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.RolRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administración de cuentas de usuario (parte administrativa de CU-01, a cargo del Director). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final PersonalAdministrativoRepository personal;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository usuarios, PersonaRepository personas,
                          PersonalAdministrativoRepository personal, RolRepository roles,
                          PasswordEncoder encoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.personas = personas;
        this.personal = personal;
        this.roles = roles;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioDto> listar(String texto, Integer idRol, EstadoCuenta estado, int pagina, int tamano) {
        String filtro = (texto == null || texto.isBlank()) ? null : texto.trim();
        var page = usuarios.buscar(filtro, idRol, estado,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100), Sort.by("nombreUsuario")));
        return Pagina.de(page, this::aDto);
    }

    @Transactional(readOnly = true)
    public UsuarioDto obtener(Integer id) {
        return aDto(buscar(id));
    }

    @Transactional
    public UsuarioDto crear(CrearUsuarioRequest req) {
        if (personas.existsByCi(req.ci().trim())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una persona registrada con el CI " + req.ci());
        }
        if (usuarios.existsByNombreUsuarioIgnoreCase(req.nombreUsuario())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
        }
        Rol rol = buscarRol(req.idRol());
        Persona persona = personas.save(new Persona(req.ci().trim(), req.nombres().trim(), req.apellidos().trim(),
                req.correo().trim().toLowerCase(), vacioANulo(req.telefono())));
        personal.save(new PersonalAdministrativo(persona.getId(), req.cargo().trim(), vacioANulo(req.area())));
        Usuario u = usuarios.save(new Usuario(persona, rol, req.nombreUsuario(), encoder.encode(req.contrasenaInicial())));
        auditoria.registrar("CREAR", "USUARIO", u.getId(),
                "Usuario " + u.getNombreUsuario() + " creado con rol " + rol.getNombre());
        return aDto(u);
    }

    @Transactional
    public UsuarioDto actualizar(Integer id, ActualizarUsuarioRequest req) {
        Usuario u = buscar(id);
        Rol nuevoRol = buscarRol(req.idRol());
        String rolAnterior = u.getRol().getNombre();
        if (!rolAnterior.equals(nuevoRol.getNombre())) {
            if (id.equals(UsuarioActual.id())) {
                throw new NegocioException("No puede cambiar su propio rol");
            }
            validarQueNoQuedeSinDirector(u);
            u.cambiarRol(nuevoRol);
        }
        u.getPersona().actualizar(req.nombres().trim(), req.apellidos().trim(), req.correo().trim().toLowerCase(),
                vacioANulo(req.telefono()));
        PersonalAdministrativo pa = personal.findById(u.getPersona().getId())
                .orElseGet(() -> new PersonalAdministrativo(u.getPersona().getId(), req.cargo().trim(), vacioANulo(req.area())));
        pa.actualizar(req.cargo().trim(), vacioANulo(req.area()));
        personal.save(pa);
        auditoria.registrar("ACTUALIZAR", "USUARIO", u.getId(),
                "Datos actualizados" + (rolAnterior.equals(nuevoRol.getNombre()) ? "" :
                        "; rol " + rolAnterior + " -> " + nuevoRol.getNombre()));
        return aDto(u);
    }

    @Transactional
    public UsuarioDto cambiarEstado(Integer id, CambiarEstadoRequest req) {
        Usuario u = buscar(id);
        if (id.equals(UsuarioActual.id()) && req.estado() != EstadoCuenta.ACTIVO) {
            throw new NegocioException("No puede bloquear ni desactivar su propia cuenta");
        }
        if (req.estado() != EstadoCuenta.ACTIVO) {
            validarQueNoQuedeSinDirector(u);
        }
        EstadoCuenta anterior = u.getEstado();
        u.cambiarEstado(req.estado());
        String accion = switch (req.estado()) {
            case ACTIVO -> anterior == EstadoCuenta.BLOQUEADO ? "DESBLOQUEAR" : "ACTIVAR";
            case BLOQUEADO -> "BLOQUEAR";
            case INACTIVO -> "DESACTIVAR";
        };
        auditoria.registrar(accion, "USUARIO", u.getId(), anterior + " -> " + req.estado() + ". Motivo: " + req.motivo());
        return aDto(u);
    }

    @Transactional
    public void restablecerContrasena(Integer id, RestablecerContrasenaRequest req) {
        Usuario u = buscar(id);
        u.cambiarContrasena(encoder.encode(req.contrasenaNueva()));
        auditoria.registrar("RESTABLECER_CONTRASENA", "USUARIO", u.getId(),
                "Contraseña restablecida por la Dirección");
    }

    /** Debe quedar siempre al menos un Director activo para administrar el sistema. */
    private void validarQueNoQuedeSinDirector(Usuario afectado) {
        if (Rol.DIRECTOR.equals(afectado.getRol().getNombre()) && afectado.estaActivo()
                && usuarios.countByRolNombreAndEstado(Rol.DIRECTOR, EstadoCuenta.ACTIVO) <= 1) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Debe existir al menos un usuario activo con rol DIRECTOR_CARRERA");
        }
    }

    private Usuario buscar(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private Rol buscarRol(Integer id) {
        return roles.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Rol", id));
    }

    private UsuarioDto aDto(Usuario u) {
        Persona p = u.getPersona();
        PersonalAdministrativo pa = personal.findById(p.getId()).orElse(null);
        return new UsuarioDto(u.getId(), u.getNombreUsuario(), p.getId(), p.getCi(), p.getNombres(), p.getApellidos(),
                p.getCorreo(), p.getTelefono(), u.getRol().getId(), u.getRol().getNombre(),
                pa == null ? null : pa.getCargo(), pa == null ? null : pa.getArea(), u.getEstado(),
                u.getIntentosFallidos(), u.getUltimoAcceso(), u.getFechaCreacion());
    }

    private static String vacioANulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
