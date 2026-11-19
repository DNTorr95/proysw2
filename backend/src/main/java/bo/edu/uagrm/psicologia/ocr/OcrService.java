package bo.edu.uagrm.psicologia.ocr;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.repositorio.ArchivoAdjuntoRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** CU-21 Procesar documentos mediante OCR · CU-22 Revisar y validar el texto extraído. */
@Service
public class OcrService {

    static final Set<String> TIPOS_OCR = Set.of("application/pdf", "image/jpeg", "image/png");

    private final ArchivoAdjuntoRepository archivos;
    private final ExtraccionOcrRepository extracciones;
    private final ClienteServiciosIa ia;
    private final AuditoriaService auditoria;

    public OcrService(ArchivoAdjuntoRepository archivos, ExtraccionOcrRepository extracciones, ClienteServiciosIa ia,
                      AuditoriaService auditoria) {
        this.archivos = archivos;
        this.extracciones = extracciones;
        this.ia = ia;
        this.auditoria = auditoria;
    }

    public record DocumentoOcrDto(ArchivoDto archivo, String estadoOcr, BigDecimal confianza, boolean validado) {
    }

    public record ExtraccionDto(Integer id, ArchivoDto archivo, String texto, BigDecimal confianza, boolean validado,
                                Integer idUsuarioValidacion, LocalDateTime fechaValidacion) {
    }

    /** Documentos procesables (PDF/JPG/PNG) con su estado OCR: PENDIENTE, EXTRAIDO o VALIDADO. */
    @Transactional(readOnly = true)
    public Pagina<DocumentoOcrDto> listar(String texto, String estado, int pagina, int tamano) {
        Specification<ArchivoAdjunto> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(root.get("tipoMime").in(TIPOS_OCR));
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("nombreArchivo")), patron), cb.like(cb.lower(cb.coalesce(root.get("descripcion"), "")), patron)));
            }
            if (estado != null && !estado.isBlank()) {
                Subquery<Integer> sq = q.subquery(Integer.class);
                Root<ExtraccionOcr> e = sq.from(ExtraccionOcr.class);
                sq.select(e.get("idArchivo"));
                switch (estado) {
                    case "PENDIENTE" -> p.add(cb.not(root.get("id").in(sq)));
                    case "EXTRAIDO" -> { sq.where(cb.isFalse(e.get("validado"))); p.add(root.get("id").in(sq)); }
                    case "VALIDADO" -> { sq.where(cb.isTrue(e.get("validado"))); p.add(root.get("id").in(sq)); }
                    default -> throw new NegocioException("Estado OCR inválido (PENDIENTE, EXTRAIDO o VALIDADO)");
                }
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = archivos.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaCarga", "id")));
        Map<Integer, ExtraccionOcr> ext = extracciones.findByIdArchivoIn(page.getContent().stream().map(ArchivoAdjunto::getId).toList())
                .stream().collect(Collectors.toMap(ExtraccionOcr::getIdArchivo, Function.identity()));
        return Pagina.de(page, a -> {
            ExtraccionOcr e = ext.get(a.getId());
            return new DocumentoOcrDto(ArchivoDto.de(a), e == null ? "PENDIENTE" : e.isValidado() ? "VALIDADO" : "EXTRAIDO",
                    e == null ? null : e.getConfianza(), e != null && e.isValidado());
        });
    }

    @Transactional(readOnly = true)
    public ExtraccionDto obtener(Integer idArchivo) {
        ArchivoAdjunto a = archivo(idArchivo);
        ExtraccionOcr e = extracciones.findByIdArchivo(idArchivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "El documento aún no fue procesado con OCR"));
        return aDto(e, a);
    }

    /**
     * Envía la ruta del archivo al servicio OCR y guarda el texto (validado=false). Un texto ya validado
     * solo se reemplaza si se indica explícitamente (reprocesar), porque se perdería la corrección humana.
     */
    @Transactional
    public ExtraccionDto procesar(Integer idArchivo, boolean reprocesar) {
        ArchivoAdjunto a = archivo(idArchivo);
        if (!TIPOS_OCR.contains(a.getTipoMime())) {
            throw new NegocioException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se procesan imágenes (JPG, PNG) y PDF");
        }
        ExtraccionOcr e = extracciones.findByIdArchivo(idArchivo).orElse(null);
        if (e != null && e.isValidado() && !reprocesar) {
            throw new NegocioException(HttpStatus.CONFLICT, "El texto ya fue validado; confirme el reprocesamiento para reemplazarlo");
        }
        JsonNode r = ia.post("/interno/ocr", Map.of("ruta", a.getRuta()));
        String texto = r.path("texto").asText("");
        BigDecimal confianza = BigDecimal.valueOf(r.path("confianza").asDouble(0)).setScale(2, RoundingMode.HALF_UP);
        if (e == null) e = new ExtraccionOcr(idArchivo);
        e.registrarExtraccion(texto.isBlank() ? "(sin texto reconocible)" : texto, confianza);
        extracciones.save(e);
        auditoria.registrar("PROCESAR_OCR", "ARCHIVO_ADJUNTO", idArchivo,
                a.getNombreArchivo() + " · confianza " + confianza + " · " + texto.length() + " caracteres");
        return aDto(e, a);
    }

    @Transactional
    public ExtraccionDto validar(Integer idArchivo, String textoCorregido) {
        ArchivoAdjunto a = archivo(idArchivo);
        ExtraccionOcr e = extracciones.findByIdArchivo(idArchivo)
                .orElseThrow(() -> new NegocioException(HttpStatus.CONFLICT, "Primero procese el documento con OCR"));
        boolean corregido = !textoCorregido.trim().equals(e.getTextoExtraido());
        e.validar(textoCorregido.trim(), UsuarioActual.id());
        auditoria.registrar("VALIDAR_OCR", "ARCHIVO_ADJUNTO", idArchivo,
                a.getNombreArchivo() + (corregido ? " · con correcciones" : " · sin cambios"));
        return aDto(e, a);
    }

    private ArchivoAdjunto archivo(Integer id) {
        return archivos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
    }

    private static ExtraccionDto aDto(ExtraccionOcr e, ArchivoAdjunto a) {
        return new ExtraccionDto(e.getId(), ArchivoDto.de(a), e.getTextoExtraido(), e.getConfianza(), e.isValidado(),
                e.getIdUsuarioValidacion(), e.getFechaValidacion());
    }
}
