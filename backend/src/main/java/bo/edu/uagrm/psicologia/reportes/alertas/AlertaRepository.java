package bo.edu.uagrm.psicologia.reportes.alertas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AlertaRepository extends JpaRepository<Alerta, Integer>, JpaSpecificationExecutor<Alerta> {

    List<Alerta> findByEstadoIn(Collection<String> estados);

    List<Alerta> findByOrigenAndEstadoIn(String origen, Collection<String> estados);

    long countByEstadoInAndNivel(Collection<String> estados, String nivel);

    long countByEstadoInAndOrigen(Collection<String> estados, String origen);
}
