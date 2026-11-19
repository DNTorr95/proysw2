package bo.edu.uagrm.psicologia.seguridad.dto;

import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** DTOs del módulo de seguridad (CU-01, CU-02). */
public final class SeguridadDtos {

    private SeguridadDtos() {
    }

    /** Política de contraseñas: mínimo 8 caracteres, al menos una letra y un número. */
    public static final String REGEX_CONTRASENA = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String MENSAJE_CONTRASENA = "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número";

    // ---------- Autenticación ----------

    public record LoginRequest(
            @NotBlank(message = "El usuario es obligatorio") @Size(max = 50) String nombreUsuario,
            @NotBlank(message = "La contraseña es obligatoria") @Size(max = 72) String contrasena) {
    }

    public record LoginResponse(String token, Instant expira, PerfilDto perfil) {
    }

    public record CambiarContrasenaRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String contrasenaActual,
            @NotBlank @Pattern(regexp = REGEX_CONTRASENA, message = MENSAJE_CONTRASENA) String contrasenaNueva) {
    }

    /** rol = rol efectivo; rolPropio = rol asignado; autoridadDelegada describe una transferencia vigente (CU-04). */
    public record PerfilDto(Integer id, String nombreUsuario, String nombreCompleto, String correo, String rol,
                            String rolPropio, String autoridadDelegada, String cargo, LocalDateTime ultimoAcceso,
                            Set<String> permisos) {
    }

    // ---------- Usuarios ----------

    public record UsuarioDto(Integer id, String nombreUsuario, Integer idPersona, String ci, String nombres,
                             String apellidos, String correo, String telefono, Integer idRol, String rol,
                             String cargo, String area, EstadoCuenta estado, int intentosFallidos,
                             LocalDateTime ultimoAcceso, LocalDateTime fechaCreacion) {
    }

    public record CrearUsuarioRequest(
            @NotBlank(message = "El CI es obligatorio") @Size(max = 20) @Pattern(regexp = "^[0-9A-Za-z-]+$", message = "CI inválido") String ci,
            @NotBlank(message = "Los nombres son obligatorios") @Size(max = 100) String nombres,
            @NotBlank(message = "Los apellidos son obligatorios") @Size(max = 100) String apellidos,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo inválido") @Size(max = 150) String correo,
            @Size(max = 20) String telefono,
            @NotBlank(message = "El cargo es obligatorio") @Size(max = 100) String cargo,
            @Size(max = 100) String area,
            @NotBlank(message = "El nombre de usuario es obligatorio")
            @Pattern(regexp = "^[a-z0-9._-]{4,50}$", message = "Usuario: 4 a 50 caracteres en minúsculas, números, punto, guion o guion bajo")
            String nombreUsuario,
            @NotBlank @Pattern(regexp = REGEX_CONTRASENA, message = MENSAJE_CONTRASENA) String contrasenaInicial,
            @NotNull(message = "El rol es obligatorio") Integer idRol) {
    }

    public record ActualizarUsuarioRequest(
            @NotBlank @Size(max = 100) String nombres,
            @NotBlank @Size(max = 100) String apellidos,
            @NotBlank @Email(message = "Correo inválido") @Size(max = 150) String correo,
            @Size(max = 20) String telefono,
            @NotBlank @Size(max = 100) String cargo,
            @Size(max = 100) String area,
            @NotNull(message = "El rol es obligatorio") Integer idRol) {
    }

    public record CambiarEstadoRequest(
            @NotNull(message = "El estado es obligatorio") EstadoCuenta estado,
            @NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {
    }

    public record RestablecerContrasenaRequest(
            @NotBlank @Pattern(regexp = REGEX_CONTRASENA, message = MENSAJE_CONTRASENA) String contrasenaNueva) {
    }

    // ---------- Roles y permisos ----------

    public record PermisoDto(Integer id, String codigo, String modulo, String descripcion) {
        public static PermisoDto de(Permiso p) {
            return new PermisoDto(p.getId(), p.getCodigo(), p.getModulo(), p.getDescripcion());
        }
    }

    public record RolDto(Integer id, String nombre, String descripcion, long usuariosActivos, List<String> permisos) {
        public static RolDto de(Rol r, long usuariosActivos) {
            return new RolDto(r.getId(), r.getNombre(), r.getDescripcion(), usuariosActivos,
                    r.getPermisos().stream().map(Permiso::getCodigo).sorted(Comparator.naturalOrder()).toList());
        }
    }

    public record ActualizarPermisosRequest(@NotNull(message = "La lista de permisos es obligatoria") Set<String> permisos) {
    }

    public record ActualizarRolRequest(@NotBlank @Size(max = 200) String descripcion) {
    }
}
