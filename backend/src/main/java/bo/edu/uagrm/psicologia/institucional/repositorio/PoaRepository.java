package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.Poa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoaRepository extends JpaRepository<Poa, Integer> {

    java.util.Optional<Poa> findByGestion(Integer gestion);

    java.util.List<Poa> findAllByOrderByGestionDesc();
}
