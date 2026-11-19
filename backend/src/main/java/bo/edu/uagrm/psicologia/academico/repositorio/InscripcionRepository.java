package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    java.util.List<Inscripcion> findByGrupoIdOrderByEstudianteRegistroAsc(Integer idGrupo);

    java.util.Optional<Inscripcion> findByEstudianteIdAndGrupoId(Integer idEstudiante, Integer idGrupo);

    @org.springframework.data.jpa.repository.Query("""
            select count(i) > 0 from Inscripcion i
            where i.estudiante.id = :idEstudiante and i.grupo.materia.id = :idMateria
              and i.grupo.periodo.id = :idPeriodo and i.estado = 'INSCRITO'
            """)
    boolean inscritoEnMateria(@org.springframework.data.repository.query.Param("idEstudiante") Integer idEstudiante,
                              @org.springframework.data.repository.query.Param("idMateria") Integer idMateria,
                              @org.springframework.data.repository.query.Param("idPeriodo") Integer idPeriodo);
}
