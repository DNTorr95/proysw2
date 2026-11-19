package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.PlantillaDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlantillaDocumentoRepository extends JpaRepository<PlantillaDocumento, Integer> {

    Optional<PlantillaDocumento> findFirstByTipoAndVigenteTrueOrderByIdDesc(String tipo);
}
