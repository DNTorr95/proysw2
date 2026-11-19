package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.RegistroMigracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroMigracionRepository extends JpaRepository<RegistroMigracion, Integer> {

    java.util.List<RegistroMigracion> findAllByOrderByFechaDescIdDesc();

    java.util.List<RegistroMigracion> findByEstudianteIdOrderByFechaDesc(Integer idEstudiante);
}
