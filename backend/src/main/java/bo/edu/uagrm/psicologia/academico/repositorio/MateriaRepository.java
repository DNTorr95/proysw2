package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.Materia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MateriaRepository extends JpaRepository<Materia, Integer> {

    boolean existsBySiglaAndPlan(String sigla, String plan);

    java.util.List<Materia> findAllByOrderByPlanAscNivelAscSiglaAsc();

    java.util.List<Materia> findByPlanOrderByNivelAscSiglaAsc(String plan);
}
