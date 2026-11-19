package bo.edu.uagrm.psicologia.academico.servicio;

import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.ActualizarGrupoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.GrupoDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.GrupoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.InscripcionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MateriaDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MateriaRequest;
import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.academico.modelo.Grupo;
import bo.edu.uagrm.psicologia.academico.modelo.Inscripcion;
import bo.edu.uagrm.psicologia.academico.modelo.Materia;
import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import bo.edu.uagrm.psicologia.academico.repositorio.AsignacionDocenteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.GrupoRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.InscripcionRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.MateriaRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.PeriodoAcademicoRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** CU-14 Gestionar materias, grupos y cupos (incluye inscripciones y cambios de grupo). */
@Service
public class MateriaGrupoService {

    private static final Set<String> ESTADOS_INSCRIBIBLES = Set.of("REGULAR", "CONDICIONAL");

    private final MateriaRepository materias;
    private final GrupoRepository grupos;
    private final PeriodoAcademicoRepository periodos;
    private final InscripcionRepository inscripciones;
    private final EstudianteRepository estudiantes;
    private final AsignacionDocenteRepository asignaciones;
    private final AuditoriaService auditoria;

    public MateriaGrupoService(MateriaRepository materias, GrupoRepository grupos, PeriodoAcademicoRepository periodos,
                               InscripcionRepository inscripciones, EstudianteRepository estudiantes,
                               AsignacionDocenteRepository asignaciones, AuditoriaService auditoria) {
        this.materias = materias;
        this.grupos = grupos;
        this.periodos = periodos;
        this.inscripciones = inscripciones;
        this.estudiantes = estudiantes;
        this.asignaciones = asignaciones;
        this.auditoria = auditoria;
    }

    // ---------- Materias ----------

    @Transactional(readOnly = true)
    public List<MateriaDto> materias(String plan) {
        return (plan == null || plan.isBlank() ? materias.findAllByOrderByPlanAscNivelAscSiglaAsc()
                : materias.findByPlanOrderByNivelAscSiglaAsc(plan)).stream().map(MateriaDto::de).toList();
    }

    @Transactional
    public MateriaDto crearMateria(MateriaRequest req) {
        if (materias.existsBySiglaAndPlan(req.sigla(), req.plan())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La materia " + req.sigla() + " ya existe en el plan " + req.plan());
        }
        Materia m = materias.save(new Materia(req.sigla(), req.nombre().trim(), req.plan(), req.nivel(), req.horas()));
        auditoria.registrar("CREAR", "MATERIA", m.getId(), m.getSigla() + " " + m.getNombre() + " (" + m.getPlan() + ")");
        return MateriaDto.de(m);
    }

    @Transactional
    public MateriaDto actualizarMateria(Integer id, MateriaRequest req) {
        Materia m = materias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Materia", id));
        if (!m.getSigla().equals(req.sigla()) || !m.getPlan().equals(req.plan())) {
            throw new NegocioException("La sigla y el plan de una materia no se modifican; registre una nueva materia");
        }
        m.actualizar(req.nombre().trim(), req.nivel(), req.horas());
        auditoria.registrar("ACTUALIZAR", "MATERIA", m.getId(), m.getSigla());
        return MateriaDto.de(m);
    }

    // ---------- Grupos y cupos ----------

    @Transactional(readOnly = true)
    public List<GrupoDto> grupos(Integer idPeriodo) {
        Map<Integer, List<String>> docentes = asignaciones.findByGrupoPeriodoIdOrderByDocenteCodigoDocenteAsc(idPeriodo).stream()
                .filter(a -> a.vigente(LocalDate.now()))
                .collect(Collectors.groupingBy(a -> a.getGrupo().getId(),
                        Collectors.mapping(a -> a.getDocente().getPersona().nombreCompleto() + " (" + a.getTipo().toLowerCase() + ")", Collectors.toList())));
        return grupos.findByPeriodoIdOrderByMateriaPlanAscMateriaNivelAscMateriaSiglaAscCodigoAsc(idPeriodo).stream()
                .map(g -> aDto(g, docentes.getOrDefault(g.getId(), List.of()))).toList();
    }

