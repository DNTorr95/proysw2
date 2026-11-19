package bo.edu.uagrm.psicologia.tramites.api;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DictamenDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DictamenRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DocumentoTramiteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.EstudianteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.EventoRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.RegistrarTramiteRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.RequisitoRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TipoTramiteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TramiteDetalleDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TramiteDto;
import bo.edu.uagrm.psicologia.tramites.servicio.TramiteService;
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

/** CU-09 a CU-12: trámites estudiantiles. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-09/CU-12 Trámites")
public class TramiteController {

    private static final String CONSULTA = "hasAnyAuthority('CONSULTAR_TRAMITES', 'REGISTRAR_TRAMITE', 'EJECUTAR_ACTUACION_TRAMITE', 'APROBAR_RECHAZAR_TRAMITE')";

    private final TramiteService servicio;

    public TramiteController(TramiteService servicio) {
        this.servicio = servicio;
    }

    // ---------- Catálogos ----------

    @GetMapping("/tipos-tramite")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tipos de trámite habilitados con requisitos y plazos")
    public List<TipoTramiteDto> tipos() {
        return servicio.tiposTramite();
    }

    @GetMapping("/estudiantes")
    @PreAuthorize("hasAnyAuthority('REGISTRAR_TRAMITE', 'CONSULTAR_TRAMITES', 'CONSULTAR_ACADEMICO', 'GESTIONAR_EXPEDIENTES')")
    @Operation(summary = "Buscar estudiantes por registro, CI o nombre")
    public Pagina<EstudianteDto> estudiantes(@RequestParam(required = false) String texto,
                                             @RequestParam(defaultValue = "0") int pagina,
                                             @RequestParam(defaultValue = "10") int tamano) {
        return servicio.buscarEstudiantes(texto, pagina, tamano);
    }

    // ---------- CU-12 Consulta ----------

    @GetMapping("/tramites")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-12 Consultar trámites con filtros (estado, tipo, fechas, vencidos…)")
    public Pagina<TramiteDto> listar(@RequestParam(required = false) String texto,
                                     @RequestParam(required = false) String estado,
                                     @RequestParam(required = false) Integer idTipoTramite,
                                     @RequestParam(required = false) String prioridad,
                                     @RequestParam(required = false) Integer idEstudiante,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam(required = false) Boolean vencidos,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, estado, idTipoTramite, prioridad, idEstudiante, desde, hasta, vencidos, pagina, tamano);
    }

    @GetMapping("/tramites/{id}")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-12 Seguimiento: detalle, requisitos, historial de estados y acciones disponibles")
    public TramiteDetalleDto obtener(@PathVariable Integer id) {
        return servicio.obtener(id);
    }

    // ---------- CU-09 / CU-10 ----------

    @PostMapping("/tramites")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REGISTRAR_TRAMITE')")
    @Operation(summary = "CU-09 Registrar trámite (incluye CU-10: requisitos presentados)")
    public TramiteDetalleDto registrar(@Valid @RequestBody RegistrarTramiteRequest req) {
        return servicio.registrar(req);
    }

    @PatchMapping("/tramites/{id}/documentos/{idDocumento}")
    @PreAuthorize("hasAnyAuthority('GESTIONAR_REQUISITOS', 'EJECUTAR_ACTUACION_TRAMITE')")
    @Operation(summary = "CU-10 Marcar requisito como presentado y/o verificado")
    public DocumentoTramiteDto actualizarRequisito(@PathVariable Integer id, @PathVariable Integer idDocumento,
                                                   @Valid @RequestBody RequisitoRequest req) {
        return servicio.actualizarRequisito(id, idDocumento, req);
    }

    @PostMapping(value = "/tramites/{id}/documentos/{idDocumento}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('GESTIONAR_REQUISITOS')")
    @Operation(summary = "CU-10 Adjuntar el archivo de un requisito (lo marca como presentado)")
    public DocumentoTramiteDto adjuntarRequisito(@PathVariable Integer id, @PathVariable Integer idDocumento,
                                                 @RequestPart("archivo") MultipartFile archivo) {
        return servicio.adjuntarRequisito(id, idDocumento, archivo);
    }

    @PostMapping(value = "/tramites/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_REQUISITOS')")
    @Operation(summary = "CU-10 Agregar documento adicional al trámite")
    public DocumentoTramiteDto agregarDocumento(@PathVariable Integer id, @RequestPart("archivo") MultipartFile archivo,
                                                @RequestParam(required = false) String observacion) {
        return servicio.agregarDocumento(id, archivo, observacion);
    }

    // ---------- CU-11 ----------

    @PostMapping("/tramites/{id}/eventos/{evento}")
    @PreAuthorize("hasAnyAuthority('EJECUTAR_ACTUACION_TRAMITE', 'APROBAR_RECHAZAR_TRAMITE')")
    @Operation(summary = "CU-11 Ejecutar actuación (derivar, observar, subsanar, aprobar, rechazar, archivar)")
    public TramiteDetalleDto evento(@PathVariable Integer id, @PathVariable String evento,
                                    @Valid @RequestBody(required = false) EventoRequest req) {
        return servicio.ejecutarEvento(id, evento, req == null ? null : req.justificacion());
    }

    @PutMapping("/tramites/{id}/dictamen")
    @PreAuthorize("hasAnyAuthority('EJECUTAR_ACTUACION_TRAMITE', 'APROBAR_RECHAZAR_TRAMITE')")
    @Operation(summary = "CU-11 Registrar o actualizar el dictamen técnico (trámite EN_REVISION)")
    public DictamenDto dictamen(@PathVariable Integer id, @Valid @RequestBody DictamenRequest req) {
        return servicio.registrarDictamen(id, req);
    }
}
