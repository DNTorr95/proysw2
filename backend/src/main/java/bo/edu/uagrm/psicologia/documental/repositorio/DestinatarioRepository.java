package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.Destinatario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DestinatarioRepository extends JpaRepository<Destinatario, Integer> {

    List<Destinatario> findAllByOrderByUnidadAscNombreAsc();
}
