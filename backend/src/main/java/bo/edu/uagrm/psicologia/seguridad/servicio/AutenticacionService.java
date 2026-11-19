package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CambiarContrasenaRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.LoginRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.LoginResponse;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.PerfilDto;
import bo.edu.uagrm.psicologia.seguridad.jwt.JwtService;
import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.PersonalAdministrativoRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** CU-01 Gestionar cuenta y acceso: inicio de sesión, perfil y cambio de contraseña. */
@Service
public class AutenticacionService {

    static final String MSG_CREDENCIALES = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarios;
    private final PersonalAdministrativoRepository personal;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditoriaService auditoria;
    private final int maxIntentos;
    private final AutoridadService autoridad;
    private final String hashFicticio;

    public AutenticacionService(UsuarioRepository usuarios, PersonalAdministrativoRepository personal,
                                PasswordEncoder encoder, JwtService jwt, AuditoriaService auditoria,
                                PropiedadesApp props, AutoridadService autoridad) {
        this.autoridad = autoridad;
        this.usuarios = usuarios;
        this.personal = personal;
        this.encoder = encoder;
        this.jwt = jwt;
        this.auditoria = auditoria;
        this.maxIntentos = props.seguridad().maxIntentosFallidos();
        this.hashFicticio = encoder.encode("hash-ficticio-para-tiempo-constante");
    }

    /**
     * Inicia sesión. Tras {@code maxIntentos} contraseñas incorrectas consecutivas la cuenta queda BLOQUEADA
     * y solo el Director puede reactivarla. El contador se guarda aunque se lance la excepción.
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public LoginResponse iniciarSesion(LoginRequest req) {
        Optional<Usuario> encontrado = usuarios.findByNombreUsuarioIgnoreCase(req.nombreUsuario().trim());
        if (encontrado.isEmpty()) {
            // Se compara contra un hash ficticio para no revelar por tiempo de respuesta si el usuario existe.
            encoder.matches(req.contrasena(), hashFicticio);
            auditoria.registrarComo(null, "LOGIN_FALLIDO", "USUARIO", null,
                    "Usuario inexistente: " + recortar(req.nombreUsuario()));
            throw new NegocioException(HttpStatus.UNAUTHORIZED, MSG_CREDENCIALES);
        }
        Usuario u = encontrado.get();

        if (u.getEstado() == EstadoCuenta.BLOQUEADO) {
            auditoria.registrarComo(u.getId(), "LOGIN_RECHAZADO", "USUARIO", u.getId(), "Cuenta bloqueada");
            throw new NegocioException(HttpStatus.LOCKED,
                    "La cuenta está bloqueada por intentos fallidos. Solicite el desbloqueo a la Dirección de Carrera");
        }
        if (u.getEstado() == EstadoCuenta.INACTIVO) {
            auditoria.registrarComo(u.getId(), "LOGIN_RECHAZADO", "USUARIO", u.getId(), "Cuenta inactiva");
            throw new NegocioException(HttpStatus.FORBIDDEN, "La cuenta está inactiva");
        }

        if (!encoder.matches(req.contrasena(), u.getContrasenaHash())) {
            boolean bloqueada = u.registrarIntentoFallido(maxIntentos);
            usuarios.save(u);
            auditoria.registrarComo(u.getId(), "LOGIN_FALLIDO", "USUARIO", u.getId(),
                    "Intento fallido " + u.getIntentosFallidos() + " de " + maxIntentos);
            if (bloqueada) {
                auditoria.registrarComo(u.getId(), "CUENTA_BLOQUEADA", "USUARIO", u.getId(),
                        "Bloqueo automático tras " + maxIntentos + " intentos fallidos");
                throw new NegocioException(HttpStatus.LOCKED,
                        "La cuenta fue bloqueada tras " + maxIntentos + " intentos fallidos. Solicite el desbloqueo a la Dirección de Carrera");
            }
            int restantes = maxIntentos - u.getIntentosFallidos();
            throw new NegocioException(HttpStatus.UNAUTHORIZED,
                    MSG_CREDENCIALES + ". Intentos restantes antes del bloqueo: " + restantes);
        }

        u.registrarAccesoExitoso();
        usuarios.save(u);
        auditoria.registrarComo(u.getId(), "LOGIN", "USUARIO", u.getId(), "Inicio de sesión exitoso");
        JwtService.TokenEmitido token = jwt.emitir(u);
        return new LoginResponse(token.token(), token.expira(), perfilDe(u));
    }

    @Transactional(readOnly = true)
    public PerfilDto perfil(Integer idUsuario) {
        return perfilDe(buscar(idUsuario));
    }

    @Transactional
    public void cambiarContrasena(Integer idUsuario, CambiarContrasenaRequest req) {
        Usuario u = buscar(idUsuario);
        if (!encoder.matches(req.contrasenaActual(), u.getContrasenaHash())) {
            auditoria.registrar("CAMBIO_CONTRASENA_FALLIDO", "USUARIO", u.getId(), "Contraseña actual incorrecta");
            throw new NegocioException("La contraseña actual es incorrecta");
        }
        if (encoder.matches(req.contrasenaNueva(), u.getContrasenaHash())) {
            throw new NegocioException("La nueva contraseña debe ser distinta de la actual");
        }
        u.cambiarContrasena(encoder.encode(req.contrasenaNueva()));
        auditoria.registrar("CAMBIO_CONTRASENA", "USUARIO", u.getId(), "El usuario cambió su contraseña");
    }

    public void cerrarSesion(Integer idUsuario) {
        auditoria.registrar("LOGOUT", "USUARIO", idUsuario, "Cierre de sesión");
    }

    private Usuario buscar(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }

    private PerfilDto perfilDe(Usuario u) {
        String cargo = personal.findById(u.getPersona().getId()).map(p -> p.getCargo()).orElse(null);
        UsuarioAutenticado efectivo = autoridad.principalDe(u);
        String delegada = autoridad.delegacionActiva(u.getId())
                .map(t -> "Autoridad de Dirección delegada por " + t.getOrigen().getPersona().nombreCompleto()
                        + (t.getFechaFin() == null ? " (indefinida)" : " hasta el " + t.getFechaFin()))
                .orElse(null);
        return new PerfilDto(u.getId(), u.getNombreUsuario(), u.getPersona().nombreCompleto(),
                u.getPersona().getCorreo(), efectivo.rol(), u.getRol().getNombre(), delegada, cargo, u.getUltimoAcceso(),
                new java.util.TreeSet<>(efectivo.permisos()));
    }

    private static String recortar(String s) {
        return s == null ? null : s.substring(0, Math.min(s.length(), 50));
    }
}
