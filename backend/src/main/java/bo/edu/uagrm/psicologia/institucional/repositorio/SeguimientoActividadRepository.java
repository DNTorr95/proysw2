package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.SeguimientoActividad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoActividadRepository extends JpaRepository<SeguimientoActividad, Integer> {

    java.util.List<SeguimientoActividad> findByIdActividadOrderByFechaDescIdDesc(Integer idActividad);
}
