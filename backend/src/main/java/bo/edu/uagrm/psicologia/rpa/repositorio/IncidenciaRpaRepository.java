package bo.edu.uagrm.psicologia.rpa.repositorio;

import bo.edu.uagrm.psicologia.rpa.modelo.IncidenciaRpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidenciaRpaRepository extends JpaRepository<IncidenciaRpa, Integer> {

    List<IncidenciaRpa> findByIdEjecucionOrderByIdAsc(Integer idEjecucion);

    List<IncidenciaRpa> findByIdReejecucion(Integer idReejecucion);

    long countByIdEjecucion(Integer idEjecucion);
}
