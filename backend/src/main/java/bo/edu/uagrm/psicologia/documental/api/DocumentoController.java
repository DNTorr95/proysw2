package bo.edu.uagrm.psicologia.documental.api;

import bo.edu.uagrm.psicologia.comun.web.Descargas;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.servicio.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** CU-07 Gestionar documentos digitales. */
@RestController
@RequestMapping("/api/documentos")
@Tag(name = "CU-07 Documentos digitales")
public class DocumentoController {

    private final DocumentoService servicio;

    public DocumentoController(DocumentoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GESTIONAR_DOCUMENTOS')")
    @Operation(summary = "Listar documentos con filtros (nombre, descripción o prefijo de hash)")
    public Pagina<ArchivoDto> listar(@RequestParam(required = false) String texto,
                                     @RequestParam(required = false) String tipoMime,
                                     @RequestParam(required = false) Integer idExpediente,
                                     @RequestParam(required = false) Integer idCorrespondencia,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, tipoMime, idExpediente, idCorrespondencia, desde, hasta, pagina, tamano);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_DOCUMENTOS')")
    @Operation(summary = "Subir documento (PDF/JPG/PNG/DOCX, máx. 10 MB). Se verifica el tipo real y se calcula SHA-256")
    public ArchivoDto subir(@RequestPart("archivo") MultipartFile archivo,
                            @RequestParam(required = false) @Size(max = 300) String descripcion,
                            @RequestParam(required = false) Integer idExpediente) {
        return servicio.subir(archivo, descripcion, idExpediente);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Metadatos de un documento (el acceso depende de su contexto)")
    public ArchivoDto obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @GetMapping("/{id}/contenido")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Descargar o visualizar un documento (verifica integridad SHA-256)")
    public ResponseEntity<byte[]> contenido(@PathVariable Integer id, @RequestParam(defaultValue = "false") boolean enLinea) {
        DocumentoService.Descarga d = servicio.descargar(id);
        return Descargas.archivo(d.nombre(), d.tipoMime(), d.contenido(), enLinea);
    }

    public record DescripcionRequest(@Size(max = 300) String descripcion) {
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_DOCUMENTOS')")
    @Operation(summary = "Actualizar la descripción")
    public ArchivoDto actualizar(@PathVariable Integer id, @RequestBody DescripcionRequest req) {
        return servicio.actualizarDescripcion(id, req.descripcion());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_DOCUMENTOS')")
    @Operation(summary = "Eliminar documento (no permitido si es requisito de un trámite)")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
