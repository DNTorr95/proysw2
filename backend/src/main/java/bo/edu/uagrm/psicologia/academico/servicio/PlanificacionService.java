package bo.edu.uagrm.psicologia.academico.servicio;

import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.PeriodoDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.PeriodoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.ResumenPlanificacionDto;
import bo.edu.uagrm.psicologia.academico.modelo.Grupo;
import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import bo.edu.uagrm.psicologia.academico.repositorio.AsignacionDocenteRepository;
import bo.edu.uagrm.psicologia.academico.repositorio.GrupoRepository;
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

/** CU-13 Gestionar planificación académica semestral. */
@Service
public class PlanificacionService {

    /** Transiciones de estado permitidas del periodo. */
    private static final Map<String, Set<String>> TRANSICIONES = Map.of(
            PeriodoAcademico.PLANIFICADO, Set.of(PeriodoAcademico.ABIERTO),
            PeriodoAcademico.ABIERTO, Set.of(PeriodoAcademico.CERRADO, PeriodoAcademico.PLANIFICADO),
            PeriodoAcademico.CERRADO, Set.of());

    private final PeriodoAcademicoRepository periodos;
    private final GrupoRepository grupos;
    private final AsignacionDocenteRepository asignaciones;
    private final AuditoriaService auditoria;

    public PlanificacionService(PeriodoAcademicoRepository periodos, GrupoRepository grupos,
                                AsignacionDocenteRepository asignaciones, AuditoriaService auditoria) {
        this.periodos = periodos;
        this.grupos = grupos;
        this.asignaciones = asignaciones;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<PeriodoDto> listar() {
        return periodos.findAllByOrderByGestionDescSemestreDesc().stream()
                .map(p -> PeriodoDto.de(p, grupos.countByPeriodoId(p.getId()))).toList();
    }

    @Transactional
    public PeriodoDto crear(PeriodoRequest req) {
        validarFechas(req);
        if (periodos.existsByGestionAndSemestre(req.gestion(), req.semestre())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe el periodo " + req.gestion() + "-" + req.semestre());
        }
        PeriodoAcademico p = periodos.save(new PeriodoAcademico(req.gestion(), req.semestre(), req.fechaInicio(),
                req.fechaFin(), req.fechaCierreInscripcion()));
        auditoria.registrar("CREAR", "PERIODO_ACADEMICO", p.getId(), p.nombre());
        return PeriodoDto.de(p, 0);
    }

    @Transactional
    public PeriodoDto actualizar(Integer id, PeriodoRequest req) {
        PeriodoAcademico p = buscar(id);
        if (p.estaCerrado()) throw new NegocioException(HttpStatus.CONFLICT, "El periodo " + p.nombre() + " está cerrado");
        validarFechas(req);
        p.actualizarFechas(req.fechaInicio(), req.fechaFin(), req.fechaCierreInscripcion());
        auditoria.registrar("ACTUALIZAR", "PERIODO_ACADEMICO", p.getId(), p.nombre());
        return PeriodoDto.de(p, grupos.countByPeriodoId(id));
    }

    @Transactional
    public PeriodoDto cambiarEstado(Integer id, String estado) {
        PeriodoAcademico p = buscar(id);
        if (!TRANSICIONES.getOrDefault(p.getEstado(), Set.of()).contains(estado)) {
            throw new NegocioException(HttpStatus.CONFLICT, "No se puede pasar el periodo de " + p.getEstado() + " a " + estado);
        }
        if (PeriodoAcademico.ABIERTO.equals(estado)) {
            periodos.findFirstByEstadoOrderByGestionDescSemestreDesc(PeriodoAcademico.ABIERTO)
                    .filter(otro -> !otro.getId().equals(id))
                    .ifPresent(otro -> {
                        throw new NegocioException(HttpStatus.CONFLICT, "Ya hay un periodo abierto (" + otro.nombre() + "); ciérrelo primero");
                    });
            if (grupos.countByPeriodoId(id) == 0) {
                throw new NegocioException("No se puede abrir un periodo sin grupos planificados");
            }
        }
        String anterior = p.getEstado();
        p.cambiarEstado(estado);
        auditoria.registrar("CAMBIAR_ESTADO", "PERIODO_ACADEMICO", p.getId(), p.nombre() + ": " + anterior + " -> " + estado);
        return PeriodoDto.de(p, grupos.countByPeriodoId(id));
    }

    /** Planificación: copia los grupos (cupo, turno, horario, aula) de otro periodo a un periodo PLANIFICADO vacío. */
    @Transactional
    public PeriodoDto copiarGrupos(Integer idDestino, Integer idOrigen) {
        PeriodoAcademico destino = buscar(idDestino);
        PeriodoAcademico origen = buscar(idOrigen);
        if (!PeriodoAcademico.PLANIFICADO.equals(destino.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Solo se copian grupos a un periodo PLANIFICADO");
        }
        if (grupos.countByPeriodoId(idDestino) > 0) {
            throw new NegocioException(HttpStatus.CONFLICT, "El periodo de destino ya tiene grupos");
        }
        List<Grupo> base = grupos.findByPeriodoIdOrderByMateriaPlanAscMateriaNivelAscMateriaSiglaAscCodigoAsc(idOrigen);
        base.forEach(g -> grupos.save(new Grupo(g.getMateria(), destino, g.getCodigo(), g.getCupo(), g.getTurno(), g.getHorario(), g.getAula())));
        auditoria.registrar("COPIAR_GRUPOS", "PERIODO_ACADEMICO", destino.getId(), base.size() + " grupo(s) copiados desde " + origen.nombre());
        return PeriodoDto.de(destino, base.size());
    }

    @Transactional(readOnly = true)
    public ResumenPlanificacionDto resumen(Integer id) {
        PeriodoAcademico p = buscar(id);
        List<Grupo> lista = grupos.findByPeriodoIdOrderByMateriaPlanAscMateriaNivelAscMateriaSiglaAscCodigoAsc(id);
        Set<Integer> conDocente = asignaciones.findByGrupoPeriodoIdOrderByDocenteCodigoDocenteAsc(id).stream()
                .filter(a -> a.vigente(LocalDate.now())).map(a -> a.getGrupo().getId()).collect(Collectors.toSet());
        long cupo = lista.stream().mapToLong(Grupo::getCupo).sum();
        long inscritos = lista.stream().mapToLong(Grupo::getInscritos).sum();
        return new ResumenPlanificacionDto(PeriodoDto.de(p, lista.size()),
                lista.stream().map(g -> g.getMateria().getId()).distinct().count(), lista.size(), cupo, inscritos,
                lista.stream().filter(g -> !conDocente.contains(g.getId())).count(),
                lista.stream().filter(g -> !g.tieneCupo()).count(),
                cupo == 0 ? 0 : Math.round(inscritos * 1000.0 / cupo) / 10.0);
    }

    PeriodoAcademico buscar(Integer id) {
        return periodos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Periodo académico", id));
    }

    private static void validarFechas(PeriodoRequest req) {
        if (!req.fechaFin().isAfter(req.fechaInicio())) {
            throw new NegocioException("La fecha de fin debe ser posterior a la de inicio");
        }
        if (req.fechaCierreInscripcion() != null
                && (req.fechaCierreInscripcion().isBefore(req.fechaInicio()) || req.fechaCierreInscripcion().isAfter(req.fechaFin()))) {
            throw new NegocioException("El cierre de inscripciones debe estar dentro del periodo");
        }
        if (req.fechaInicio().getYear() != req.gestion()) {
            throw new NegocioException("La fecha de inicio no corresponde a la gestión " + req.gestion());
        }
    }
}
