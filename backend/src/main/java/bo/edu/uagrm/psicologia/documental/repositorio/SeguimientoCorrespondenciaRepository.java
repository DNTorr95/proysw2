package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.SeguimientoCorrespondencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeguimientoCorrespondenciaRepository extends JpaRepository<SeguimientoCorrespondencia, Integer> {

    List<SeguimientoCorrespondencia> findByIdCorrespondenciaOrderByFechaDescIdDesc(Integer idCorrespondencia);
}
