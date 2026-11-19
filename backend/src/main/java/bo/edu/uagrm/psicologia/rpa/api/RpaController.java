package bo.edu.uagrm.psicologia.rpa.api;

import bo.edu.uagrm.psicologia.comun.web.Descargas;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.CerrarIncidenciaRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.DevolverRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EditarBorradorRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EjecucionDetalleDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EjecucionDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.IncidenciaDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.InformeDetalleDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.InformeDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.IniciarEjecucionRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.MetricasDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.PrevisualizacionDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.ProcesoDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.ReejecutarRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.RevisorDto;
import bo.edu.uagrm.psicologia.rpa.servicio.RevisionRpaService;
import bo.edu.uagrm.psicologia.rpa.servicio.RpaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/** CU-31 a CU-37: automatización RPA de la consolidación de correspondencia. */
@RestController
@RequestMapping("/api/rpa")
@Tag(name = "CU-31/CU-37 RPA")
public class RpaController {

    private static final String CONSULTA = "hasAnyAuthority('CONSULTAR_RPA', 'REVISAR_RPA', 'AUTORIZAR_ENVIO_RPA', 'GESTIONAR_REEJECUCION_RPA')";
    private static final String REVISION = "hasAnyAuthority('REVISAR_RPA', 'AUTORIZAR_ENVIO_RPA')";

    private final RpaService rpa;
    private final RevisionRpaService revision;

    public RpaController(RpaService rpa, RevisionRpaService revision) {
        this.rpa = rpa;
        this.revision = revision;
    }

    // ---------- CU-31 ----------

    @GetMapping("/procesos")
    @PreAuthorize("hasAnyAuthority('EJECUTAR_RPA', 'CONSULTAR_RPA')")
    @Operation(summary = "Procedimientos RPA disponibles")
    public List<ProcesoDto> procesos() {
        return rpa.procesos();
    }

    @GetMapping("/revisores")
    @PreAuthorize("hasAnyAuthority('EJECUTAR_RPA', 'GESTIONAR_REEJECUCION_RPA')")
    @Operation(summary = "Usuarios que pueden revisar resultados RPA")
    public List<RevisorDto> revisores() {
        return rpa.revisores();
    }

    @GetMapping("/previsualizacion")
    @PreAuthorize("hasAuthority('EJECUTAR_RPA')")
    @Operation(summary = "Oficios autorizados del periodo e informe vigente (antes de ejecutar)")
    public PrevisualizacionDto previsualizar(@RequestParam Integer idProceso,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                             @RequestParam(defaultValue = "TODAS") String tipo) {
        return rpa.previsualizar(idProceso, desde, hasta, tipo);
    }

    @PostMapping("/ejecuciones")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('EJECUTAR_RPA')")
    @Operation(summary = "CU-31 Iniciar la consolidación (asíncrona). Consultar el progreso en GET /ejecuciones/{id}")
    public EjecucionDto iniciar(@Valid @RequestBody IniciarEjecucionRequest req) {
        return rpa.iniciar(req);
    }

    // ---------- CU-36 ----------

    @GetMapping("/ejecuciones")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-36 Historial de ejecuciones con métricas por ejecución")
    public Pagina<EjecucionDto> listar(@RequestParam(required = false) String estado,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamano) {
        return rpa.listar(estado, desde, hasta, pagina, tamano);
    }

    @GetMapping("/ejecuciones/{id}")
    @PreAuthorize("hasAnyAuthority('EJECUTAR_RPA', 'CONSULTAR_RPA', 'REVISAR_RPA', 'AUTORIZAR_ENVIO_RPA', 'GESTIONAR_REEJECUCION_RPA')")
    @Operation(summary = "CU-36 Detalle: pasos (evidencias), incidencias y posibilidad de reejecución")
    public EjecucionDetalleDto obtener(@PathVariable Integer id) {
        return rpa.obtener(id);
    }

    @GetMapping("/metricas")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-36 Métricas: duración, registros, tasa de éxito, errores y comparación con el tiempo manual")
    public MetricasDto metricas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return rpa.metricas(desde, hasta);
    }

    // ---------- CU-37 ----------

    @PostMapping("/ejecuciones/{id}/reejecucion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('GESTIONAR_REEJECUCION_RPA')")
    @Operation(summary = "CU-37 Reejecutar una ejecución FALLIDA o DEVUELTA (no enviada); referencia a la anterior")
    public EjecucionDto reejecutar(@PathVariable Integer id, @Valid @RequestBody(required = false) ReejecutarRequest req) {
        return rpa.reejecutar(id, req);
    }

    @PatchMapping("/incidencias/{id}/cierre")
    @PreAuthorize("hasAuthority('GESTIONAR_REEJECUCION_RPA')")
    @Operation(summary = "CU-37 Cerrar una incidencia con comentario")
    public IncidenciaDto cerrarIncidencia(@PathVariable Integer id, @Valid @RequestBody CerrarIncidenciaRequest req) {
        return rpa.cerrarIncidencia(id, req.comentario());
    }

    // ---------- CU-32 ----------

    @GetMapping("/informes")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-32 Informes generados (filtro por estado de revisión)")
    public Pagina<InformeDto> informes(@RequestParam(required = false) String estado,
                                       @RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamano) {
        return revision.listar(estado, pagina, tamano);
    }

    @GetMapping("/informes/{id}")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-32 Detalle del informe, borrador de correo e incidencias")
    public InformeDetalleDto informe(@PathVariable Integer id) {
        return revision.obtener(id);
    }

    @GetMapping("/informes/{id}/pdf")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-32 Vista previa del PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer id) {
        return Descargas.archivo("informe-" + id + ".pdf", "application/pdf", revision.pdf(id), true);
    }

    @PutMapping("/informes/{id}/borrador")
    @PreAuthorize(REVISION)
    @Operation(summary = "CU-32 Editar el borrador de correo antes de aprobar")
    public InformeDetalleDto editarBorrador(@PathVariable Integer id, @Valid @RequestBody EditarBorradorRequest req) {
        return revision.editarBorrador(id, req);
    }

    @PostMapping("/informes/{id}/aprobacion")
    @PreAuthorize("hasAuthority('AUTORIZAR_ENVIO_RPA')")
    @Operation(summary = "CU-32 Aprobar y autorizar el envío (el backend envía por SMTP/TLS)")
    public InformeDetalleDto aprobar(@PathVariable Integer id) {
        return revision.aprobarYAutorizar(id);
    }

    @PostMapping("/informes/{id}/devolucion")
    @PreAuthorize(REVISION)
    @Operation(summary = "CU-32 Devolver el informe con observaciones (queda DEVUELTO; puede reejecutarse)")
    public InformeDetalleDto devolver(@PathVariable Integer id, @Valid @RequestBody DevolverRequest req) {
        return revision.devolver(id, req.observaciones());
    }

    @PostMapping("/informes/{id}/reintento-envio")
    @PreAuthorize("hasAuthority('AUTORIZAR_ENVIO_RPA')")
    @Operation(summary = "CU-32 Reintentar el envío de un correo autorizado que falló")
    public InformeDetalleDto reintentar(@PathVariable Integer id) {
        return revision.reintentarEnvio(id);
    }
}
