package bo.edu.uagrm.psicologia.ocr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExtraccionOcrRepository extends JpaRepository<ExtraccionOcr, Integer> {

    Optional<ExtraccionOcr> findByIdArchivo(Integer idArchivo);

    List<ExtraccionOcr> findByIdArchivoIn(Collection<Integer> idsArchivo);
}
