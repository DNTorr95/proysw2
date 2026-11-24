package bo.edu.uagrm.psicologia.reportes.alertas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ModeloPrediccionRepository extends JpaRepository<ModeloPrediccion, Integer> {

    Optional<ModeloPrediccion> findByVigenteTrue();

    List<ModeloPrediccion> findAllByOrderByVersionDesc();

    @Query("select coalesce(max(m.version), 0) from ModeloPrediccion m")
    int ultimaVersion();
}
