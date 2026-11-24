package bo.edu.uagrm.psicologia.reportes.alertas;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.tramites.modelo.DocumentoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.HistorialEstado;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.HistorialEstadoRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.TramiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CU-29 Análisis predictivo: estima con el modelo de servicios-ia la probabilidad de demora de los trámites
 * en curso (sin datos personales). Si supera el umbral, crea una alerta de origen PREDICCION (sin duplicar).
 *
 * El modelo se entrena con los trámites históricos cerrados y queda guardado y versionado (tabla
 * modelo_prediccion y archivo demora-v&lt;versión&gt;.joblib en servicios-ia). Cada análisis reutiliza la versión
 * vigente; se reentrena solo si se pide, si todavía no hay modelo o si servicios-ia no tiene la versión vigente.
 */
@Service
public class PrediccionService {

    static final double UMBRAL_MEDIA = 0.6;
    static final double UMBRAL_ALTA = 0.8;

    private final TramiteRepository tramites;
    private final HistorialEstadoRepository historial;
    private final AlertaService alertas;
    private final ClienteServiciosIa ia;
    private final AuditoriaService auditoria;
    private final ModeloPrediccionRepository modelos;

    public PrediccionService(TramiteRepository tramites, HistorialEstadoRepository historial, AlertaService alertas,
                             ClienteServiciosIa ia, AuditoriaService auditoria, ModeloPrediccionRepository modelos) {
        this.tramites = tramites;
        this.historial = historial;
        this.alertas = alertas;
        this.ia = ia;
        this.auditoria = auditoria;
        this.modelos = modelos;
    }

    public record RiesgoDto(Integer idTramite, String numero, String tipo, String estudiante, String estado,
                            java.time.LocalDate fechaLimite, double probabilidad, String nivel) {
    }

    public record AnalisisDto(int historicos, int demoradosHistoricos, Double exactitud, String modelo,
                              int evaluados, int alertasCreadas, int alertasActualizadas, List<RiesgoDto> riesgos,
                              int version, LocalDateTime fechaEntrenamiento, boolean reentrenado) {
    }

    public record ModeloDto(int version, String algoritmo, LocalDateTime fechaEntrenamiento, int ejemplos, int demorados,
                            Double exactitud, String hashSha256, Integer idUsuario, boolean vigente) {
        static ModeloDto de(ModeloPrediccion m) {
            return new ModeloDto(m.getVersion(), m.getAlgoritmo(), m.getFechaEntrenamiento(), m.getEjemplos(), m.getDemorados(),
                    m.getExactitud() == null ? null : m.getExactitud().doubleValue(), m.getHashSha256(), m.getIdUsuario(), m.isVigente());
        }
    }

    @Transactional(readOnly = true)
    public List<ModeloDto> modelos() {
        return modelos.findAllByOrderByVersionDesc().stream().map(ModeloDto::de).toList();
    }

    /** Entrena una versión nueva con el histórico, la registra como vigente y retira la anterior. */
    private ModeloPrediccion entrenar(List<Map<String, Object>> historico) {
        int version = modelos.ultimaVersion() + 1;
        JsonNode r = ia.post("/interno/prediccion/entrenamiento", Map.of("historico", historico, "version", version));
        int ejemplos = r.path("ejemplos").asInt(historico.size());
        int demorados = Math.min(r.path("positivos").asInt(0), ejemplos);
        BigDecimal exactitud = r.path("exactitud").isNumber()
                ? BigDecimal.valueOf(r.path("exactitud").asDouble()).setScale(3, RoundingMode.HALF_UP) : null;
        String hash = r.path("sha256").isTextual() ? r.path("sha256").asText() : null;
        modelos.findByVigenteTrue().ifPresent(anterior -> {
            anterior.retirar();
            modelos.saveAndFlush(anterior);
        });
        ModeloPrediccion m = modelos.save(new ModeloPrediccion(version, r.path("modelo").asText("RandomForest"), ejemplos, demorados,
                exactitud, hash, UsuarioActual.id()));
        auditoria.registrar("ENTRENAR_MODELO", "MODELO_PREDICCION", m.getId(), "Versión " + version + " · " + ejemplos
                + " ejemplos · exactitud " + exactitud + (hash != null ? " · SHA-256 " + hash : ""));
        return m;
    }

