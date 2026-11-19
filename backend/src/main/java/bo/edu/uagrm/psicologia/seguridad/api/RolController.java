package bo.edu.uagrm.psicologia.seguridad.api;

import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.ActualizarPermisosRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.ActualizarRolRequest;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.PermisoDto;
import bo.edu.uagrm.psicologia.seguridad.dto.SeguridadDtos.RolDto;
import bo.edu.uagrm.psicologia.seguridad.servicio.RolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CU-02 Gestionar roles y permisos institucionales. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-02 Roles y permisos")
public class RolController {

    private final RolService servicio;

    public RolController(RolService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyAuthority('GESTIONAR_ROLES_PERMISOS', 'GESTIONAR_USUARIOS')")
    @Operation(summary = "Listar los roles con sus permisos")
    public List<RolDto> roles() {
        return servicio.listarRoles();
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('GESTIONAR_ROLES_PERMISOS')")
    @Operation(summary = "Catálogo de permisos por módulo")
    public List<PermisoDto> permisos() {
        return servicio.listarPermisos();
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_ROLES_PERMISOS')")
    @Operation(summary = "Actualizar la descripción de un rol")
    public RolDto actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarRolRequest req) {
        return servicio.actualizarDescripcion(id, req.descripcion());
    }

    @PutMapping("/roles/{id}/permisos")
    @PreAuthorize("hasAuthority('GESTIONAR_ROLES_PERMISOS')")
    @Operation(summary = "Reemplazar el conjunto de permisos de un rol")
    public RolDto actualizarPermisos(@PathVariable Integer id, @Valid @RequestBody ActualizarPermisosRequest req) {
        return servicio.actualizarPermisos(id, req.permisos());
    }
}
