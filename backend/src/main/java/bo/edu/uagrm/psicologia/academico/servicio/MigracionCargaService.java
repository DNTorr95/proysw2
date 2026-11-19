package bo.edu.uagrm.psicologia.academico.servicio;

import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.AsignacionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.AsignacionRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.DocenteDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MigracionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MigracionRequest;
import bo.edu.uagrm.psicologia.academico.modelo.AsignacionDocente;
import bo.edu.uagrm.psicologia.academico.modelo.Docente;
import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.academico.modelo.Grupo;
import bo.edu.uagrm.psicologia.academico.modelo.RegistroMigracion;
import bo.edu.uagrm.psicologia.academico.repositorio.AsignacionDocenteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.DocenteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.GrupoRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.RegistroMigracionRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** CU-15 Migraciones entre planes 148-1 y 148-2 · CU-16 Carga docente y antecedentes de cobertura. */
@Service
public class MigracionCargaService {

    private final RegistroMigracionRepository migraciones;
    private final EstudianteRepository estudiantes;
    private final DocenteRepository docentes;
    private final GrupoRepository grupos;
    private final AsignacionDocenteRepository asignaciones;
    private final AuditoriaService auditoria;

    public MigracionCargaService(RegistroMigracionRepository migraciones, EstudianteRepository estudiantes,
                                 DocenteRepository docentes, GrupoRepository grupos,
                                 AsignacionDocenteRepository asignaciones, AuditoriaService auditoria) {
        this.migraciones = migraciones;
        this.estudiantes = estudiantes;
        this.docentes = docentes;
        this.grupos = grupos;
        this.asignaciones = asignaciones;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // CU-15 Migraciones
    // =====================================================================

    @Transactional(readOnly = true)
    public List<MigracionDto> migraciones(Integer idEstudiante) {
        return (idEstudiante == null ? migraciones.findAllByOrderByFechaDescIdDesc()
                : migraciones.findByEstudianteIdOrderByFechaDesc(idEstudiante)).stream().map(MigracionDto::de).toList();
    }

    /** Registra la migración y actualiza el plan del estudiante en la misma transacción. */
    @Transactional
    public MigracionDto migrar(MigracionRequest req) {
        Estudiante e = estudiantes.findById(req.idEstudiante())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", req.idEstudiante()));
        if (e.getPlan().equals(req.planDestino())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El estudiante ya pertenece al plan " + req.planDestino());
        }
        if ("TITULADO".equals(e.getEstado()) || "ABANDONO".equals(e.getEstado())) {
            throw new NegocioException("Un estudiante en estado " + e.getEstado() + " no puede migrar de plan");
        }
        if (req.fecha().isAfter(LocalDate.now())) throw new NegocioException("La fecha de migración no puede ser futura");
        String origen = e.getPlan();
        RegistroMigracion m = migraciones.save(new RegistroMigracion(e, origen, req.planDestino(), req.fecha(), req.observacion().trim()));
        e.migrarAPlan(req.planDestino());
        auditoria.registrar("MIGRAR_PLAN", "ESTUDIANTE", e.getId(), e.getRegistro() + ": " + origen + " -> " + req.planDestino());
        return MigracionDto.de(m);
    }

    // =====================================================================
    // CU-16 Carga docente
    // =====================================================================

    @Transactional(readOnly = true)
    public List<DocenteDto> docentes(Integer idPeriodo) {
        return docentes.findAllByOrderByCodigoDocenteAsc().stream().map(d -> new DocenteDto(d.getId(), d.getCodigoDocente(),
                d.getPersona().nombreCompleto(), d.getCategoria(), d.getDedicacion(), d.getEspecialidad(), d.limiteHoras(),
                idPeriodo == null ? 0 : asignaciones.horasVigentes(d.getId(), idPeriodo))).toList();
    }

    @Transactional(readOnly = true)
    public List<AsignacionDto> asignaciones(Integer idPeriodo) {
        return asignaciones.findByGrupoPeriodoIdOrderByDocenteCodigoDocenteAsc(idPeriodo).stream().map(AsignacionDto::de).toList();
    }

    /** Antecedentes de cobertura: todas las asignaciones históricas del docente. */
    @Transactional(readOnly = true)
    public List<AsignacionDto> antecedentes(Integer idDocente) {
        return asignaciones.findByDocenteIdOrderByFechaInicioDesc(idDocente).stream().map(AsignacionDto::de).toList();
    }

    @Transactional
    public AsignacionDto asignar(AsignacionRequest req) {
        Docente d = docentes.findById(req.idDocente()).orElseThrow(() -> new RecursoNoEncontradoException("Docente", req.idDocente()));
        Grupo g = grupos.bloquear(req.idGrupo()).orElseThrow(() -> new RecursoNoEncontradoException("Grupo", req.idGrupo()));
        if (g.getPeriodo().estaCerrado()) throw new NegocioException(HttpStatus.CONFLICT, "El periodo del grupo está cerrado");
        if (req.fechaFin() != null && req.fechaFin().isBefore(req.fechaInicio())) {
            throw new NegocioException("La fecha de fin es anterior a la de inicio");
        }
        if (req.horas() > g.getMateria().getHoras()) {
            throw new NegocioException("Las horas asignadas superan las " + g.getMateria().getHoras() + " horas de la materia");
        }
        List<AsignacionDocente> delGrupo = asignaciones.findByGrupoIdOrderByFechaInicioAsc(g.getId()).stream()
                .filter(a -> a.vigente(LocalDate.now())).toList();
        if (delGrupo.stream().anyMatch(a -> a.getDocente().getId().equals(d.getId()))) {
            throw new NegocioException(HttpStatus.CONFLICT, "El docente ya tiene una asignación vigente en este grupo");
        }
        if (!"COBERTURA".equals(req.tipo()) && delGrupo.stream().anyMatch(a -> !"COBERTURA".equals(a.getTipo()))) {
            throw new NegocioException(HttpStatus.CONFLICT, "El grupo ya tiene un docente responsable; registre una COBERTURA o finalice la asignación vigente");
        }
        if ("COBERTURA".equals(req.tipo()) && (req.observacion() == null || req.observacion().isBlank())) {
            throw new NegocioException("La cobertura requiere el antecedente (motivo, docente cubierto, documento)");
        }
        long carga = asignaciones.horasVigentes(d.getId(), g.getPeriodo().getId());
        if (carga + req.horas() > d.limiteHoras()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Se excede la carga horaria del docente: " + carga + " + " + req.horas()
                    + " > " + d.limiteHoras() + " h (" + d.getDedicacion() + ")");
        }
        AsignacionDocente a = asignaciones.save(new AsignacionDocente(d, g, req.tipo(), req.horas(), req.fechaInicio(),
                req.fechaFin(), req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().trim()));
        auditoria.registrar("ASIGNAR_DOCENTE", "GRUPO", g.getId(), d.getCodigoDocente() + " · " + req.tipo() + " · "
                + req.horas() + " h · " + g.getMateria().getSigla() + " " + g.getCodigo());
        return AsignacionDto.de(a);
    }

    @Transactional
    public AsignacionDto finalizar(Integer id, LocalDate fecha, String observacion) {
        AsignacionDocente a = asignaciones.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Asignación", id));
        if (!a.vigente(LocalDate.now())) throw new NegocioException(HttpStatus.CONFLICT, "La asignación ya finalizó");
        if (fecha.isBefore(a.getFechaInicio())) throw new NegocioException("La fecha de fin es anterior al inicio de la asignación");
        a.finalizar(fecha, observacion.trim());
        auditoria.registrar("FINALIZAR_ASIGNACION", "GRUPO", a.getGrupo().getId(), a.getDocente().getCodigoDocente() + ": " + observacion);
        return AsignacionDto.de(a);
    }
}
