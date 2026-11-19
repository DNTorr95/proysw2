package bo.edu.uagrm.psicologia.busqueda;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** CU-23 Búsqueda avanzada · CU-24 Búsqueda semántica · CU-25 Asistente. */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-23/CU-25 Búsqueda y asistente")
public class BusquedaController {

    private final BusquedaService busqueda;
    private final AsistenteService asistente;

    public BusquedaController(BusquedaService busqueda, AsistenteService asistente) {
        this.busqueda = busqueda;
        this.asistente = asistente;
    }

    public record PreguntaRequest(@NotBlank(message = "Escriba una pregunta") @Size(min = 2, max = 500) String pregunta) {
    }

    @GetMapping("/busqueda")
    @PreAuthorize("hasAnyAuthority('BUSQUEDA_AVANZADA', 'CONSULTAR_FILTROS')")
    @Operation(summary = "CU-23 Búsqueda avanzada en correspondencia, trámites, documentos (incluye texto OCR) y trabajos de grado")
    public BusquedaService.RespuestaBusqueda avanzada(@RequestParam String texto,
                                                      @RequestParam(required = false) Set<String> tipos,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return busqueda.avanzada(texto, tipos, desde, hasta);
    }

    @GetMapping("/busqueda/semantica")
    @PreAuthorize("hasAuthority('BUSQUEDA_SEMANTICA')")
    @Operation(summary = "CU-24 Búsqueda semántica por significado (los resultados se filtran por los permisos del usuario)")
    public BusquedaService.RespuestaBusqueda semantica(@RequestParam String consulta, @RequestParam(required = false) Set<String> tipos) {
        return busqueda.semantica(consulta, tipos);
    }

    @PostMapping("/busqueda/indice")
    @PreAuthorize("hasAuthority('BUSQUEDA_SEMANTICA')")
    @Operation(summary = "CU-24 Regenerar el índice semántico")
    public Map<String, Integer> reindexar() {
        return Map.of("indexados", busqueda.reindexar());
    }

    @PostMapping("/asistente")
    @PreAuthorize("hasAuthority('USAR_ASISTENTE')")
    @Operation(summary = "CU-25 Consultar al asistente (responde solo con datos que el usuario puede ver)")
    public AsistenteService.Respuesta preguntar(@Valid @RequestBody PreguntaRequest req) {
        return asistente.responder(req.pregunta());
    }
}
