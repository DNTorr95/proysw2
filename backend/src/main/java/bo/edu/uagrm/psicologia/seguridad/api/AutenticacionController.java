package bo.edu.uagrm.psicologia.seguridad.api;

import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CambiarContrasenaRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.LoginRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.LoginResponse;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.PerfilDto;
import bo.edu.uagrm.psicologia.seguridad.servicio.AutenticacionService;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CU-01 Gestionar cuenta y acceso. Cualquier usuario autenticado puede usar estos endpoints. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "CU-01 Cuenta y acceso")
public class AutenticacionController {

    private final AutenticacionService servicio;

    public AutenticacionController(AutenticacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión (JWT de 8 h). Bloquea la cuenta tras 5 intentos fallidos")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return servicio.iniciarSesion(req);
    }

    @GetMapping("/perfil")
    @Operation(summary = "Perfil y permisos del usuario autenticado")
    public PerfilDto perfil() {
        return servicio.perfil(UsuarioActual.id());
    }

    @PostMapping("/cambiar-contrasena")
    @Operation(summary = "Cambiar la contraseña propia")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest req) {
        servicio.cambiarContrasena(UsuarioActual.id(), req);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Registrar el cierre de sesión en la auditoría")
    public ResponseEntity<Void> logout() {
        servicio.cerrarSesion(UsuarioActual.id());
        return ResponseEntity.noContent().build();
    }
}
