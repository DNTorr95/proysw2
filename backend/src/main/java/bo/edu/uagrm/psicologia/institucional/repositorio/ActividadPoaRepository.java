package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActividadPoaRepository extends JpaRepository<ActividadPoa, Integer> {

    java.util.List<ActividadPoa> findAllByOrderByFechaInicioAsc();
}
