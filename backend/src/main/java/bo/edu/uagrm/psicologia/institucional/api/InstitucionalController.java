package bo.edu.uagrm.psicologia.institucional.api;

import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.ActividadDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.ActividadRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.EventoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.EventoRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaResumenDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.SeguimientoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.SeguimientoRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TituladoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TituladoRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TrabajoGradoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TrabajoGradoRequest;
import bo.edu.uagrm.psicologia.institucional.servicio.PoaEventoService;
import bo.edu.uagrm.psicologia.institucional.servicio.TituladoTrabajoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

/** CU-17 a CU-20: titulados, trabajos de grado, POA y eventos. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-17/CU-20 Gestión institucional")
public class InstitucionalController {

    private static final String CONSULTA = "hasAnyAuthority('CONSULTAR_INSTITUCIONAL', 'GESTIONAR_TITULADOS', 'GESTIONAR_TRABAJOS_GRADO', 'GESTIONAR_EVENTOS')";
    private static final String CONSULTA_POA = "hasAnyAuthority('CONSULTAR_POA', 'GESTIONAR_POA')";

    private final TituladoTrabajoService titulados;
    private final PoaEventoService poa;

    public InstitucionalController(TituladoTrabajoService titulados, PoaEventoService poa) {
        this.titulados = titulados;
        this.poa = poa;
    }

    // ---------- CU-17 ----------

    @GetMapping("/titulados")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-17 Registros de titulados")
    public List<TituladoDto> titulados() {
        return titulados.titulados();
    }

    @PostMapping("/titulados")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_TITULADOS')")
    @Operation(summary = "CU-17 Registrar titulado (el estudiante pasa a TITULADO)")
    public TituladoDto registrarTitulado(@Valid @RequestBody TituladoRequest req) {
        return titulados.registrarTitulado(req);
    }

    @PutMapping("/titulados/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_TITULADOS')")
    public TituladoDto actualizarTitulado(@PathVariable Integer id, @Valid @RequestBody TituladoRequest req) {
        return titulados.actualizarTitulado(id, req);
    }

    // ---------- CU-18 ----------

    @GetMapping("/trabajos-grado")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-18 Buscar trabajos de grado por título, palabras clave, tutor o autor")
    public List<TrabajoGradoDto> trabajos(@RequestParam(required = false) String texto, @RequestParam(required = false) Integer gestion) {
        return titulados.trabajos(texto, gestion);
    }

    @PostMapping("/trabajos-grado")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_TRABAJOS_GRADO')")
    public TrabajoGradoDto crearTrabajo(@Valid @RequestBody TrabajoGradoRequest req) {
        return titulados.crearTrabajo(req);
    }

    @PutMapping("/trabajos-grado/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_TRABAJOS_GRADO')")
    public TrabajoGradoDto actualizarTrabajo(@PathVariable Integer id, @Valid @RequestBody TrabajoGradoRequest req) {
        return titulados.actualizarTrabajo(id, req);
    }

    @PostMapping(value = "/trabajos-grado/{id}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('GESTIONAR_TRABAJOS_GRADO')")
    @Operation(summary = "CU-18 Adjuntar el documento del trabajo de grado")
    public TrabajoGradoDto adjuntar(@PathVariable Integer id, @RequestPart("archivo") MultipartFile archivo) {
        return titulados.adjuntar(id, archivo);
    }

    // ---------- CU-19 ----------

    @GetMapping("/poa")
    @PreAuthorize(CONSULTA_POA)
    @Operation(summary = "CU-19 POA por gestión con avance global")
    public List<PoaResumenDto> poas() {
        return poa.poas();
    }

    @GetMapping("/poa/{gestion}")
    @PreAuthorize(CONSULTA_POA)
    @Operation(summary = "CU-19 POA de una gestión con sus actividades")
    public PoaDto poa(@PathVariable Integer gestion) {
        return poa.poa(gestion);
    }

    @PostMapping("/poa")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_POA')")
    public PoaResumenDto crearPoa(@Valid @RequestBody PoaRequest req) {
        return poa.crearPoa(req);
    }

    @PutMapping("/poa/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_POA')")
    public PoaResumenDto actualizarPoa(@PathVariable Integer id, @Valid @RequestBody PoaRequest req) {
        return poa.actualizarPoa(id, req);
    }

    @PostMapping("/poa/{id}/actividades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_POA')")
    public ActividadDto crearActividad(@PathVariable Integer id, @Valid @RequestBody ActividadRequest req) {
        return poa.crearActividad(id, req);
    }

    @PutMapping("/actividades-poa/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_POA')")
    public ActividadDto actualizarActividad(@PathVariable Integer id, @Valid @RequestBody ActividadRequest req) {
        return poa.actualizarActividad(id, req);
    }

    @GetMapping("/actividades-poa/{id}/seguimientos")
    @PreAuthorize(CONSULTA_POA)
    public List<SeguimientoDto> seguimientos(@PathVariable Integer id) {
        return poa.seguimientos(id);
    }

    @PostMapping("/actividades-poa/{id}/seguimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_POA')")
    @Operation(summary = "CU-19 Registrar avance de una actividad (recalcula estado y avance del POA)")
    public ActividadDto registrarSeguimiento(@PathVariable Integer id, @Valid @RequestBody SeguimientoRequest req) {
        return poa.registrarSeguimiento(id, req);
    }

    // ---------- CU-20 ----------

    @GetMapping("/eventos")
    @PreAuthorize("hasAnyAuthority('CONSULTAR_INSTITUCIONAL', 'GESTIONAR_EVENTOS', 'CONSULTAR_POA', 'GESTIONAR_POA')")
    @Operation(summary = "CU-20 Eventos y talleres")
    public List<EventoDto> eventos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return poa.eventos(desde, hasta);
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_EVENTOS')")
    public EventoDto crearEvento(@Valid @RequestBody EventoRequest req) {
        return poa.crearEvento(req);
    }

    @PutMapping("/eventos/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_EVENTOS')")
    public EventoDto actualizarEvento(@PathVariable Integer id, @Valid @RequestBody EventoRequest req) {
        return poa.actualizarEvento(id, req);
    }
}
