package bo.edu.uagrm.psicologia.institucional.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.ActividadDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.ActividadRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.EventoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.EventoRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.PoaResumenDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.SeguimientoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.SeguimientoRequest;
import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import bo.edu.uagrm.psicologia.institucional.modelo.Evento;
import bo.edu.uagrm.psicologia.institucional.modelo.Poa;
import bo.edu.uagrm.psicologia.institucional.modelo.SeguimientoActividad;
import bo.edu.uagrm.psicologia.institucional.repositorio.ActividadPoaRepository;
import bo.edu.uagrm.psicologia.institucional.repositorio.EventoRepository;
import bo.edu.uagrm.psicologia.institucional.repositorio.PoaRepository;
import bo.edu.uagrm.psicologia.institucional.repositorio.SeguimientoActividadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CU-19 Gestionar seguimiento del POA · CU-20 Gestionar eventos y talleres institucionales. */
@Service
public class PoaEventoService {

    private final PoaRepository poas;
    private final ActividadPoaRepository actividades;
    private final SeguimientoActividadRepository seguimientos;
    private final EventoRepository eventos;
    private final AuditoriaService auditoria;

    public PoaEventoService(PoaRepository poas, ActividadPoaRepository actividades, SeguimientoActividadRepository seguimientos,
                            EventoRepository eventos, AuditoriaService auditoria) {
        this.poas = poas;
        this.actividades = actividades;
        this.seguimientos = seguimientos;
        this.eventos = eventos;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // CU-19 POA
    // =====================================================================

    @Transactional(readOnly = true)
    public List<PoaResumenDto> poas() {
        return poas.findAllByOrderByGestionDesc().stream().map(PoaEventoService::resumen).toList();
    }

    /** Devuelve el POA con sus actividades; recalcula estados (p. ej. RETRASADA al vencer la fecha). */
    @Transactional
    public PoaDto poa(Integer gestion) {
        Poa p = poas.findByGestion(gestion).orElseThrow(() -> new RecursoNoEncontradoException("POA de la gestión", gestion));
        LocalDate hoy = LocalDate.now();
        p.getActividades().forEach(a -> a.recalcularEstado(hoy));
        Map<Integer, Long> porActividad = eventos.findAll().stream().filter(e -> e.getActividad() != null)
                .collect(Collectors.groupingBy(e -> e.getActividad().getId(), Collectors.counting()));
        return new PoaDto(resumen(p), p.getActividades().stream().map(a -> ActividadDto.de(a, porActividad.getOrDefault(a.getId(), 0L))).toList());
    }

    @Transactional
    public PoaResumenDto crearPoa(PoaRequest req) {
        if (poas.findByGestion(req.gestion()).isPresent()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe el POA de la gestión " + req.gestion());
        }
        Poa p = poas.save(new Poa(req.gestion(), req.objetivo().trim()));
        auditoria.registrar("CREAR", "POA", p.getId(), "Gestión " + p.getGestion());
        return resumen(p);
    }

    @Transactional
    public PoaResumenDto actualizarPoa(Integer id, PoaRequest req) {
        Poa p = poas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("POA", id));
        if (!p.getGestion().equals(req.gestion())) throw new NegocioException("No se puede cambiar la gestión del POA");
        p.actualizar(req.objetivo().trim(), req.estado() == null ? p.getEstado() : req.estado());
        auditoria.registrar("ACTUALIZAR", "POA", p.getId(), "Estado " + p.getEstado());
        return resumen(p);
    }

    @Transactional
    public ActividadDto crearActividad(Integer idPoa, ActividadRequest req) {
        Poa p = poas.findById(idPoa).orElseThrow(() -> new RecursoNoEncontradoException("POA", idPoa));
        exigirAbierto(p);
        validarFechasActividad(p, req);
        ActividadPoa a = new ActividadPoa(p, req.descripcion().trim(), req.meta().trim(), vacio(req.responsable()), req.fechaInicio(), req.fechaFin());
        p.getActividades().add(a);
        actividades.save(a);
        auditoria.registrar("CREAR_ACTIVIDAD", "POA", p.getId(), a.getDescripcion());
        return ActividadDto.de(a, 0);
    }

    @Transactional
    public ActividadDto actualizarActividad(Integer id, ActividadRequest req) {
        ActividadPoa a = actividad(id);
        exigirAbierto(a.getPoa());
        validarFechasActividad(a.getPoa(), req);
        a.actualizar(req.descripcion().trim(), req.meta().trim(), vacio(req.responsable()), req.fechaInicio(), req.fechaFin());
        auditoria.registrar("ACTUALIZAR_ACTIVIDAD", "POA", a.getPoa().getId(), a.getDescripcion());
        return ActividadDto.de(a, 0);
    }

