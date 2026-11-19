package bo.edu.uagrm.psicologia.reportes;

import bo.edu.uagrm.psicologia.comun.web.Descargas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** CU-26 Dashboard institucional · CU-27 Generar y exportar reportes. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-26/CU-27 Dashboard y reportes")
public class ReportesController {

    private final DashboardService dashboard;
    private final ReporteService reportes;

    public ReportesController(DashboardService dashboard, ReporteService reportes) {
        this.dashboard = dashboard;
        this.reportes = reportes;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VER_DASHBOARD')")
    @Operation(summary = "CU-26 Indicadores institucionales (trámites, correspondencia, RPA, alertas, POA, académico)")
    public DashboardService.Dashboard dashboard() {
        return dashboard.obtener();
    }

    @GetMapping("/reportes/{tipo}")
    @PreAuthorize("hasAuthority('GENERAR_REPORTES')")
    @Operation(summary = "CU-27 Exportar reporte (tramites, correspondencia, carga-docente, poa, rpa) en PDF o XLSX")
    public ResponseEntity<byte[]> reporte(@PathVariable String tipo,
                                          @RequestParam(defaultValue = "pdf") String formato,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) Integer idPeriodo,
                                          @RequestParam(required = false) Integer gestion) {
        ReporteService.Archivo a = reportes.generar(tipo, formato, desde, hasta, idPeriodo, gestion);
        return Descargas.archivo(a.nombre(), a.tipoMime(), a.contenido(), false);
    }
}
