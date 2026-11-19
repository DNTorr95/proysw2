package bo.edu.uagrm.psicologia.documental.api;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ActualizarCorrespondenciaRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.AutorizarRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CorrespondenciaDetalleDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CorrespondenciaDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CrearCorrespondenciaRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.DestinatarioDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.DestinatarioRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.SeguimientoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.SeguimientoRequest;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.servicio.CorrespondenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/** CU-05 Gestionar correspondencia institucional · CU-06 Gestionar seguimiento de correspondencia. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-05/CU-06 Correspondencia")
public class CorrespondenciaController {

    private static final String CONSULTA = "hasAnyAuthority('GESTIONAR_CORRESPONDENCIA', 'CONSULTAR_CORRESPONDENCIA', 'SEGUIMIENTO_CORRESPONDENCIA')";

    private final CorrespondenciaService servicio;

    public CorrespondenciaController(CorrespondenciaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/correspondencia")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "Listar correspondencia con filtros")
    public Pagina<CorrespondenciaDto> listar(@RequestParam(required = false) String texto,
                                             @RequestParam(required = false) Correspondencia.Tipo tipo,
                                             @RequestParam(required = false) Correspondencia.Estado estado,
                                             @RequestParam(required = false) Integer idDestinatario,
                                             @RequestParam(required = false) Boolean autorizado,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                             @RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, tipo, estado, idDestinatario, autorizado, desde, hasta, pagina, tamano);
    }

    @GetMapping("/correspondencia/{id}")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "Detalle con adjuntos y seguimiento")
    public CorrespondenciaDetalleDto obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    @PostMapping("/correspondencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Registrar oficio (el código único lo genera el sistema)")
    public CorrespondenciaDto crear(@Valid @RequestBody CrearCorrespondenciaRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/correspondencia/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Actualizar oficio")
    public CorrespondenciaDto actualizar(@PathVariable Integer id, @Valid @RequestBody ActualizarCorrespondenciaRequest req) {
        return servicio.actualizar(id, req);
    }

    @PatchMapping("/correspondencia/{id}/autorizacion")
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Marcar o quitar la autorización (solo los autorizados se consolidan en el RPA)")
    public CorrespondenciaDto autorizar(@PathVariable Integer id, @Valid @RequestBody AutorizarRequest req) {
        return servicio.autorizar(id, req.autorizado());
    }

    @PostMapping(value = "/correspondencia/{id}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Adjuntar archivo al oficio (PDF/JPG/PNG/DOCX, máx. 10 MB)")
    public ArchivoDto adjuntar(@PathVariable Integer id, @RequestPart("archivo") MultipartFile archivo,
                               @RequestParam(required = false) String descripcion) {
        return servicio.adjuntar(id, archivo, descripcion);
    }

    @PostMapping("/correspondencia/{id}/seguimiento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SEGUIMIENTO_CORRESPONDENCIA')")
    @Operation(summary = "CU-06 Registrar seguimiento y cambio de estado del oficio")
    public SeguimientoDto seguimiento(@PathVariable Integer id, @Valid @RequestBody SeguimientoRequest req) {
        return servicio.registrarSeguimiento(id, req);
    }

    @GetMapping("/destinatarios")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "Catálogo de destinatarios / dependencias")
    public List<DestinatarioDto> destinatarios() {
        return servicio.destinatarios();
    }

    @PostMapping("/destinatarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Registrar destinatario")
    public DestinatarioDto crearDestinatario(@Valid @RequestBody DestinatarioRequest req) {
        return servicio.guardarDestinatario(null, req);
    }

    @PutMapping("/destinatarios/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_CORRESPONDENCIA')")
    @Operation(summary = "Actualizar destinatario")
    public DestinatarioDto actualizarDestinatario(@PathVariable Integer id, @Valid @RequestBody DestinatarioRequest req) {
        return servicio.guardarDestinatario(id, req);
    }
}
