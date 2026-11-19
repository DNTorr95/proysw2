package bo.edu.uagrm.psicologia.ocr;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CU-21 Procesar documentos mediante OCR · CU-22 Revisar y validar texto extraído. */
@RestController
@RequestMapping("/api/ocr/documentos")
@Tag(name = "CU-21/CU-22 OCR")
public class OcrController {

    private final OcrService servicio;

    public OcrController(OcrService servicio) {
        this.servicio = servicio;
    }

    public record ValidarRequest(@NotBlank(message = "El texto validado es obligatorio") @Size(max = 200000) String texto) {
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PROCESAR_OCR', 'VALIDAR_OCR')")
    @Operation(summary = "Documentos procesables (PDF/JPG/PNG) con su estado OCR")
    public Pagina<OcrService.DocumentoOcrDto> listar(@RequestParam(required = false) String texto,
                                                     @RequestParam(required = false) String estado,
                                                     @RequestParam(defaultValue = "0") int pagina,
                                                     @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(texto, estado, pagina, tamano);
    }

    @GetMapping("/{idArchivo}")
    @PreAuthorize("hasAnyAuthority('PROCESAR_OCR', 'VALIDAR_OCR')")
    public OcrService.ExtraccionDto obtener(@PathVariable Integer idArchivo) {
        return servicio.obtener(idArchivo);
    }

    @PostMapping("/{idArchivo}/procesamiento")
    @PreAuthorize("hasAuthority('PROCESAR_OCR')")
    @Operation(summary = "CU-21 Procesar con OCR (Tesseract, español); el texto queda pendiente de validación")
    public OcrService.ExtraccionDto procesar(@PathVariable Integer idArchivo, @RequestParam(defaultValue = "false") boolean reprocesar) {
        return servicio.procesar(idArchivo, reprocesar);
    }

    @PutMapping("/{idArchivo}/validacion")
    @PreAuthorize("hasAuthority('VALIDAR_OCR')")
    @Operation(summary = "CU-22 Validar (y corregir) el texto extraído")
    public OcrService.ExtraccionDto validar(@PathVariable Integer idArchivo, @Valid @RequestBody ValidarRequest req) {
        return servicio.validar(idArchivo, req.texto());
    }
}
