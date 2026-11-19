package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ArchivoAdjuntoRepository extends JpaRepository<ArchivoAdjunto, Integer>, JpaSpecificationExecutor<ArchivoAdjunto> {

    List<ArchivoAdjunto> findByIdCorrespondenciaOrderByFechaCargaDesc(Integer idCorrespondencia);

    List<ArchivoAdjunto> findByIdExpedienteOrderByFechaCargaDesc(Integer idExpediente);

    long countByIdCorrespondencia(Integer idCorrespondencia);
}
