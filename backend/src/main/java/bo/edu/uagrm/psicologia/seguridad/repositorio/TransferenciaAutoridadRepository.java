package bo.edu.uagrm.psicologia.seguridad.repositorio;

import bo.edu.uagrm.psicologia.seguridad.modelo.TransferenciaAutoridad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaAutoridadRepository extends JpaRepository<TransferenciaAutoridad, Integer> {

    java.util.List<TransferenciaAutoridad> findAllByOrderByFechaInicioDescIdDesc();

    java.util.List<TransferenciaAutoridad> findByEstado(String estado);

    java.util.List<TransferenciaAutoridad> findByDestinoIdAndEstado(Integer idDestino, String estado);

    boolean existsByIdArchivoRespaldo(Integer idArchivo);
}
