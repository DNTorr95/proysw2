package bo.edu.uagrm.psicologia.tramites.repositorio;

import bo.edu.uagrm.psicologia.tramites.modelo.TipoTramite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TipoTramiteRepository extends JpaRepository<TipoTramite, Integer> {

    List<TipoTramite> findByActivoTrueOrderByNombre();
}
