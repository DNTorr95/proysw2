package bo.edu.uagrm.psicologia.documental.api;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ActualizarExpedienteRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CrearExpedienteRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.EstadoExpedienteRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ExpedienteDetalleDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ExpedienteDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.VincularTramiteRequest;
import bo.edu.uagrm.psicologia.documental.servicio.ExpedienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** CU-08 Gestionar expedientes administrativos. */
@RestController
@RequestMapping("/api/expedientes")
@Tag(name = "CU-08 Expedientes")
public class ExpedienteController {

    private static final String CONSULTA = "hasAnyAuthority('GESTIONAR_EXPEDIENTES', 'CONSULTAR_EXPEDIENTES')";
    private static final String GESTION = "hasAuthority('GESTIONAR_EXPEDIENTES')";

    private final ExpedienteService servicio;

    public ExpedienteController(ExpedienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize(CONSULTA)
    @Operation(summary = "Listar expedientes")
    public Pagina<ExpedienteDto> listar(@RequestParam(required = false) String texto,
                                        @RequestParam(required = false) String estado,
                                        @RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "Detalle con trámites y documentos")
    public ExpedienteDetalleDto obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    @Operation(summary = "Abrir expediente")
    public ExpedienteDto crear(@Valid @RequestBody CrearExpedienteRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Actualizar expediente")
    public ExpedienteDto actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarExpedienteRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize(GESTION)
    @Operation(summary = "Cerrar o reabrir expediente")
    public ExpedienteDto estado(@PathVariable Integer id, @Valid @RequestBody EstadoExpedienteRequest req) {
        return servicio.cambiarEstado(id, req.estado(), req.motivo());
    }

    @PostMapping("/{id}/tramites")
    @PreAuthorize(GESTION)
    @Operation(summary = "Vincular un trámite al expediente")
    public ExpedienteDto vincular(@PathVariable Integer id, @Valid @RequestBody VincularTramiteRequest req) {
        return servicio.vincularTramite(id, req.idTramite());
    }

    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    @Operation(summary = "Adjuntar documento al expediente")
    public ArchivoDto adjuntar(@PathVariable Integer id, @RequestPart("archivo") MultipartFile archivo,
                               @RequestParam(required = false) String descripcion) {
        return servicio.adjuntar(id, archivo, descripcion);
    }
}
