package bo.edu.uagrm.psicologia.tramites.repositorio;

import bo.edu.uagrm.psicologia.tramites.modelo.Dictamen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DictamenRepository extends JpaRepository<Dictamen, Integer> {

    Optional<Dictamen> findByIdTramite(Integer idTramite);
}
