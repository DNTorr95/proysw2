package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodoAcademicoRepository extends JpaRepository<PeriodoAcademico, Integer> {

    boolean existsByGestionAndSemestre(Integer gestion, Integer semestre);

    java.util.List<PeriodoAcademico> findAllByOrderByGestionDescSemestreDesc();

    java.util.Optional<PeriodoAcademico> findFirstByEstadoOrderByGestionDescSemestreDesc(String estado);
}
