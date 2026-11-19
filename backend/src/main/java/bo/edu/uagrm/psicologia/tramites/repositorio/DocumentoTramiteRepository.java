package bo.edu.uagrm.psicologia.tramites.repositorio;

import bo.edu.uagrm.psicologia.tramites.modelo.DocumentoTramite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoTramiteRepository extends JpaRepository<DocumentoTramite, Integer> {

    List<DocumentoTramite> findByArchivoId(Integer idArchivo);

    boolean existsByArchivoId(Integer idArchivo);
}
