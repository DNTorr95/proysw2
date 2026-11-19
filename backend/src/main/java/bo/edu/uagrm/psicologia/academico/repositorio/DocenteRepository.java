package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.Docente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocenteRepository extends JpaRepository<Docente, Integer> {

    java.util.List<Docente> findAllByOrderByCodigoDocenteAsc();
}
