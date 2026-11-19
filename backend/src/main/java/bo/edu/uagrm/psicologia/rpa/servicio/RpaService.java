package bo.edu.uagrm.psicologia.rpa.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.repositorio.CorrespondenciaRepository;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EjecucionDetalleDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EjecucionDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.IncidenciaDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.IniciarEjecucionRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.MetricasDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.PrevisualizacionDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.ProcesoDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.ReejecutarRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.RevisorDto;
import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.IncidenciaRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import bo.edu.uagrm.psicologia.rpa.modelo.ProcesoRpa;
import bo.edu.uagrm.psicologia.rpa.repositorio.EjecucionRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.IncidenciaRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.ProcesoRpaRepository;
import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-31 Ejecutar consolidación de correspondencia · CU-36 Consultar resultados e incidencias ·
 * CU-37 Gestionar incidencias y reejecuciones.
 */
@Service
public class RpaService {

    static final Set<String> PERMISOS_REVISION = Set.of("REVISAR_RPA", "AUTORIZAR_ENVIO_RPA");
    private static final List<String> EN_CURSO = List.of(EjecucionRpa.PENDIENTE, EjecucionRpa.EN_PROCESO);
    private static final List<String> INFORMES_VIGENTES = List.of(InformeMensual.PENDIENTE_REVISION, InformeMensual.APROBADO);

    private final ProcesoRpaRepository procesos;
    private final EjecucionRpaRepository ejecuciones;
    private final InformeMensualRepository informes;
    private final NotificacionCorreoRepository correos;
    private final IncidenciaRpaRepository incidencias;
    private final CorrespondenciaRepository correspondencias;
    private final UsuarioRepository usuarios;
    private final ProcesadorEjecucionRpa procesador;
    private final AuditoriaService auditoria;