    @Transactional(readOnly = true)
    public List<SeguimientoDto> seguimientos(Integer idActividad) {
        return seguimientos.findByIdActividadOrderByFechaDescIdDesc(idActividad).stream().map(SeguimientoDto::de).toList();
    }

    /** Registra un avance; si es menor al actual exige que la observación explique la corrección. */
    @Transactional
    public ActividadDto registrarSeguimiento(Integer idActividad, SeguimientoRequest req) {
        ActividadPoa a = actividad(idActividad);
        exigirAbierto(a.getPoa());
        if (req.idEvento() != null) {
            Evento e = eventos.findById(req.idEvento()).orElseThrow(() -> new RecursoNoEncontradoException("Evento", req.idEvento()));
            if (e.getActividad() == null || !e.getActividad().getId().equals(idActividad)) {
                throw new NegocioException("El evento no está vinculado a esta actividad");
            }
        }
        if (req.avance().compareTo(a.getAvance()) < 0 && req.observacion().trim().length() < 20) {
            throw new NegocioException("El avance es menor al registrado (" + a.getAvance() + " %): justifique la corrección en la observación (mín. 20 caracteres)");
        }
        seguimientos.save(new SeguimientoActividad(a.getId(), req.fecha(), req.avance(), req.observacion().trim(), vacio(req.evidencia()), req.idEvento()));
        a.registrarAvance(req.avance(), LocalDate.now());
        auditoria.registrar("SEGUIMIENTO_POA", "ACTIVIDAD_POA", a.getId(), req.avance() + " % · " + a.getEstado());
        return ActividadDto.de(a, 0);
    }

    // =====================================================================
    // CU-20 Eventos y talleres
    // =====================================================================

    @Transactional(readOnly = true)
    public List<EventoDto> eventos(LocalDate desde, LocalDate hasta) {
        return (desde != null && hasta != null ? eventos.findByFechaBetweenOrderByFechaAsc(desde, hasta) : eventos.findAllByOrderByFechaDesc())
                .stream().map(EventoDto::de).toList();
    }

    @Transactional
    public EventoDto crearEvento(EventoRequest req) {
        Evento e = eventos.save(new Evento(req.nombre().trim(), req.tipo(), req.fecha(), vacio(req.responsable()), actividadOpcional(req.idActividad())));
        auditoria.registrar("CREAR", "EVENTO", e.getId(), e.getTipo() + " · " + e.getNombre() + " · " + e.getFecha());
        return EventoDto.de(e);
    }

    @Transactional
    public EventoDto actualizarEvento(Integer id, EventoRequest req) {
        Evento e = eventos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Evento", id));
        e.actualizar(req.nombre().trim(), req.tipo(), req.fecha(), vacio(req.responsable()), actividadOpcional(req.idActividad()));
        auditoria.registrar("ACTUALIZAR", "EVENTO", e.getId(), e.getNombre());
        return EventoDto.de(e);
    }

    // ---------- Apoyo ----------

    private ActividadPoa actividadOpcional(Integer id) {
        return id == null ? null : actividad(id);
    }

    private ActividadPoa actividad(Integer id) {
        return actividades.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Actividad del POA", id));
    }

    private static void exigirAbierto(Poa p) {
        if ("CERRADO".equals(p.getEstado())) throw new NegocioException(HttpStatus.CONFLICT, "El POA " + p.getGestion() + " está cerrado");
    }

    private static void validarFechasActividad(Poa p, ActividadRequest req) {
        if (req.fechaFin().isBefore(req.fechaInicio())) throw new NegocioException("La fecha de fin es anterior a la de inicio");
        if (req.fechaInicio().getYear() != p.getGestion() || req.fechaFin().getYear() != p.getGestion()) {
            throw new NegocioException("Las fechas de la actividad deben estar dentro de la gestión " + p.getGestion());
        }
    }

    static PoaResumenDto resumen(Poa p) {
        return new PoaResumenDto(p.getId(), p.getGestion(), p.getObjetivo(), p.getEstado(), p.avance(), p.getActividades().size(),
                p.getActividades().stream().filter(a -> ActividadPoa.CONCLUIDA.equals(a.getEstado())).count(),
                p.getActividades().stream().filter(a -> ActividadPoa.RETRASADA.equals(a.getEstado())).count());
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
