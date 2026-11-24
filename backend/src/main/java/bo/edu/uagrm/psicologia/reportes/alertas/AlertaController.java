package bo.edu.uagrm.psicologia.reportes.alertas;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** CU-28 Alertas administrativas y predictivas · CU-29 Análisis predictivo · CU-30 Atención de alertas. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-28/CU-30 Alertas y predicción")
public class AlertaController {

    private static final String CONSULTA = "hasAnyAuthority('CONSULTAR_ALERTAS', 'ATENDER_ALERTAS')";

    private final AlertaService alertas;
    private final PrediccionService prediccion;

    public AlertaController(AlertaService alertas, PrediccionService prediccion) {
        this.alertas = alertas;
        this.prediccion = prediccion;
    }

    public record AtencionRequest(
            @NotBlank @Pattern(regexp = "EN_ATENCION|ATENDIDA", message = "Estado inválido") String estado,
            @NotBlank(message = "El comentario es obligatorio") @Size(max = 300) String comentario) {
    }

    @GetMapping("/alertas")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-28 Alertas (estado ABIERTAS = abierta o en atención)")
    public Pagina<AlertaService.AlertaDto> listar(@RequestParam(required = false) String estado,
                                                 @RequestParam(required = false) String origen,
                                                 @RequestParam(required = false) String nivel,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "20") int tamano) {
        return alertas.listar(estado, origen, nivel, pagina, tamano);
    }

    @GetMapping("/alertas/resumen")
    @PreAuthorize(CONSULTA)
    public AlertaService.ResumenAlertas resumen() {
        return alertas.resumen();
    }

    @PostMapping("/alertas/generacion")
    @PreAuthorize("hasAnyAuthority('ATENDER_ALERTAS', 'ANALISIS_PREDICTIVO')")
    @Operation(summary = "Ejecutar ahora la generación de alertas por plazo (normalmente es un job diario)")
    public AlertaService.GeneracionDto generar() {
        return alertas.generarPorPlazo(LocalDate.now());
    }

    @PatchMapping("/alertas/{id}/atencion")
    @PreAuthorize("hasAuthority('ATENDER_ALERTAS')")
    @Operation(summary = "CU-30 Registrar la atención de una alerta (con comentario)")
    public AlertaService.AlertaDto atender(@PathVariable Integer id, @Valid @RequestBody AtencionRequest req) {
        return alertas.atender(id, req.estado(), req.comentario());
    }

    @PostMapping("/prediccion/analisis")
    @PreAuthorize("hasAuthority('ANALISIS_PREDICTIVO')")
    @Operation(summary = "CU-29 Estimar el riesgo de demora de los trámites en curso con el modelo vigente "
            + "(reentrenar=true entrena una versión nueva con el histórico)")
    public PrediccionService.AnalisisDto analizar(@RequestParam(defaultValue = "false") boolean reentrenar) {
        return prediccion.analizar(reentrenar);
    }

    @GetMapping("/prediccion/modelos")
    @PreAuthorize("hasAuthority('ANALISIS_PREDICTIVO')")
    @Operation(summary = "CU-29 Versiones del modelo predictivo (fecha, ejemplos, exactitud, SHA-256 y vigente)")
    public List<PrediccionService.ModeloDto> modelos() {
        return prediccion.modelos();
    }
}
