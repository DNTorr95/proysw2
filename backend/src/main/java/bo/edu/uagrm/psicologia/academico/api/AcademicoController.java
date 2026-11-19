package bo.edu.uagrm.psicologia.academico.api;

import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.ActualizarGrupoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.AsignacionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.AsignacionRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.CambioGrupoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.CopiarGruposRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.DocenteDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.EstadoPeriodoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.FinalizarAsignacionRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.GrupoDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.GrupoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.InscribirRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.InscripcionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MateriaDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MateriaRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MigracionDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.MigracionRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.PeriodoDto;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.PeriodoRequest;
import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.ResumenPlanificacionDto;
import bo.edu.uagrm.psicologia.academico.servicio.MateriaGrupoService;
import bo.edu.uagrm.psicologia.academico.servicio.MigracionCargaService;
import bo.edu.uagrm.psicologia.academico.servicio.PlanificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CU-13 a CU-16: gestión académica (planificación, materias, grupos y cupos, migraciones, carga docente). */
@RestController
@RequestMapping("/api")
@Tag(name = "CU-13/CU-16 Gestión académica")
public class AcademicoController {

    private static final String CONSULTA = "hasAnyAuthority('CONSULTAR_ACADEMICO', 'GESTIONAR_PLANIFICACION', 'GESTIONAR_MATERIAS_GRUPOS', 'GESTIONAR_MIGRACIONES', 'GESTIONAR_CARGA_DOCENTE')";

    private final PlanificacionService planificacion;
    private final MateriaGrupoService materiasGrupos;
    private final MigracionCargaService migracionCarga;

    public AcademicoController(PlanificacionService planificacion, MateriaGrupoService materiasGrupos,
                               MigracionCargaService migracionCarga) {
        this.planificacion = planificacion;
        this.materiasGrupos = materiasGrupos;
        this.migracionCarga = migracionCarga;
    }

    public record MotivoRequest(@NotBlank(message = "El motivo es obligatorio") @Size(max = 300) String motivo) {
    }

    // ---------- CU-13 ----------

    @GetMapping("/periodos")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-13 Periodos académicos")
    public List<PeriodoDto> periodos() {
        return planificacion.listar();
    }

    @GetMapping("/periodos/{id}/resumen")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-13 Resumen de planificación: grupos, cupos, ocupación y grupos sin docente")
    public ResumenPlanificacionDto resumen(@PathVariable Integer id) {
        return planificacion.resumen(id);
    }

    @PostMapping("/periodos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_PLANIFICACION')")
    @Operation(summary = "CU-13 Planificar un nuevo periodo")
    public PeriodoDto crearPeriodo(@Valid @RequestBody PeriodoRequest req) {
        return planificacion.crear(req);
    }

    @PutMapping("/periodos/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_PLANIFICACION')")
    @Operation(summary = "CU-13 Actualizar fechas del periodo")
    public PeriodoDto actualizarPeriodo(@PathVariable Integer id, @Valid @RequestBody PeriodoRequest req) {
        return planificacion.actualizar(id, req);
    }

    @PatchMapping("/periodos/{id}/estado")
    @PreAuthorize("hasAuthority('GESTIONAR_PLANIFICACION')")
    @Operation(summary = "CU-13 Abrir o cerrar el periodo")
    public PeriodoDto estadoPeriodo(@PathVariable Integer id, @Valid @RequestBody EstadoPeriodoRequest req) {
        return planificacion.cambiarEstado(id, req.estado());
    }

    @PostMapping("/periodos/{id}/copia-grupos")
    @PreAuthorize("hasAuthority('GESTIONAR_PLANIFICACION')")
    @Operation(summary = "CU-13 Copiar la oferta de grupos de otro periodo")
    public PeriodoDto copiarGrupos(@PathVariable Integer id, @Valid @RequestBody CopiarGruposRequest req) {
        return planificacion.copiarGrupos(id, req.idPeriodoOrigen());
    }

    // ---------- CU-14 ----------

    @GetMapping("/materias")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-14 Materias (filtro por plan)")
    public List<MateriaDto> materias(@RequestParam(required = false) String plan) {
        return materiasGrupos.materias(plan);
    }

    @PostMapping("/materias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    public MateriaDto crearMateria(@Valid @RequestBody MateriaRequest req) {
        return materiasGrupos.crearMateria(req);
    }

