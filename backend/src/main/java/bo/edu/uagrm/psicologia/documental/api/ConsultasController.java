package bo.edu.uagrm.psicologia.documental.api;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CorrespondenciaDto;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.servicio.CorrespondenciaService;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TramiteDto;
import bo.edu.uagrm.psicologia.tramites.servicio.TramiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CU-33 Consultar registros mediante filtros convencionales: consulta de solo lectura sobre correspondencia
 * y trámites con filtros combinables, para quien tiene CONSULTAR_FILTROS.
 */
@RestController
@RequestMapping("/api/consultas")
@Tag(name = "CU-33 Consultas con filtros")
@PreAuthorize("hasAuthority('CONSULTAR_FILTROS')")
public class ConsultasController {

    private final CorrespondenciaService correspondencia;
    private final TramiteService tramites;

    public ConsultasController(CorrespondenciaService correspondencia, TramiteService tramites) {
        this.correspondencia = correspondencia;
        this.tramites = tramites;
    }

    @GetMapping("/correspondencia")
    @Operation(summary = "Correspondencia por texto, tipo, estado, dependencia, autorización y fechas")
    public Pagina<CorrespondenciaDto> correspondencia(@RequestParam(required = false) String texto,
                                                     @RequestParam(required = false) Correspondencia.Tipo tipo,
                                                     @RequestParam(required = false) Correspondencia.Estado estado,
                                                     @RequestParam(required = false) Integer idDestinatario,
                                                     @RequestParam(required = false) Boolean autorizado,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                                     @RequestParam(defaultValue = "0") int pagina,
                                                     @RequestParam(defaultValue = "20") int tamano) {
        return correspondencia.listar(texto, tipo, estado, idDestinatario, autorizado, desde, hasta, pagina, tamano);
    }

    @GetMapping("/tramites")
    @Operation(summary = "Trámites por texto, estado, tipo, prioridad, fechas y vencimiento")
    public Pagina<TramiteDto> tramites(@RequestParam(required = false) String texto,
                                       @RequestParam(required = false) String estado,
                                       @RequestParam(required = false) Integer idTipoTramite,
                                       @RequestParam(required = false) String prioridad,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(required = false) Boolean vencidos,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamano) {
        return tramites.listar(texto, estado, idTipoTramite, prioridad, null, desde, hasta, vencidos, pagina, tamano);
    }
}
