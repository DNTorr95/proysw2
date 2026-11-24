package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.institucional.modelo.TrabajoGrado;
import bo.edu.uagrm.psicologia.ocr.ExtraccionOcr;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CU-23 Búsqueda avanzada (consultas con filtros sobre la base) y CU-24 Búsqueda semántica
 * (servicios-ia devuelve ids y relevancia; el backend carga las entidades y FILTRA POR PERMISOS).
 *
 * La semántica usa los vectores guardados en PostgreSQL con pgvector ({@link IndiceVectorial}); si servicios-ia
 * trabaja con TF-IDF (sin vectores de dimensión fija) se usa su índice en memoria.
 */
@Service
public class BusquedaService {

    private static final Logger log = LoggerFactory.getLogger(BusquedaService.class);
    public static final Set<String> TIPOS = Set.of("CORRESPONDENCIA", "TRAMITE", "DOCUMENTO", "TRABAJO_GRADO");
    private static final int MAXIMO_POR_TIPO = 25;

    private final EntityManager em;
    private final ClienteServiciosIa ia;
    private final AuditoriaService auditoria;
    private final IndiceVectorial vectorial;

    public BusquedaService(EntityManager em, ClienteServiciosIa ia, AuditoriaService auditoria, IndiceVectorial vectorial) {
        this.em = em;
        this.ia = ia;
        this.auditoria = auditoria;
        this.vectorial = vectorial;
    }

    public record ResultadoDto(String tipo, Integer id, String titulo, String detalle, LocalDate fecha, String ruta, Double relevancia) {
    }

    public record RespuestaBusqueda(List<ResultadoDto> resultados, List<String> tiposConsultados, String motor) {
    }

    // ------------------------------------------------------------------ permisos por tipo

    static boolean puedeVer(UsuarioAutenticado u, String tipo) {
        return switch (tipo) {
            case "CORRESPONDENCIA" -> u.tienePermiso("CONSULTAR_CORRESPONDENCIA") || u.tienePermiso("GESTIONAR_CORRESPONDENCIA");
            case "TRAMITE" -> u.tienePermiso("CONSULTAR_TRAMITES") || u.tienePermiso("REGISTRAR_TRAMITE");
            case "DOCUMENTO" -> u.tienePermiso("GESTIONAR_DOCUMENTOS");
            case "TRABAJO_GRADO" -> u.tienePermiso("CONSULTAR_INSTITUCIONAL") || u.tienePermiso("GESTIONAR_TRABAJOS_GRADO");
            default -> false;
        };
    }

    private static List<String> tiposPermitidos(UsuarioAutenticado u, Set<String> pedidos) {
        return TIPOS.stream().filter(t -> pedidos == null || pedidos.isEmpty() || pedidos.contains(t))
                .filter(t -> puedeVer(u, t)).sorted().toList();
    }

    // ------------------------------------------------------------------ CU-23 Avanzada

