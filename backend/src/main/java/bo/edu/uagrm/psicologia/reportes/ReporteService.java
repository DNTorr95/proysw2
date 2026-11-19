package bo.edu.uagrm.psicologia.reportes;

import bo.edu.uagrm.psicologia.academico.modelo.AsignacionDocente;
import bo.edu.uagrm.psicologia.academico.repositorio.AsignacionDocenteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.PeriodoAcademicoRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.institucional.modelo.Poa;
import bo.edu.uagrm.psicologia.institucional.repositorio.PoaRepository;
import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CU-27 Generar y exportar reportes (PDF y XLSX). */
@Service
public class ReporteService {

    public static final List<String> TIPOS = List.of("tramites", "correspondencia", "carga-docente", "poa", "rpa");
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager em;
    private final AsignacionDocenteRepository asignaciones;
    private final PeriodoAcademicoRepository periodos;
    private final PoaRepository poas;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public ReporteService(EntityManager em, AsignacionDocenteRepository asignaciones, PeriodoAcademicoRepository periodos,
                          PoaRepository poas, UsuarioRepository usuarios, AuditoriaService auditoria) {
        this.em = em;
        this.asignaciones = asignaciones;
        this.periodos = periodos;
        this.poas = poas;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    public record Archivo(String nombre, String tipoMime, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public Archivo generar(String tipo, String formato, LocalDate desde, LocalDate hasta, Integer idPeriodo, Integer gestion) {
        if (!"pdf".equals(formato) && !"xlsx".equals(formato)) throw new NegocioException("Formato inválido: use pdf o xlsx");
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        LocalDate d = desde == null ? h.minusMonths(3).withDayOfMonth(1) : desde;
        if (d.isAfter(h)) throw new NegocioException("La fecha inicial es posterior a la final");
        String autor = usuarios.findById(UsuarioActual.id()).map(u -> u.getPersona().nombreCompleto()).orElse("");
        Reporte r = switch (tipo) {
            case "tramites" -> tramites(d, h, autor);
            case "correspondencia" -> correspondencia(d, h, autor);
            case "carga-docente" -> cargaDocente(idPeriodo, autor);
            case "poa" -> poa(gestion, autor);
            case "rpa" -> rpa(d, h, autor);
            default -> throw new NegocioException("Tipo de reporte desconocido: " + tipo + " (" + String.join(", ", TIPOS) + ")");
        };
        byte[] contenido = "pdf".equals(formato) ? r.pdf() : r.xlsx();
        auditoria.registrar("GENERAR_REPORTE", "REPORTE", null, tipo + " · " + formato + " · " + r.subtitulo() + " · " + r.filas().size() + " fila(s)");
        String nombre = "reporte-" + tipo + "-" + LocalDate.now() + "." + formato;
        return new Archivo(nombre, "pdf".equals(formato) ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contenido);
    }

    // ------------------------------------------------------------------ reportes

    private Reporte tramites(LocalDate d, LocalDate h, String autor) {
        List<Tramite> lista = em.createQuery("""
                select t from Tramite t join fetch t.tipoTramite join fetch t.estudiante e join fetch e.persona
                where t.fechaRecepcion >= :d and t.fechaRecepcion < :h order by t.fechaRecepcion""", Tramite.class)
                .setParameter("d", d.atStartOfDay()).setParameter("h", h.plusDays(1).atStartOfDay()).getResultList();
        List<List<Object>> filas = lista.stream().map(t -> fila(t.getNumero(), t.getTipoTramite().getNombre(), t.getEstudiante().getRegistro(),
                t.getEstudiante().nombreCompleto(), t.getFechaRecepcion().format(F), t.getFechaLimite().format(F),
                t.getFechaCierre() == null ? "" : t.getFechaCierre().format(F),
                t.getFechaCierre() == null ? null : ChronoUnit.DAYS.between(t.getFechaRecepcion().toLocalDate(), t.getFechaCierre().toLocalDate()),
                t.getEstadoActual(), t.estaVencido() || (t.getFechaCierre() != null && t.getFechaCierre().toLocalDate().isAfter(t.getFechaLimite())) ? "Sí" : "No"))
                .collect(Collectors.toList());
        Map<String, Long> porEstado = lista.stream().collect(Collectors.groupingBy(Tramite::getEstadoActual, Collectors.counting()));
        long demorados = filas.stream().filter(f -> "Sí".equals(f.get(9))).count();
        return new Reporte("Reporte de trámites estudiantiles", periodo(d, h),
                List.of("Número", "Tipo", "Registro", "Estudiante", "Recepción", "Límite", "Cierre", "Días", "Estado", "Fuera de plazo"),
                filas, List.of("Total: " + lista.size() + " · por estado: " + porEstado, "Fuera de plazo: " + demorados), autor);
    }

    private Reporte correspondencia(LocalDate d, LocalDate h, String autor) {
        List<Correspondencia> lista = em.createQuery("""
                select c from Correspondencia c join fetch c.destinatario where c.fechaDocumento between :d and :h
                order by c.fechaDocumento, c.codigo""", Correspondencia.class).setParameter("d", d).setParameter("h", h).getResultList();
        long recibidas = lista.stream().filter(c -> c.getTipo() == Correspondencia.Tipo.RECIBIDA).count();
        return new Reporte("Reporte de correspondencia institucional", periodo(d, h),
                List.of("Código", "Fecha", "Tipo", "Asunto", "Dependencia", "Estado", "Autorizado"),
                lista.stream().map(c -> fila(c.getCodigo(), c.getFechaDocumento().format(F), c.getTipo().name(), c.getAsunto(),
                        c.getDestinatario().getUnidad(), c.getEstado().name(), c.isAutorizado() ? "Sí" : "No")).collect(Collectors.toList()),
                List.of("Total: " + lista.size() + " · recibidas: " + recibidas + " · emitidas: " + (lista.size() - recibidas)), autor);
    }

    private Reporte cargaDocente(Integer idPeriodo, String autor) {
        var p = idPeriodo == null ? periodos.findFirstByEstadoOrderByGestionDescSemestreDesc("ABIERTO")
                .orElseThrow(() -> new NegocioException("Indique el periodo")) :
                periodos.findById(idPeriodo).orElseThrow(() -> new RecursoNoEncontradoException("Periodo", idPeriodo));
        List<AsignacionDocente> lista = asignaciones.findByGrupoPeriodoIdOrderByDocenteCodigoDocenteAsc(p.getId());
        Map<String, Integer> horas = lista.stream().collect(Collectors.groupingBy(a -> a.getDocente().getPersona().nombreCompleto(),
                Collectors.summingInt(AsignacionDocente::getHorasAsignadas)));
        return new Reporte("Reporte de carga docente", "Periodo " + p.nombre(),
                List.of("Docente", "Dedicación", "Materia", "Grupo", "Tipo", "Horas", "Desde", "Hasta", "Antecedente"),
                lista.stream().map(a -> fila(a.getDocente().getPersona().nombreCompleto(), a.getDocente().getDedicacion(),
                        a.getGrupo().getMateria().getSigla() + " " + a.getGrupo().getMateria().getNombre(), a.getGrupo().getCodigo(), a.getTipo(),
                        a.getHorasAsignadas(), a.getFechaInicio().format(F), a.getFechaFin() == null ? "" : a.getFechaFin().format(F),
                        a.getObservacion())).collect(Collectors.toList()),
                List.of("Horas por docente: " + horas), autor);
    }

    private Reporte poa(Integer gestion, String autor) {
        Poa p = (gestion == null ? poas.findAllByOrderByGestionDesc().stream().findFirst() : poas.findByGestion(gestion))
                .orElseThrow(() -> new NegocioException("No hay POA para la gestión indicada"));
        return new Reporte("Reporte de seguimiento del POA", "Gestión " + p.getGestion() + " · avance global " + p.avance() + " %",
                List.of("Actividad", "Meta", "Responsable", "Inicio", "Fin", "Avance (%)", "Estado"),
                p.getActividades().stream().map(a -> fila(a.getDescripcion(), a.getMeta(), a.getResponsable(), a.getFechaInicio().format(F),
                        a.getFechaFin().format(F), a.getAvance().doubleValue(), a.getEstado())).collect(Collectors.toList()),
                List.of("Objetivo: " + p.getObjetivo()), autor);
    }

    private Reporte rpa(LocalDate d, LocalDate h, String autor) {
        List<EjecucionRpa> lista = em.createQuery("""
                select e from EjecucionRpa e join fetch e.proceso where e.fechaInicio >= :d and e.fechaInicio < :h order by e.fechaInicio""", EjecucionRpa.class)
                .setParameter("d", d.atStartOfDay()).setParameter("h", h.plusDays(1).atStartOfDay()).getResultList();
        long ok = lista.stream().filter(e -> EjecucionRpa.COMPLETADA.equals(e.getEstado())).count();
        int manual = lista.isEmpty() ? 0 : lista.get(0).getProceso().getTiempoManualMinutos();
        return new Reporte("Reporte de ejecuciones RPA", periodo(d, h),
                List.of("Ejecución", "Periodo consolidado", "Inicio", "Estado", "Duración (s)", "Procesados", "Válidos", "Errores", "Reejecución de"),
                lista.stream().map(e -> fila(e.getId(), e.getPeriodoDesde().format(F) + " – " + e.getPeriodoHasta().format(F),
                        e.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), e.getEstado(),
                        e.getDuracionMs() == null ? null : e.getDuracionMs() / 1000.0, e.getRegistrosProcesados(), e.getRegistrosValidos(),
                        e.getErroresDetectados(), e.getIdEjecucionOrigen())).collect(Collectors.toList()),
                List.of("Ejecuciones: " + lista.size() + " · completadas: " + ok + " · tasa de éxito: "
                        + (lista.isEmpty() ? 0 : Math.round(ok * 1000.0 / lista.size()) / 10.0) + " %", "Tiempo manual de referencia: " + manual + " min"), autor);
    }

    private static List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    private static String periodo(LocalDate d, LocalDate h) {
        return "Periodo: " + d.format(F) + " al " + h.format(F);
    }
}
