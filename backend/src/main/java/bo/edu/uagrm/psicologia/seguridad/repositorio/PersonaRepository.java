package bo.edu.uagrm.psicologia.seguridad.repositorio;

import bo.edu.uagrm.psicologia.seguridad.modelo.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Integer> {

    boolean existsByCi(String ci);
}