    public RpaService(ProcesoRpaRepository procesos, EjecucionRpaRepository ejecuciones, InformeMensualRepository informes,
                      NotificacionCorreoRepository correos, IncidenciaRpaRepository incidencias,
                      CorrespondenciaRepository correspondencias, UsuarioRepository usuarios,
                      ProcesadorEjecucionRpa procesador, AuditoriaService auditoria) {
        this.procesos = procesos;
        this.ejecuciones = ejecuciones;
        this.informes = informes;
        this.correos = correos;
        this.incidencias = incidencias;
        this.correspondencias = correspondencias;
        this.usuarios = usuarios;
        this.procesador = procesador;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // Catálogos
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ProcesoDto> procesos() {
        return procesos.findAll(Sort.by("nombre")).stream().map(ProcesoDto::de).toList();
    }

    /** Usuarios activos cuyo rol puede revisar resultados RPA. */
    @Transactional(readOnly = true)
    public List<RevisorDto> revisores() {
        return usuarios.findAll().stream().filter(Usuario::estaActivo).filter(RpaService::puedeRevisar)
                .map(u -> new RevisorDto(u.getId(), u.getPersona().nombreCompleto(), u.getRol().getNombre(), u.getPersona().getCorreo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PrevisualizacionDto previsualizar(Integer idProceso, LocalDate desde, LocalDate hasta, String tipo) {
        validarPeriodo(desde, hasta);
        Correspondencia.Tipo t = "TODAS".equals(tipo) ? null : Correspondencia.Tipo.valueOf(tipo);
        Optional<InformeMensual> vigente = informes.vigentes(idProceso, desde, hasta, tipo, INFORMES_VIGENTES).stream().findFirst();
        return new PrevisualizacionDto(correspondencias.contarDelPeriodo(true, desde, hasta, t),
                correspondencias.contarDelPeriodo(false, desde, hasta, t),
                vigente.map(InformeMensual::getId).orElse(null), vigente.map(InformeMensual::getEstadoRevision).orElse(null),
                ejecuciones.existsByProcesoIdAndPeriodoDesdeAndPeriodoHastaAndTipoCorrespondenciaAndEstadoIn(
                        idProceso, desde, hasta, tipo, EN_CURSO));
    }

    // =====================================================================
    // CU-31 Iniciar ejecución
    // =====================================================================

    @Transactional
    public EjecucionDto iniciar(IniciarEjecucionRequest req) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        ProcesoRpa proceso = procesos.findById(req.idProceso())
                .orElseThrow(() -> new RecursoNoEncontradoException("Procedimiento RPA", req.idProceso()));
        if (!proceso.estaHabilitado()) {
            throw new NegocioException(HttpStatus.CONFLICT, "El procedimiento '" + proceso.getNombre() + "' está deshabilitado");
        }
        validarPeriodo(req.periodoDesde(), req.periodoHasta());
        Usuario revisor = validarRevisor(req.idRevisor(), u.id());
        exigirSinDuplicados(proceso.getId(), req.periodoDesde(), req.periodoHasta(), req.tipoCorrespondencia());

        EjecucionRpa e = ejecuciones.save(new EjecucionRpa(proceso, u.id(), revisor.getId(), null,
                req.periodoDesde(), req.periodoHasta(), req.tipoCorrespondencia()));
        auditoria.registrar("RPA_INICIAR", "EJECUCION_RPA", e.getId(), proceso.getNombre() + " · periodo "
                + e.getPeriodoDesde() + " a " + e.getPeriodoHasta() + " · " + e.getTipoCorrespondencia()
                + " · revisor " + revisor.getNombreUsuario());
        lanzarDespuesDelCommit(e.getId(), limpiar(req.destinatariosCorreo()));
        return resumen(e);
    }

    // =====================================================================
    // CU-37 Reejecución
    // =====================================================================

    @Transactional
    public EjecucionDto reejecutar(Integer idOrigen, ReejecutarRequest req) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        EjecucionRpa origen = ejecuciones.bloquear(idOrigen).orElseThrow(() -> new RecursoNoEncontradoException("Ejecución RPA", idOrigen));
        String motivo = motivoNoReejecutable(origen);
        if (motivo != null) throw new NegocioException(HttpStatus.CONFLICT, motivo);
        exigirSinDuplicados(origen.getProceso().getId(), origen.getPeriodoDesde(), origen.getPeriodoHasta(),
                origen.getTipoCorrespondencia());

        Optional<NotificacionCorreo> correoAnterior = informes.findByEjecucionId(idOrigen)
                .flatMap(i -> correos.findByInformeId(i.getId()));
        List<String> destinatarios = req != null && req.destinatariosCorreo() != null && !req.destinatariosCorreo().isEmpty()
                ? limpiar(req.destinatariosCorreo())
                : correoAnterior.map(NotificacionCorreo::listaDestinatarios).orElse(List.of());
        if (destinatarios.isEmpty()) {
            throw new NegocioException("Indique los destinatarios del correo para la reejecución");
        }
        Integer idRevisor = req != null && req.idRevisor() != null ? validarRevisor(req.idRevisor(), u.id()).getId()
                : origen.getIdUsuarioRevisor();

        EjecucionRpa nueva = ejecuciones.save(new EjecucionRpa(origen.getProceso(), u.id(), idRevisor, origen.getId(),
                origen.getPeriodoDesde(), origen.getPeriodoHasta(), origen.getTipoCorrespondencia()));
        incidencias.findByIdEjecucionOrderByIdAsc(origen.getId()).stream()
                .filter(i -> IncidenciaRpa.ABIERTA.equals(i.getEstado()))
                .forEach(i -> i.enviarAReejecucion(nueva.getId()));
        auditoria.registrar("RPA_REEJECUTAR", "EJECUCION_RPA", nueva.getId(), "Reejecución de la ejecución " + origen.getId()
                + (req != null && req.motivo() != null ? ". Motivo: " + req.motivo() : ""));
        lanzarDespuesDelCommit(nueva.getId(), destinatarios);
        return resumen(nueva);
    }

    /**
     * Reglas de reejecución: solo una ejecución FALLIDA, o COMPLETADA cuyo informe fue DEVUELTO, que no haya sido
     * enviada ni reejecutada antes. Devuelve el motivo si no se permite, o null.
     */
    String motivoNoReejecutable(EjecucionRpa e) {
        if (e.estaEnCurso()) return "La ejecución todavía está en curso";
        if (ejecuciones.findFirstByIdEjecucionOrigen(e.getId()).isPresent()) {
            return "La ejecución ya fue reejecutada (ver la ejecución " + ejecuciones.findFirstByIdEjecucionOrigen(e.getId()).get().getId() + ")";
        }
        Optional<InformeMensual> informe = informes.findByEjecucionId(e.getId());
        if (EjecucionRpa.COMPLETADA.equals(e.getEstado())) {
            if (informe.isEmpty() || !InformeMensual.DEVUELTO.equals(informe.get().getEstadoRevision())) {
                return "Solo se reejecutan ejecuciones FALLIDAS o cuyo informe fue DEVUELTO";
            }
        }
        if (informe.isPresent()) {
            Optional<NotificacionCorreo> c = correos.findByInformeId(informe.get().getId());
            if (c.isPresent() && Set.of(NotificacionCorreo.ENVIADO, NotificacionCorreo.AUTORIZADO).contains(c.get().getEstado())) {
                return "El informe ya fue autorizado o enviado; no puede reejecutarse";
            }
        }
        return null;
    }

    // =====================================================================
    // CU-36 Consulta de resultados, incidencias y métricas
    // =====================================================================

    @Transactional(readOnly = true)
    public Pagina<EjecucionDto> listar(String estado, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Specification<EjecucionRpa> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (estado != null && !estado.isBlank()) p.add(cb.equal(root.get("estado"), estado));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaInicio"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = ejecuciones.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaInicio", "id")));
        Map<Integer, String> nombres = nombres(page.getContent().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getIdUsuario(), e.getIdUsuarioRevisor())).collect(Collectors.toSet()));
        return Pagina.de(page, e -> aDto(e, nombres));
    }

    @Transactional(readOnly = true)
    public EjecucionDetalleDto obtener(Integer id) {
        EjecucionRpa e = ejecuciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Ejecución RPA", id));
        String motivo = motivoNoReejecutable(e);
        UsuarioAutenticado u = UsuarioActual.obtener();
        boolean puede = motivo == null && u.tienePermiso("GESTIONAR_REEJECUCION_RPA");
        return new EjecucionDetalleDto(aDto(e, nombres(Set.of(e.getIdUsuario(), e.getIdUsuarioRevisor()))),
                procesador.deJson(e.getLogPasos()),
                incidencias.findByIdEjecucionOrderByIdAsc(id).stream().map(IncidenciaDto::de).toList(),
                e.getMensajeError(), puede,
                motivo != null ? motivo : (u.tienePermiso("GESTIONAR_REEJECUCION_RPA") ? null : "Requiere el permiso GESTIONAR_REEJECUCION_RPA"));
    }

    @Transactional(readOnly = true)
    public MetricasDto metricas(LocalDate desde, LocalDate hasta) {
        Specification<EjecucionRpa> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaInicio"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        List<EjecucionRpa> lista = ejecuciones.findAll(spec);
        long completadas = lista.stream().filter(e -> EjecucionRpa.COMPLETADA.equals(e.getEstado())).count();
        long fallidas = lista.stream().filter(e -> EjecucionRpa.FALLIDA.equals(e.getEstado())).count();
        long enCurso = lista.stream().filter(EjecucionRpa::estaEnCurso).count();
        var duraciones = lista.stream().filter(e -> e.getDuracionMs() != null && EjecucionRpa.COMPLETADA.equals(e.getEstado()))
                .mapToLong(EjecucionRpa::getDuracionMs).summaryStatistics();
        int manual = procesos.findAll().stream().findFirst().map(ProcesoRpa::getTiempoManualMinutos).orElse(0);
        double promedioMin = duraciones.getCount() == 0 ? 0 : duraciones.getAverage() / 60000.0;
        double ahorroHoras = completadas * Math.max(0, manual - promedioMin) / 60.0;
        long terminadas = completadas + fallidas;
        return new MetricasDto(lista.size(), completadas, fallidas, enCurso,
                terminadas == 0 ? 0 : Math.round(completadas * 1000.0 / terminadas) / 10.0,
                duraciones.getCount() == 0 ? null : Math.round(duraciones.getAverage() * 10) / 10.0,
                duraciones.getCount() == 0 ? null : duraciones.getMax(),
                lista.stream().mapToLong(EjecucionRpa::getRegistrosProcesados).sum(),
                lista.stream().mapToLong(EjecucionRpa::getErroresDetectados).sum(),
                manual, Math.round(promedioMin * 100) / 100.0, Math.round(ahorroHoras * 10) / 10.0,
                informes.countByEstadoRevision(InformeMensual.PENDIENTE_REVISION),
                informes.countByEstadoRevision(InformeMensual.APROBADO),
                informes.countByEstadoRevision(InformeMensual.DEVUELTO),
                correos.countByEstado(NotificacionCorreo.ENVIADO),
                lista.stream().filter(e -> e.getIdEjecucionOrigen() != null).count());
    }

    @Transactional
    public IncidenciaDto cerrarIncidencia(Integer id, String comentario) {
        IncidenciaRpa i = incidencias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Incidencia", id));
        if (IncidenciaRpa.CERRADA.equals(i.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La incidencia ya está cerrada");
        }
        i.cerrar();
        auditoria.registrar("CERRAR_INCIDENCIA", "INCIDENCIA_RPA", i.getId(), i.getTipo() + " · " + comentario.trim());
        return IncidenciaDto.de(i);
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    private void exigirSinDuplicados(Integer idProceso, LocalDate desde, LocalDate hasta, String tipo) {
        if (ejecuciones.existsByProcesoIdAndPeriodoDesdeAndPeriodoHastaAndTipoCorrespondenciaAndEstadoIn(
                idProceso, desde, hasta, tipo, EN_CURSO)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya hay una ejecución en curso para ese periodo y tipo");
        }
        List<InformeMensual> vigentes = informes.vigentes(idProceso, desde, hasta, tipo, INFORMES_VIGENTES);
        if (!vigentes.isEmpty()) {
            InformeMensual i = vigentes.get(0);
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe el informe " + i.getId() + " (" + i.getEstadoRevision()
                    + ") para ese periodo y tipo; no se generan informes duplicados. Si fue devuelto, use la reejecución.");
        }
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new NegocioException("Indique el periodo");
        if (hasta.isBefore(desde)) throw new NegocioException("La fecha final es anterior a la inicial");
        if (hasta.isAfter(LocalDate.now())) throw new NegocioException("El periodo no puede incluir fechas futuras");
        if (desde.plusDays(366).isBefore(hasta)) throw new NegocioException("El periodo no puede superar un año");
    }

    private Usuario validarRevisor(Integer idRevisor, Integer idSolicitante) {
        Usuario r = usuarios.findById(idRevisor).orElseThrow(() -> new RecursoNoEncontradoException("Revisor", idRevisor));
        if (r.getEstado() != EstadoCuenta.ACTIVO || !puedeRevisar(r)) {
            throw new NegocioException("El revisor seleccionado no está activo o no tiene permiso para revisar resultados RPA");
        }
        if (r.getId().equals(idSolicitante)) {
            throw new NegocioException("El revisor debe ser una persona distinta de quien ejecuta el proceso");
        }
        return r;
    }

    private static boolean puedeRevisar(Usuario u) {
        return u.getRol().getPermisos().stream().map(Permiso::getCodigo).anyMatch(PERMISOS_REVISION::contains);
    }

    private static List<String> limpiar(List<String> correos) {
        return correos.stream().map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).distinct().toList();
    }

    /** El trabajador se invoca solo después de confirmar la transacción (la ejecución ya existe en PENDIENTE). */
    private void lanzarDespuesDelCommit(Integer idEjecucion, List<String> destinatarios) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                procesador.procesarAsync(idEjecucion, destinatarios);
            }
        });
    }

    private EjecucionDto resumen(EjecucionRpa e) {
        return aDto(e, nombres(Set.of(e.getIdUsuario(), e.getIdUsuarioRevisor())));
    }

    private Map<Integer, String> nombres(Collection<Integer> ids) {
        Map<Integer, String> m = new HashMap<>();
        usuarios.findAllById(ids).forEach(x -> m.put(x.getId(), x.getPersona().nombreCompleto()));
        return m;
    }

    EjecucionDto aDto(EjecucionRpa e, Map<Integer, String> nombres) {
        Optional<InformeMensual> informe = informes.findByEjecucionId(e.getId());
        Optional<NotificacionCorreo> correo = informe.flatMap(i -> correos.findByInformeId(i.getId()));
        Integer idReejecucion = ejecuciones.findFirstByIdEjecucionOrigen(e.getId()).map(EjecucionRpa::getId).orElse(null);
        return new EjecucionDto(e.getId(), e.getProceso().getNombre(), e.getPeriodoDesde(), e.getPeriodoHasta(),
                e.getTipoCorrespondencia(), e.getEstado(), situacion(e, informe, correo), e.getIdUsuario(),
                nombres.get(e.getIdUsuario()), e.getIdUsuarioRevisor(), nombres.get(e.getIdUsuarioRevisor()),
                e.getFechaInicio(), e.getFechaFin(), e.getDuracionMs(), e.getRegistrosProcesados(), e.getRegistrosValidos(),
                e.getErroresDetectados(), e.getIdEjecucionOrigen(), idReejecucion,
                informe.map(InformeMensual::getId).orElse(null), informe.map(InformeMensual::getEstadoRevision).orElse(null),
                correo.map(NotificacionCorreo::getEstado).orElse(null), incidencias.countByIdEjecucion(e.getId()));
    }

    /** Estado legible del ciclo completo: incluye DEVUELTA y ENVIADA, que se derivan del informe y del correo. */
    private static String situacion(EjecucionRpa e, Optional<InformeMensual> informe, Optional<NotificacionCorreo> correo) {
        if (!EjecucionRpa.COMPLETADA.equals(e.getEstado())) return e.getEstado();
        if (correo.isPresent() && NotificacionCorreo.ENVIADO.equals(correo.get().getEstado())) return "ENVIADA";
        if (correo.isPresent() && NotificacionCorreo.ERROR.equals(correo.get().getEstado())) return "ERROR_ENVIO";
        return informe.map(i -> switch (i.getEstadoRevision()) {
            case InformeMensual.DEVUELTO -> "DEVUELTA";
            case InformeMensual.APROBADO -> "APROBADA";
            default -> "PENDIENTE_REVISION";
        }).orElse(e.getEstado());
    }
}
