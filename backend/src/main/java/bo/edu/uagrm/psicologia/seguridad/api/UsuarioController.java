package bo.edu.uagrm.psicologia.seguridad.api;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.ActualizarUsuarioRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CambiarEstadoRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.CrearUsuarioRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.RestablecerContrasenaRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.UsuarioDto;
import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CU-01 (administración de cuentas) y CU-02: gestión de usuarios por la Dirección. */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "CU-01/CU-02 Usuarios")
@PreAuthorize("hasAuthority('GESTIONAR_USUARIOS')")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios con filtros (texto, rol, estado)")
    public Pagina<UsuarioDto> listar(@RequestParam(required = false) String texto,
                                     @RequestParam(required = false) Integer idRol,
                                     @RequestParam(required = false) EstadoCuenta estado,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, idRol, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario")
    public UsuarioDto obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear usuario (persona + cargo + cuenta con un rol)")
    public UsuarioDto crear(@Valid @RequestBody CrearUsuarioRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar datos, cargo y rol de un usuario")
    public UsuarioDto actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarUsuarioRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Activar, desbloquear, bloquear o desactivar una cuenta (con motivo)")
    public UsuarioDto cambiarEstado(@PathVariable Integer id, @Valid @RequestBody CambiarEstadoRequest req) {
        return servicio.cambiarEstado(id, req);
    }

    @PostMapping("/{id}/restablecer-contrasena")
    @Operation(summary = "Restablecer la contraseña de un usuario")
    public ResponseEntity<Void> restablecer(@PathVariable Integer id, @Valid @RequestBody RestablecerContrasenaRequest req) {
        servicio.restablecerContrasena(id, req);
        return ResponseEntity.noContent().build();
    }
}
