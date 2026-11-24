package bo.edu.uagrm.psicologia.seguridad.api;

import bo.edu.uagrm.psicologia.seguridad.servicio.TransferenciaAutoridadService;
import bo.edu.uagrm.psicologia.seguridad.servicio.TransferenciaAutoridadService.TransferenciaDto;
import bo.edu.uagrm.psicologia.seguridad.servicio.TransferenciaAutoridadService.TransferirRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CU-04 Transferir autoridad de Dirección. */
@RestController
@RequestMapping("/api/transferencias-autoridad")
@Tag(name = "CU-04 Transferencia de autoridad")
@PreAuthorize("hasAuthority('TRANSFERIR_AUTORIDAD')")
public class TransferenciaAutoridadController {

    private final TransferenciaAutoridadService servicio;

    public TransferenciaAutoridadController(TransferenciaAutoridadService servicio) {
        this.servicio = servicio;
    }

    public record RevocarRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    @GetMapping
    @Operation(summary = "Historial de transferencias de autoridad")
    public List<TransferenciaDto> listar() {
        return servicio.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transferir la autoridad de Dirección a otro usuario (con fechas y motivo)")
    public TransferenciaDto transferir(@Valid @RequestBody TransferirRequest req) {
        return servicio.transferir(req);
    }

    @PostMapping(value = "/{id}/respaldo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Adjuntar o reemplazar el documento de respaldo de una transferencia vigente (PDF/JPG/PNG/DOCX, máx. 10 MB)")
    public TransferenciaDto respaldo(@PathVariable Integer id, @RequestPart("archivo") MultipartFile archivo) {
        return servicio.adjuntarRespaldo(id, archivo);
    }

    @PostMapping("/{id}/revocacion")
    @Operation(summary = "Revocar una transferencia vigente")
    public TransferenciaDto revocar(@PathVariable Integer id, @Valid @RequestBody RevocarRequest req) {
        return servicio.revocar(id, req.motivo());
    }
}