    /** Características sin datos personales. "demorado" = cerrado después de la fecha límite. */
    Map<String, Object> caracteristicas(Tramite t, Map<Integer, Long> observaciones) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tipo", t.getTipoTramite().getNombre());
        f.put("prioridad", t.getPrioridad());
        f.put("plazoDias", t.getTipoTramite().getPlazoReferenciaDias());
        f.put("requisitosPendientes", t.getDocumentos().stream().filter(DocumentoTramite::esObligatorioPendiente).count());
        f.put("observaciones", observaciones.getOrDefault(t.getId(), 0L));
        f.put("mesRecepcion", t.getFechaRecepcion().getMonthValue());
        return f;
    }

    @Transactional
    public AnalisisDto analizar() {
        return analizar(false);
    }

    @Transactional
    public AnalisisDto analizar(boolean reentrenar) {
        List<Tramite> todos = tramites.findAll();
        Map<Integer, Long> observaciones = new HashMap<>();
        for (Tramite t : todos) {
            observaciones.put(t.getId(), historial.findByIdTramiteOrderByFechaHoraAscIdAsc(t.getId()).stream()
                    .map(HistorialEstado::getEvento).filter("observar"::equals).count());
        }

        List<Map<String, Object>> historico = new ArrayList<>();
        for (Tramite t : todos) {
            if (t.getFechaCierre() == null) continue;
            Map<String, Object> f = caracteristicas(t, observaciones);
            f.put("demorado", t.getFechaCierre().toLocalDate().isAfter(t.getFechaLimite()));
            historico.add(f);
        }
        Optional<ModeloPrediccion> vigente = modelos.findByVigenteTrue();
        boolean entrenado = reentrenar || vigente.isEmpty();
        ModeloPrediccion m = entrenado ? entrenar(historico) : vigente.get();

        List<Tramite> enCurso = todos.stream().filter(t -> AlertaService.ESTADOS_EN_CURSO.contains(t.getEstadoActual())).toList();
        List<Map<String, Object>> filas = enCurso.stream().map(t -> caracteristicas(t, observaciones)).collect(Collectors.toList());
        JsonNode p = predecir(filas);
        // servicios-ia no tiene el modelo vigente (409) o tiene otra versión (p. ej. se perdió su volumen): se reentrena
        JsonNode versionIa = p == null ? null : p.path("metricas").path("version");
        if (!entrenado && (p == null || (versionIa.isNumber() && versionIa.asInt() != m.getVersion()))) {
            m = entrenar(historico);
            entrenado = true;
            p = ia.post("/interno/prediccion", Map.of("tramites", filas));
        }

        List<RiesgoDto> riesgos = new ArrayList<>();
        int creadas = 0, actualizadas = 0;
        for (int i = 0; i < enCurso.size(); i++) {
            Tramite t = enCurso.get(i);
            double prob = p.path("probabilidades").path(i).asDouble(0);
            String nivel = prob >= UMBRAL_ALTA ? "ALTA" : prob >= UMBRAL_MEDIA ? "MEDIA" : "BAJA";
            riesgos.add(new RiesgoDto(t.getId(), t.getNumero(), t.getTipoTramite().getNombre(), t.getEstudiante().nombreCompleto(),
                    t.getEstadoActual(), t.getFechaLimite(), prob, nivel));
            if (prob >= UMBRAL_MEDIA) {
                boolean nueva = alertas.registrarPrediccion(t, nivel, BigDecimal.valueOf(prob * 100).setScale(2, RoundingMode.HALF_UP),
                        "Probabilidad de demora " + Math.round(prob * 100) + " % según el modelo predictivo");
                if (nueva) creadas++; else actualizadas++;
            }
        }
        riesgos.sort((a, b) -> Double.compare(b.probabilidad(), a.probabilidad()));
        Double exactitud = m.getExactitud() == null ? null : m.getExactitud().doubleValue();
        auditoria.registrar("ANALISIS_PREDICTIVO", "ALERTA", null, "Modelo v" + m.getVersion() + (entrenado ? " (reentrenado)" : "")
                + " · " + m.getEjemplos() + " históricos · " + enCurso.size() + " evaluados · exactitud " + exactitud
                + " · " + creadas + " alerta(s) nuevas");
        return new AnalisisDto(m.getEjemplos(), m.getDemorados(), exactitud, m.getAlgoritmo(), enCurso.size(), creadas, actualizadas,
                riesgos, m.getVersion(), m.getFechaEntrenamiento(), entrenado);
    }

    /** Devuelve null si servicios-ia responde 409 (no tiene un modelo cargado). */
    private JsonNode predecir(List<Map<String, Object>> filas) {
        try {
            return ia.post("/interno/prediccion", Map.of("tramites", filas));
        } catch (ClienteServiciosIa.RespuestaIa e) {
            if (e.getCodigo() == 409) return null;
            throw e;
        }
    }
}