    @Transactional(readOnly = true)
    public RespuestaBusqueda avanzada(String texto, Set<String> tipos, LocalDate desde, LocalDate hasta) {
        if (texto == null || texto.trim().length() < 2) throw new NegocioException("Escriba al menos 2 caracteres");
        UsuarioAutenticado u = UsuarioActual.obtener();
        List<String> permitidos = tiposPermitidos(u, tipos);
        String patron = "%" + texto.trim().toLowerCase() + "%";
        LocalDate d = desde == null ? LocalDate.of(2000, 1, 1) : desde;
        LocalDate h = hasta == null ? LocalDate.of(2100, 1, 1) : hasta;
        List<ResultadoDto> res = new ArrayList<>();
        if (permitidos.contains("CORRESPONDENCIA")) {
            consulta("""
                    select c from Correspondencia c join fetch c.destinatario dd
                    where (lower(c.codigo) like :p or lower(c.asunto) like :p or lower(coalesce(c.descripcion, '')) like :p
                           or lower(dd.unidad) like :p) and c.fechaDocumento between :d and :h order by c.fechaDocumento desc
                    """, Correspondencia.class, patron, d, h).forEach(c -> res.add(deCorrespondencia(c, null)));
        }
        if (permitidos.contains("TRAMITE")) {
            consulta("""
                    select t from Tramite t join fetch t.estudiante e join fetch e.persona p join fetch t.tipoTramite tt
                    where (lower(t.numero) like :p or lower(coalesce(t.observaciones, '')) like :p or e.registro like :p
                           or lower(concat(p.nombres, ' ', p.apellidos)) like :p or lower(tt.nombre) like :p)
                      and cast(t.fechaRecepcion as LocalDate) between :d and :h order by t.fechaRecepcion desc
                    """, Tramite.class, patron, d, h).forEach(t -> res.add(deTramite(t, null)));
        }
        if (permitidos.contains("DOCUMENTO")) {
            List<ArchivoAdjunto> docs = consulta("""
                    select a from ArchivoAdjunto a
                    where (lower(a.nombreArchivo) like :p or lower(coalesce(a.descripcion, '')) like :p
                           or a.id in (select e.idArchivo from ExtraccionOcr e where lower(e.textoExtraido) like :p))
                      and cast(a.fechaCarga as LocalDate) between :d and :h order by a.fechaCarga desc
                    """, ArchivoAdjunto.class, patron, d, h);
            Map<Integer, ExtraccionOcr> ocr = ocrDe(docs.stream().map(ArchivoAdjunto::getId).toList());
            docs.forEach(a -> res.add(deDocumento(a, ocr.get(a.getId()), texto.trim(), null)));
        }
        if (permitidos.contains("TRABAJO_GRADO")) {
            consulta("""
                    select t from TrabajoGrado t join fetch t.estudiante e join fetch e.persona p
                    where (lower(t.titulo) like :p or lower(coalesce(t.palabrasClave, '')) like :p
                           or lower(coalesce(t.tutor, '')) like :p or lower(concat(p.nombres, ' ', p.apellidos)) like :p)
                      and t.gestion between :ad and :ah order by t.gestion desc
                    """, TrabajoGrado.class, patron, d, h).forEach(t -> res.add(deTrabajo(t, null)));
        }
        res.sort(Comparator.comparing(ResultadoDto::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return new RespuestaBusqueda(res, permitidos, "SQL");
    }

    private <T> List<T> consulta(String jpql, Class<T> tipo, String patron, LocalDate d, LocalDate h) {
        TypedQuery<T> q = em.createQuery(jpql, tipo).setParameter("p", patron).setMaxResults(MAXIMO_POR_TIPO);
        if (jpql.contains(":ad")) {
            q.setParameter("ad", d.getYear()).setParameter("ah", h.getYear());
        } else {
            q.setParameter("d", d).setParameter("h", h);
        }
        return q.getResultList();
    }

    // ------------------------------------------------------------------ CU-24 Semántica

    /** Corpus que se indexa: textos de las cuatro fuentes con id tipado (el filtrado por permisos se hace al responder). */
    @Transactional(readOnly = true)
    public List<Map<String, String>> corpus() {
        List<Map<String, String>> docs = new ArrayList<>();
        em.createQuery("select c from Correspondencia c join fetch c.destinatario", Correspondencia.class).getResultList()
                .forEach(c -> docs.add(Map.of("id", "CORR-" + c.getId(), "texto",
                        c.getAsunto() + ". " + nz(c.getDescripcion()) + ". " + c.getDestinatario().getUnidad())));
        em.createQuery("select t from Tramite t join fetch t.tipoTramite", Tramite.class).getResultList()
                .forEach(t -> docs.add(Map.of("id", "TRAM-" + t.getId(), "texto", t.getTipoTramite().getNombre() + ". " + nz(t.getObservaciones()))));
        Map<Integer, ExtraccionOcr> ocr = em.createQuery("select e from ExtraccionOcr e", ExtraccionOcr.class).getResultList()
                .stream().collect(Collectors.toMap(ExtraccionOcr::getIdArchivo, Function.identity()));
        em.createQuery("select a from ArchivoAdjunto a", ArchivoAdjunto.class).getResultList()
                .forEach(a -> docs.add(Map.of("id", "DOC-" + a.getId(), "texto", a.getNombreArchivo() + ". " + nz(a.getDescripcion()) + ". "
                        + (ocr.containsKey(a.getId()) ? ocr.get(a.getId()).getTextoExtraido() : ""))));
        em.createQuery("select t from TrabajoGrado t", TrabajoGrado.class).getResultList()
                .forEach(t -> docs.add(Map.of("id", "TRAB-" + t.getId(), "texto", t.getTitulo() + ". " + nz(t.getPalabrasClave()))));
        return docs;
    }

    public int reindexar() {
        List<Map<String, String>> docs = corpus();
        IndiceVectorial.Sincronizacion s = vectorial.sincronizar(docs);
        if (s != null) return s.indexados();
        JsonNode r = ia.post("/interno/busqueda/indice", Map.of("documentos", docs));
        int n = r.path("indexados").asInt();
        log.info("indice semantico regenerado documentos={} motor={}", n, r.path("motor").asText());
        return n;
    }

    /** Reindexación nocturna: recalcula los vectores de lo que cambió (o regenera el índice en memoria con TF-IDF). */
    @Scheduled(cron = "0 30 2 * * *")
    public void reindexarProgramado() {
        try {
            reindexar();
        } catch (NegocioException e) {
            log.warn("No se pudo reindexar: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RespuestaBusqueda semantica(String consulta, Set<String> tipos) {
        if (consulta == null || consulta.trim().length() < 3) throw new NegocioException("Escriba al menos 3 caracteres");
        UsuarioAutenticado u = UsuarioActual.obtener();
        List<String> permitidos = tiposPermitidos(u, tipos);
        Map<String, Double> relevancia = new LinkedHashMap<>();
        String motor;
        IndiceVectorial.Coincidencias v = vectorial.buscar(consulta.trim(), 60);
        if (v != null && v.indiceVacio()) {
            reindexar();                       // primera búsqueda o cambio de modelo: se calculan los vectores
            v = vectorial.buscar(consulta.trim(), 60);
        }
        if (v != null) {
            relevancia.putAll(v.relevancia());
            motor = v.motor() + " · pgvector";
        } else {
            Map<String, Object> cuerpo = Map.of("consulta", consulta.trim(), "limite", 60);
            JsonNode r;
            try {
                r = ia.post("/interno/busqueda", cuerpo);
            } catch (ClienteServiciosIa.RespuestaIa e) {
                if (e.getCodigo() != 409) throw e;
                reindexar();                       // índice vacío (p. ej. el servicio se reinició)
                r = ia.post("/interno/busqueda", cuerpo);
            }
            r.path("resultados").forEach(n -> relevancia.put(n.path("id").asText(), n.path("relevancia").asDouble()));
            motor = r.path("motor").asText();
        }
        List<ResultadoDto> res = cargar(relevancia, permitidos, consulta.trim());
        auditoria.registrar("BUSQUEDA_SEMANTICA", "BUSQUEDA", null, "\"" + consulta.trim() + "\" · " + res.size() + " resultado(s) visibles");
        return new RespuestaBusqueda(res, permitidos, motor);
    }

    /** Carga las entidades devueltas por el servicio, descartando las que el usuario no puede ver. */
    private List<ResultadoDto> cargar(Map<String, Double> relevancia, List<String> permitidos, String consulta) {
        Map<String, List<Integer>> porTipo = new HashMap<>();
        relevancia.keySet().forEach(clave -> {
            int i = clave.indexOf('-');
            if (i > 0) porTipo.computeIfAbsent(clave.substring(0, i), k -> new ArrayList<>()).add(Integer.valueOf(clave.substring(i + 1)));
        });
        Map<String, ResultadoDto> encontrados = new HashMap<>();
        if (permitidos.contains("CORRESPONDENCIA") && porTipo.containsKey("CORR")) {
            em.createQuery("select c from Correspondencia c join fetch c.destinatario where c.id in :ids", Correspondencia.class)
                    .setParameter("ids", porTipo.get("CORR")).getResultList()
                    .forEach(c -> encontrados.put("CORR-" + c.getId(), deCorrespondencia(c, relevancia.get("CORR-" + c.getId()))));
        }
        if (permitidos.contains("TRAMITE") && porTipo.containsKey("TRAM")) {
            em.createQuery("select t from Tramite t join fetch t.estudiante e join fetch e.persona join fetch t.tipoTramite where t.id in :ids", Tramite.class)
                    .setParameter("ids", porTipo.get("TRAM")).getResultList()
                    .forEach(t -> encontrados.put("TRAM-" + t.getId(), deTramite(t, relevancia.get("TRAM-" + t.getId()))));
        }
        if (permitidos.contains("DOCUMENTO") && porTipo.containsKey("DOC")) {
            List<ArchivoAdjunto> docs = em.createQuery("select a from ArchivoAdjunto a where a.id in :ids", ArchivoAdjunto.class)
                    .setParameter("ids", porTipo.get("DOC")).getResultList();
            Map<Integer, ExtraccionOcr> ocr = ocrDe(docs.stream().map(ArchivoAdjunto::getId).toList());
            docs.forEach(a -> encontrados.put("DOC-" + a.getId(), deDocumento(a, ocr.get(a.getId()), consulta, relevancia.get("DOC-" + a.getId()))));
        }
        if (permitidos.contains("TRABAJO_GRADO") && porTipo.containsKey("TRAB")) {
            em.createQuery("select t from TrabajoGrado t join fetch t.estudiante e join fetch e.persona where t.id in :ids", TrabajoGrado.class)
                    .setParameter("ids", porTipo.get("TRAB")).getResultList()
                    .forEach(t -> encontrados.put("TRAB-" + t.getId(), deTrabajo(t, relevancia.get("TRAB-" + t.getId()))));
        }
        // Se conserva el orden por relevancia del servicio
        return relevancia.keySet().stream().map(encontrados::get).filter(java.util.Objects::nonNull).limit(30).toList();
    }

    // ------------------------------------------------------------------ mapeo

    private Map<Integer, ExtraccionOcr> ocrDe(List<Integer> ids) {
        if (ids.isEmpty()) return Map.of();
        return em.createQuery("select e from ExtraccionOcr e where e.idArchivo in :ids", ExtraccionOcr.class).setParameter("ids", ids)
                .getResultList().stream().collect(Collectors.toMap(ExtraccionOcr::getIdArchivo, Function.identity()));
    }

    private static ResultadoDto deCorrespondencia(Correspondencia c, Double r) {
        return new ResultadoDto("CORRESPONDENCIA", c.getId(), c.getCodigo() + " · " + c.getAsunto(),
                (c.getTipo() == Correspondencia.Tipo.RECIBIDA ? "Recibida de " : "Emitida a ") + c.getDestinatario().getUnidad() + " · " + c.getEstado(),
                c.getFechaDocumento(), "/correspondencia?id=" + c.getId(), r);
    }

    private static ResultadoDto deTramite(Tramite t, Double r) {
        return new ResultadoDto("TRAMITE", t.getId(), t.getNumero() + " · " + t.getTipoTramite().getNombre(),
                t.getEstudiante().nombreCompleto() + " · " + t.getEstadoActual(), t.getFechaRecepcion().toLocalDate(), "/tramites/" + t.getId(), r);
    }

    private static ResultadoDto deDocumento(ArchivoAdjunto a, ExtraccionOcr ocr, String consulta, Double r) {
        String detalle = nz(a.getDescripcion());
        if (ocr != null) {
            detalle = (ocr.isValidado() ? "OCR validado: " : "OCR sin validar: ") + fragmento(ocr.getTextoExtraido(), consulta);
        }
        return new ResultadoDto("DOCUMENTO", a.getId(), a.getNombreArchivo(), detalle, a.getFechaCarga().toLocalDate(),
                "/documentos?id=" + a.getId(), r);
    }

    private static ResultadoDto deTrabajo(TrabajoGrado t, Double r) {
        return new ResultadoDto("TRABAJO_GRADO", t.getId(), t.getTitulo(), t.getEstudiante().nombreCompleto() + " · gestión " + t.getGestion()
                + (t.getPalabrasClave() != null ? " · " + t.getPalabrasClave() : ""), LocalDate.of(t.getGestion(), 1, 1), "/institucional", r);
    }

    /** Fragmento del texto alrededor de la primera coincidencia (para mostrar el contexto OCR). */
    static String fragmento(String texto, String consulta) {
        if (texto == null) return "";
        String t = texto.replaceAll("\\s+", " ");
        int i = consulta == null ? -1 : t.toLowerCase().indexOf(consulta.toLowerCase());
        int desde = Math.max(0, i < 0 ? 0 : i - 60);
        String f = t.substring(desde, Math.min(t.length(), desde + 180));
        return (desde > 0 ? "…" : "") + f + (desde + 180 < t.length() ? "…" : "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
