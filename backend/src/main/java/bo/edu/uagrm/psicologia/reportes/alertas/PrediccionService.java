package bo.edu.uagrm.psicologia.reportes.alertas;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CU-29 Análisis predictivo: entrena el modelo de servicios-ia con los trámites históricos cerrados
 * (sin datos personales) y estima la probabilidad de demora de los trámites en curso. Si supera el
 * umbral, crea una alerta de origen PREDICCION (sin duplicar).
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

    public PrediccionService(TramiteRepository tramites, HistorialEstadoRepository historial, AlertaService alertas,
                             ClienteServiciosIa ia, AuditoriaService auditoria) {
        this.tramites = tramites;
        this.historial = historial;
        this.alertas = alertas;
        this.ia = ia;
        this.auditoria = auditoria;
    }

    public record RiesgoDto(Integer idTramite, String numero, String tipo, String estudiante, String estado,
                            java.time.LocalDate fechaLimite, double probabilidad, String nivel) {
    }

    public record AnalisisDto(int historicos, int demoradosHistoricos, Double exactitud, String modelo,
                              int evaluados, int alertasCreadas, int alertasActualizadas, List<RiesgoDto> riesgos) {
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
        JsonNode m = ia.post("/interno/prediccion/entrenamiento", Map.of("historico", historico));

        List<Tramite> enCurso = todos.stream().filter(t -> AlertaService.ESTADOS_EN_CURSO.contains(t.getEstadoActual())).toList();
        List<Map<String, Object>> filas = enCurso.stream().map(t -> caracteristicas(t, observaciones)).collect(Collectors.toList());
        JsonNode p = ia.post("/interno/prediccion", Map.of("tramites", filas));

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
        Double exactitud = m.path("exactitud").isNumber() ? m.path("exactitud").asDouble() : null;
        auditoria.registrar("ANALISIS_PREDICTIVO", "ALERTA", null, historico.size() + " históricos · " + enCurso.size()
                + " evaluados · exactitud " + exactitud + " · " + creadas + " alerta(s) nuevas");
        return new AnalisisDto(historico.size(), m.path("positivos").asInt(), exactitud, m.path("modelo").asText(),
                enCurso.size(), creadas, actualizadas, riesgos);
    }
}
