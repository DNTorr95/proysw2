package bo.edu.uagrm.psicologia.reportes;

import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import bo.edu.uagrm.psicologia.academico.repositorio.PeriodoAcademicoRepository;
import bo.edu.uagrm.psicologia.academico.servicio.PlanificacionService;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import bo.edu.uagrm.psicologia.institucional.repositorio.PoaRepository;
import bo.edu.uagrm.psicologia.reportes.alertas.AlertaService;
import bo.edu.uagrm.psicologia.rpa.servicio.RpaService;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** CU-26 Consultar dashboard institucional: indicadores consolidados de todos los módulos. */
@Service
public class DashboardService {

    private static final Set<String> EN_CURSO = Set.of(EstadoTramite.RECEPCIONADO, EstadoTramite.EN_REVISION, EstadoTramite.OBSERVADO);
    private static final List<String> ORDEN_ESTADOS = List.of("RECEPCIONADO", "EN_REVISION", "OBSERVADO", "APROBADO", "RECHAZADO", "ARCHIVADO");

    private final EntityManager em;
    private final RpaService rpa;
    private final AlertaService alertas;
    private final PoaRepository poas;
    private final PeriodoAcademicoRepository periodos;
    private final PlanificacionService planificacion;

    public DashboardService(EntityManager em, RpaService rpa, AlertaService alertas, PoaRepository poas,
                            PeriodoAcademicoRepository periodos, PlanificacionService planificacion) {
        this.em = em;
        this.rpa = rpa;
        this.alertas = alertas;
        this.poas = poas;
        this.periodos = periodos;
        this.planificacion = planificacion;
    }

    public record Conteo(String clave, long valor) {
    }

    public record TipoTramiteKpi(String tipo, long total, long cerrados, long aTiempo, double promedioDias) {
    }

    public record MesCorrespondencia(String mes, long recibidas, long emitidas) {
    }

    public record TramitesKpi(long total, long enCurso, long vencidos, double promedioDiasAtencion, double porcentajeATiempo,
                              List<Conteo> porEstado, List<TipoTramiteKpi> porTipo) {
    }

    public record CorrespondenciaKpi(long ultimoMes, long sinAutorizar, List<MesCorrespondencia> porMes) {
    }

    public record PoaKpi(Integer gestion, BigDecimal avance, long actividades, long concluidas, long retrasadas) {
    }

    public record AcademicoKpi(String periodo, long grupos, long cupo, long inscritos, double ocupacion, long gruposSinDocente) {
    }

    public record Dashboard(LocalDate fecha, TramitesKpi tramites, CorrespondenciaKpi correspondencia, Object rpa,
                            AlertaService.ResumenAlertas alertas, PoaKpi poa, AcademicoKpi academico) {
    }

    @Transactional(readOnly = true)
    public Dashboard obtener() {
        LocalDate hoy = LocalDate.now();
        return new Dashboard(hoy, tramites(hoy), correspondencia(hoy), rpa.metricas(null, null), alertas.resumen(), poa(), academico());
    }

    private TramitesKpi tramites(LocalDate hoy) {
        List<Tramite> todos = em.createQuery("select t from Tramite t join fetch t.tipoTramite", Tramite.class).getResultList();
        List<Tramite> cerrados = todos.stream().filter(t -> t.getFechaCierre() != null).toList();
        long aTiempo = cerrados.stream().filter(t -> !t.getFechaCierre().toLocalDate().isAfter(t.getFechaLimite())).count();
        Map<String, Long> estados = todos.stream().collect(Collectors.groupingBy(Tramite::getEstadoActual, Collectors.counting()));
        List<Conteo> porEstado = ORDEN_ESTADOS.stream().map(e -> new Conteo(e, estados.getOrDefault(e, 0L))).toList();
        Map<String, List<Tramite>> porTipo = todos.stream().collect(Collectors.groupingBy(t -> t.getTipoTramite().getNombre(), LinkedHashMap::new, Collectors.toList()));
        List<TipoTramiteKpi> tipos = porTipo.entrySet().stream().map(e -> {
            List<Tramite> c = e.getValue().stream().filter(t -> t.getFechaCierre() != null).toList();
            return new TipoTramiteKpi(e.getKey(), e.getValue().size(), c.size(),
                    c.stream().filter(t -> !t.getFechaCierre().toLocalDate().isAfter(t.getFechaLimite())).count(), promedioDias(c));
        }).sorted((a, b) -> Long.compare(b.total(), a.total())).toList();
        return new TramitesKpi(todos.size(), todos.stream().filter(t -> EN_CURSO.contains(t.getEstadoActual())).count(),
                todos.stream().filter(Tramite::estaVencido).count(), promedioDias(cerrados),
                cerrados.isEmpty() ? 0 : Math.round(aTiempo * 1000.0 / cerrados.size()) / 10.0, porEstado, tipos);
    }

    private static double promedioDias(List<Tramite> cerrados) {
        return cerrados.isEmpty() ? 0 : Math.round(cerrados.stream()
                .mapToLong(t -> ChronoUnit.DAYS.between(t.getFechaRecepcion().toLocalDate(), t.getFechaCierre().toLocalDate()))
                .average().orElse(0) * 10) / 10.0;
    }

    private CorrespondenciaKpi correspondencia(LocalDate hoy) {
        YearMonth actual = YearMonth.from(hoy);
        LocalDate desde = actual.minusMonths(5).atDay(1);
        List<Correspondencia> lista = em.createQuery("select c from Correspondencia c where c.fechaDocumento >= :d", Correspondencia.class)
                .setParameter("d", desde).getResultList();
        List<MesCorrespondencia> meses = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = actual.minusMonths(i);
            List<Correspondencia> delMes = lista.stream().filter(c -> YearMonth.from(c.getFechaDocumento()).equals(m)).toList();
            meses.add(new MesCorrespondencia(m.toString(), delMes.stream().filter(c -> c.getTipo() == Correspondencia.Tipo.RECIBIDA).count(),
                    delMes.stream().filter(c -> c.getTipo() == Correspondencia.Tipo.EMITIDA).count()));
        }
        long sinAutorizar = em.createQuery("select count(c) from Correspondencia c where c.autorizado = false and c.estado <> :a", Long.class)
                .setParameter("a", Correspondencia.Estado.ARCHIVADA).getSingleResult();
        MesCorrespondencia ultimo = meses.get(meses.size() - 2);
        return new CorrespondenciaKpi(ultimo.recibidas() + ultimo.emitidas(), sinAutorizar, meses);
    }

    private PoaKpi poa() {
        return poas.findAllByOrderByGestionDesc().stream().findFirst().map(p -> new PoaKpi(p.getGestion(), p.avance(), p.getActividades().size(),
                        p.getActividades().stream().filter(a -> ActividadPoa.CONCLUIDA.equals(a.getEstado())).count(),
                        p.getActividades().stream().filter(a -> ActividadPoa.RETRASADA.equals(a.getEstado())).count()))
                .orElse(new PoaKpi(null, BigDecimal.ZERO, 0, 0, 0));
    }

    private AcademicoKpi academico() {
        return periodos.findFirstByEstadoOrderByGestionDescSemestreDesc(PeriodoAcademico.ABIERTO).map(p -> {
            var r = planificacion.resumen(p.getId());
            return new AcademicoKpi(p.nombre(), r.grupos(), r.cupoTotal(), r.inscritos(), r.ocupacion(), r.gruposSinDocente());
        }).orElse(new AcademicoKpi(null, 0, 0, 0, 0, 0));
    }
}