    @Transactional
    public GrupoDto crearGrupo(GrupoRequest req) {
        Materia m = materias.findById(req.idMateria()).orElseThrow(() -> new RecursoNoEncontradoException("Materia", req.idMateria()));
        PeriodoAcademico p = periodos.findById(req.idPeriodo()).orElseThrow(() -> new RecursoNoEncontradoException("Periodo", req.idPeriodo()));
        if (p.estaCerrado()) throw new NegocioException(HttpStatus.CONFLICT, "El periodo " + p.nombre() + " está cerrado");
        if (grupos.existsByMateriaIdAndPeriodoIdAndCodigo(m.getId(), p.getId(), req.codigo())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe el grupo " + req.codigo() + " de " + m.getSigla() + " en " + p.nombre());
        }
        Grupo g = grupos.save(new Grupo(m, p, req.codigo(), req.cupo(), req.turno(), vacio(req.horario()), vacio(req.aula())));
        auditoria.registrar("CREAR", "GRUPO", g.getId(), m.getSigla() + " " + g.getCodigo() + " · " + p.nombre() + " · cupo " + g.getCupo());
        return aDto(g, List.of());
    }

    @Transactional
    public GrupoDto actualizarGrupo(Integer id, ActualizarGrupoRequest req) {
        Grupo g = grupos.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Grupo", id));
        if (g.getPeriodo().estaCerrado()) throw new NegocioException(HttpStatus.CONFLICT, "El periodo está cerrado");
        if (req.cupo() < g.getInscritos()) {
            throw new NegocioException(HttpStatus.CONFLICT, "El cupo no puede ser menor a los " + g.getInscritos() + " inscritos");
        }
        int anterior = g.getCupo();
        g.actualizar(req.cupo(), req.turno(), vacio(req.horario()), vacio(req.aula()));
        auditoria.registrar("ACTUALIZAR", "GRUPO", g.getId(), g.getMateria().getSigla() + " " + g.getCodigo() + " · cupo " + anterior + " -> " + g.getCupo());
        return aDto(g, List.of());
    }

    // ---------- Inscripciones ----------

    @Transactional(readOnly = true)
    public List<InscripcionDto> inscripciones(Integer idGrupo) {
        return inscripciones.findByGrupoIdOrderByEstudianteRegistroAsc(idGrupo).stream().map(MateriaGrupoService::aDto).toList();
    }

    /**
     * Inscribe con todas las reglas: periodo ABIERTO, fecha de cierre (salvo caso especial, p. ej. trámite de
     * adición fuera de fecha), plan del estudiante = plan de la materia, estado del estudiante, sin duplicar
     * la materia y con cupo disponible (bloqueo del grupo para no sobrepasarlo con inscripciones simultáneas).
     */
    @Transactional
    public InscripcionDto inscribir(Integer idGrupo, Integer idEstudiante, boolean casoEspecial, String motivo) {
        Grupo g = grupos.bloquear(idGrupo).orElseThrow(() -> new RecursoNoEncontradoException("Grupo", idGrupo));
        Estudiante e = estudiantes.findById(idEstudiante).orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", idEstudiante));
        validarInscripcion(g, e, casoEspecial, motivo);
        Inscripcion previa = inscripciones.findByEstudianteIdAndGrupoId(idEstudiante, idGrupo).orElse(null);
        if (previa != null) {
            throw new NegocioException(HttpStatus.CONFLICT, "El estudiante ya tiene un registro en este grupo (" + previa.getEstado() + ")");
        }
        g.sumarInscrito();
        Inscripcion i = inscripciones.save(new Inscripcion(e, g, casoEspecial));
        auditoria.registrar("INSCRIBIR", "GRUPO", g.getId(), e.getRegistro() + " en " + g.getMateria().getSigla() + " " + g.getCodigo()
                + (casoEspecial ? " (caso especial: " + motivo + ")" : ""));
        return aDto(i);
    }

    @Transactional
    public InscripcionDto retirar(Integer idInscripcion, String motivo) {
        Inscripcion i = inscripciones.findById(idInscripcion).orElseThrow(() -> new RecursoNoEncontradoException("Inscripción", idInscripcion));
        if (!i.activa()) throw new NegocioException(HttpStatus.CONFLICT, "La inscripción no está activa");
        Grupo g = grupos.bloquear(i.getGrupo().getId()).orElseThrow();
        if (g.getPeriodo().estaCerrado()) throw new NegocioException(HttpStatus.CONFLICT, "El periodo está cerrado");
        i.retirar();
        g.restarInscrito();
        auditoria.registrar("RETIRAR", "GRUPO", g.getId(), i.getEstudiante().getRegistro() + ". Motivo: " + motivo);
        return aDto(i);
    }

