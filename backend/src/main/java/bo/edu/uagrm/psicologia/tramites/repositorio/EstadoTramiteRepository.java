package bo.edu.uagrm.psicologia.tramites.repositorio;

import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoTramiteRepository extends JpaRepository<EstadoTramite, Integer> {
}
