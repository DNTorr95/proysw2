package bo.edu.uagrm.psicologia.reportes.alertas;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.TramiteRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-28 Consultar alertas · CU-30 Registrar atención de alertas.
 * Las alertas de PLAZO las genera un job diario con el plazo del tipo de trámite.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    static final List<String> ABIERTAS = List.of(Alerta.ABIERTA, Alerta.EN_ATENCION);
    static final Set<String> ESTADOS_EN_CURSO = Set.of(EstadoTramite.RECEPCIONADO, EstadoTramite.EN_REVISION, EstadoTramite.OBSERVADO);
    /** Días antes del vencimiento en que se avisa (nivel MEDIA). */
    static final int DIAS_AVISO = 2;
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertaRepository alertas;
    private final TramiteRepository tramites;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public AlertaService(AlertaRepository alertas, TramiteRepository tramites, UsuarioRepository usuarios, AuditoriaService auditoria) {
        this.alertas = alertas;
        this.tramites = tramites;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    public record AlertaDto(Integer id, Integer idTramite, String numeroTramite, String tipoTramite, String estudiante,
                            String estadoTramite, LocalDate fechaLimite, String origen, String nivel, BigDecimal probabilidad,
                            String motivo, String estado, LocalDateTime fechaGeneracion, String atendidaPor, LocalDateTime fechaAtencion) {
    }

    public record ResumenAlertas(long abiertas, long altas, long medias, long bajas, long porPlazo, long porPrediccion) {
    }

    public record GeneracionDto(int creadas, int actualizadas, int cerradasAutomaticamente) {
    }

    // ------------------------------------------------------------------ consulta (CU-28)

    @Transactional(readOnly = true)
    public Pagina<AlertaDto> listar(String estado, String origen, String nivel, int pagina, int tamano) {
        Specification<Alerta> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if ("ABIERTAS".equals(estado)) p.add(root.get("estado").in(ABIERTAS));
            else if (estado != null && !estado.isBlank()) p.add(cb.equal(root.get("estado"), estado));
            if (origen != null && !origen.isBlank()) p.add(cb.equal(root.get("origen"), origen));
            if (nivel != null && !nivel.isBlank()) p.add(cb.equal(root.get("nivel"), nivel));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = alertas.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaGeneracion", "id")));
        Map<Integer, String> nombres = usuarios.findAllById(page.getContent().stream().map(Alerta::getIdUsuarioAtencion)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getPersona().nombreCompleto()));
        return Pagina.de(page, a -> aDto(a, nombres));
    }

    @Transactional(readOnly = true)
    public ResumenAlertas resumen() {
        return new ResumenAlertas(alertas.findByEstadoIn(ABIERTAS).size(),
                alertas.countByEstadoInAndNivel(ABIERTAS, "ALTA"), alertas.countByEstadoInAndNivel(ABIERTAS, "MEDIA"),
                alertas.countByEstadoInAndNivel(ABIERTAS, "BAJA"), alertas.countByEstadoInAndOrigen(ABIERTAS, Alerta.PLAZO),
                alertas.countByEstadoInAndOrigen(ABIERTAS, Alerta.PREDICCION));
    }

    // ------------------------------------------------------------------ atención (CU-30)

    @Transactional
    public AlertaDto atender(Integer id, String nuevoEstado, String comentario) {
        Alerta a = alertas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Alerta", id));
        if (!a.abierta()) throw new NegocioException(HttpStatus.CONFLICT, "La alerta ya fue atendida");
        if (!Alerta.EN_ATENCION.equals(nuevoEstado) && !Alerta.ATENDIDA.equals(nuevoEstado)) {
            throw new NegocioException("Estado inválido: use EN_ATENCION o ATENDIDA");
        }
        a.atender(nuevoEstado, UsuarioActual.id());
        auditoria.registrar(Alerta.ATENDIDA.equals(nuevoEstado) ? "ATENDER_ALERTA" : "TOMAR_ALERTA", "ALERTA", a.getId(),
                a.getOrigen() + " · " + a.getTramite().getNumero() + " · " + comentario.trim());
        return aDto(a, Map.of(UsuarioActual.id(), usuarios.findById(UsuarioActual.id()).map(u -> u.getPersona().nombreCompleto()).orElse("")));
    }

    // ------------------------------------------------------------------ generación por plazo (job diario)

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional   // la llamada interna no pasa por el proxy: la transacción se abre aquí
    public void generarProgramado() {
        GeneracionDto r = generarPorPlazo(LocalDate.now());
        log.info("alertas de plazo creadas={} actualizadas={} cerradas={}", r.creadas(), r.actualizadas(), r.cerradasAutomaticamente());
    }

    /**
     * Crea/actualiza una alerta PLAZO por trámite en curso: ALTA si venció, MEDIA si vence en ≤ 2 días.
     * Nunca duplica (una alerta abierta por trámite y origen) y cierra las alertas de trámites ya resueltos.
     */
    @Transactional
    public GeneracionDto generarPorPlazo(LocalDate hoy) {
        int creadas = 0, actualizadas = 0;
        Map<Integer, Alerta> abiertasPorTramite = alertas.findByOrigenAndEstadoIn(Alerta.PLAZO, ABIERTAS).stream()
                .collect(Collectors.toMap(a -> a.getTramite().getId(), a -> a, (x, y) -> x));
        for (Tramite t : tramites.findAll()) {
            if (!ESTADOS_EN_CURSO.contains(t.getEstadoActual())) continue;
            long dias = ChronoUnit.DAYS.between(hoy, t.getFechaLimite());
            String nivel;
            String motivo;
            if (dias < 0) {
                nivel = "ALTA";
                motivo = "Plazo vencido el " + t.getFechaLimite().format(F) + " (" + (-dias) + " día(s) de retraso)";
            } else if (dias <= DIAS_AVISO) {
                nivel = "MEDIA";
                motivo = "El plazo vence el " + t.getFechaLimite().format(F) + (dias == 0 ? " (hoy)" : " (en " + dias + " día(s))");
            } else {
                continue;
            }
            Alerta existente = abiertasPorTramite.get(t.getId());
            if (existente == null) {
                alertas.save(new Alerta(t, Alerta.PLAZO, nivel, null, motivo));
                creadas++;
            } else if (!existente.getMotivo().equals(motivo) || !existente.getNivel().equals(nivel)) {
                existente.actualizar(nivel, null, motivo);
                actualizadas++;
            }
        }
        int cerradas = cerrarResueltas();
        if (creadas + actualizadas + cerradas > 0) {
            auditoria.registrarComo(null, "GENERAR_ALERTAS", "ALERTA", null,
                    "Plazos: " + creadas + " creada(s), " + actualizadas + " actualizada(s), " + cerradas + " cerrada(s)");
        }
        return new GeneracionDto(creadas, actualizadas, cerradas);
    }

    /** Alertas de trámites que ya no están en curso: se cierran como ATENDIDA por el sistema. */
    int cerrarResueltas() {
        int n = 0;
        for (Alerta a : alertas.findByEstadoIn(ABIERTAS)) {
            if (!ESTADOS_EN_CURSO.contains(a.getTramite().getEstadoActual())) {
                a.atender(Alerta.ATENDIDA, null);
                n++;
            }
        }
        return n;
    }

    /** Usado por la predicción: crea o actualiza la alerta PREDICCION abierta del trámite. */
    @Transactional
    public boolean registrarPrediccion(Tramite t, String nivel, BigDecimal probabilidad, String motivo) {
        Optional<Alerta> existente = alertas.findByOrigenAndEstadoIn(Alerta.PREDICCION, ABIERTAS).stream()
                .filter(a -> a.getTramite().getId().equals(t.getId())).findFirst();
        if (existente.isPresent()) {
            existente.get().actualizar(nivel, probabilidad, motivo);
            return false;
        }
        alertas.save(new Alerta(t, Alerta.PREDICCION, nivel, probabilidad, motivo));
        return true;
    }

    static AlertaDto aDto(Alerta a, Map<Integer, String> nombres) {
        Tramite t = a.getTramite();
        return new AlertaDto(a.getId(), t.getId(), t.getNumero(), t.getTipoTramite().getNombre(), t.getEstudiante().nombreCompleto(),
                t.getEstadoActual(), t.getFechaLimite(), a.getOrigen(), a.getNivel(), a.getProbabilidad(), a.getMotivo(), a.getEstado(),
                a.getFechaGeneracion(), a.getIdUsuarioAtencion() == null ? (a.getEstado().equals(Alerta.ATENDIDA) ? "Sistema" : null)
                        : nombres.get(a.getIdUsuarioAtencion()), a.getFechaAtencion());
    }
}