    @PutMapping("/materias/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    public MateriaDto actualizarMateria(@PathVariable Integer id, @Valid @RequestBody MateriaRequest req) {
        return materiasGrupos.actualizarMateria(id, req);
    }

    @GetMapping("/grupos")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-14 Grupos del periodo con cupos, inscritos y docentes")
    public List<GrupoDto> grupos(@RequestParam Integer idPeriodo) {
        return materiasGrupos.grupos(idPeriodo);
    }

    @PostMapping("/grupos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    public GrupoDto crearGrupo(@Valid @RequestBody GrupoRequest req) {
        return materiasGrupos.crearGrupo(req);
    }

    @PutMapping("/grupos/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    @Operation(summary = "CU-14 Actualizar cupo, turno, horario y aula (el cupo no baja de los inscritos)")
    public GrupoDto actualizarGrupo(@PathVariable Integer id, @Valid @RequestBody ActualizarGrupoRequest req) {
        return materiasGrupos.actualizarGrupo(id, req);
    }

    @GetMapping("/grupos/{id}/inscripciones")
    @PreAuthorize(CONSULTA)
    public List<InscripcionDto> inscripciones(@PathVariable Integer id) {
        return materiasGrupos.inscripciones(id);
    }

    @PostMapping("/grupos/{id}/inscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    @Operation(summary = "CU-14 Inscribir estudiante (valida cupo, plan, fecha de cierre o caso especial)")
    public InscripcionDto inscribir(@PathVariable Integer id, @Valid @RequestBody InscribirRequest req) {
        return materiasGrupos.inscribir(id, req.idEstudiante(), req.casoEspecial(), req.motivo());
    }

    @PostMapping("/inscripciones/{id}/retiro")
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    public InscripcionDto retirar(@PathVariable Integer id, @Valid @RequestBody MotivoRequest req) {
        return materiasGrupos.retirar(id, req.motivo());
    }

    @PostMapping("/inscripciones/{id}/cambio-grupo")
    @PreAuthorize("hasAuthority('GESTIONAR_MATERIAS_GRUPOS')")
    @Operation(summary = "CU-14 Cambio de grupo (misma materia y periodo)")
    public InscripcionDto cambiarGrupo(@PathVariable Integer id, @Valid @RequestBody CambioGrupoRequest req) {
        return materiasGrupos.cambiarGrupo(id, req.idGrupoDestino(), req.motivo());
    }

    // ---------- CU-15 ----------

    @GetMapping("/migraciones")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-15 Registros de migración entre planes")
    public List<MigracionDto> migraciones(@RequestParam(required = false) Integer idEstudiante) {
        return migracionCarga.migraciones(idEstudiante);
    }

    @PostMapping("/migraciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_MIGRACIONES')")
    @Operation(summary = "CU-15 Registrar migración (actualiza el plan del estudiante)")
    public MigracionDto migrar(@Valid @RequestBody MigracionRequest req) {
        return migracionCarga.migrar(req);
    }

    // ---------- CU-16 ----------

    @GetMapping("/docentes")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-16 Docentes con carga horaria del periodo y su límite")
    public List<DocenteDto> docentes(@RequestParam(required = false) Integer idPeriodo) {
        return migracionCarga.docentes(idPeriodo);
    }

    @GetMapping("/docentes/{id}/antecedentes")
    @PreAuthorize(CONSULTA)
    @Operation(summary = "CU-16 Antecedentes de asignación y cobertura del docente")
    public List<AsignacionDto> antecedentes(@PathVariable Integer id) {
        return migracionCarga.antecedentes(id);
    }

    @GetMapping("/asignaciones")
    @PreAuthorize(CONSULTA)
    public List<AsignacionDto> asignaciones(@RequestParam Integer idPeriodo) {
        return migracionCarga.asignaciones(idPeriodo);
    }

    @PostMapping("/asignaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('GESTIONAR_CARGA_DOCENTE')")
    @Operation(summary = "CU-16 Asignar carga docente (valida límite por dedicación y un responsable por grupo)")
    public AsignacionDto asignar(@Valid @RequestBody AsignacionRequest req) {
        return migracionCarga.asignar(req);
    }

    @PostMapping("/asignaciones/{id}/finalizacion")
    @PreAuthorize("hasAuthority('GESTIONAR_CARGA_DOCENTE')")
    public AsignacionDto finalizar(@PathVariable Integer id, @Valid @RequestBody FinalizarAsignacionRequest req) {
        return migracionCarga.finalizar(id, req.fecha(), req.observacion());
    }
}