    /** Cambio de grupo (trámite "Cambio de grupo"): misma materia y periodo, con cupo en el destino. */
    @Transactional
    public InscripcionDto cambiarGrupo(Integer idInscripcion, Integer idGrupoDestino, String motivo) {
        Inscripcion actual = inscripciones.findById(idInscripcion).orElseThrow(() -> new RecursoNoEncontradoException("Inscripción", idInscripcion));
        if (!actual.activa()) throw new NegocioException(HttpStatus.CONFLICT, "La inscripción no está activa");
        Grupo origen = grupos.bloquear(actual.getGrupo().getId()).orElseThrow();
        Grupo destino = grupos.bloquear(idGrupoDestino).orElseThrow(() -> new RecursoNoEncontradoException("Grupo", idGrupoDestino));
        if (origen.getId().equals(destino.getId())) throw new NegocioException("El grupo de destino es el mismo");
        if (!origen.getMateria().getId().equals(destino.getMateria().getId()) || !origen.getPeriodo().getId().equals(destino.getPeriodo().getId())) {
            throw new NegocioException("El cambio de grupo debe ser a la misma materia y periodo");
        }
        if (!destino.tieneCupo()) throw new NegocioException(HttpStatus.CONFLICT, "El grupo de destino no tiene cupo");
        actual.retirar();
        origen.restarInscrito();
        destino.sumarInscrito();
        Inscripcion nueva = inscripciones.save(new Inscripcion(actual.getEstudiante(), destino, true));
        auditoria.registrar("CAMBIO_GRUPO", "GRUPO", destino.getId(), actual.getEstudiante().getRegistro() + ": "
                + origen.getMateria().getSigla() + " " + origen.getCodigo() + " -> " + destino.getCodigo() + ". Motivo: " + motivo);
        return aDto(nueva);
    }

    private void validarInscripcion(Grupo g, Estudiante e, boolean casoEspecial, String motivo) {
        PeriodoAcademico p = g.getPeriodo();
        if (!PeriodoAcademico.ABIERTO.equals(p.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El periodo " + p.nombre() + " no está abierto para inscripciones");
        }
        if (!p.inscripcionAbierta(LocalDate.now()) && !casoEspecial) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "Las inscripciones cerraron el " + p.getFechaCierreInscripcion() + "; regístrela como caso especial (adición fuera de fecha)");
        }
        if (casoEspecial && (motivo == null || motivo.isBlank())) {
            throw new NegocioException("Indique el motivo o el trámite que respalda el caso especial");
        }
        if (!e.getPlan().equals(g.getMateria().getPlan())) {
            throw new NegocioException("La materia pertenece al plan " + g.getMateria().getPlan() + " y el estudiante está en el plan " + e.getPlan());
        }
        if (!ESTADOS_INSCRIBIBLES.contains(e.getEstado())) {
            throw new NegocioException("El estudiante está en estado " + e.getEstado() + " y no puede inscribirse");
        }
        if (inscripciones.inscritoEnMateria(e.getId(), g.getMateria().getId(), p.getId())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El estudiante ya está inscrito en " + g.getMateria().getSigla() + " este periodo");
        }
        if (!g.tieneCupo()) {
            throw new NegocioException(HttpStatus.CONFLICT, "El grupo " + g.getCodigo() + " no tiene cupo disponible (" + g.getCupo() + ")");
        }
    }

    private static GrupoDto aDto(Grupo g, List<String> docentes) {
        Materia m = g.getMateria();
        return new GrupoDto(g.getId(), m.getId(), m.getSigla(), m.getNombre(), m.getPlan(), m.getNivel(), g.getPeriodo().getId(),
                g.getPeriodo().nombre(), g.getCodigo(), g.getCupo(), g.getInscritos(), g.getCupo() - g.getInscritos(),
                g.getTurno(), g.getHorario(), g.getAula(), docentes);
    }

    private static InscripcionDto aDto(Inscripcion i) {
        Estudiante e = i.getEstudiante();
        return new InscripcionDto(i.getId(), e.getId(), e.getRegistro(), e.nombreCompleto(), e.getPlan(), i.getFechaInscripcion(),
                i.getEstado(), i.isEsCasoEspecial());
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
