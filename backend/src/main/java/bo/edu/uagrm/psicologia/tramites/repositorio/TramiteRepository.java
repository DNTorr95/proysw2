package bo.edu.uagrm.psicologia.tramites.repositorio;

import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TramiteRepository extends JpaRepository<Tramite, Integer>, JpaSpecificationExecutor<Tramite> {

    @Query(value = "select nextval('seq_tramite_numero')", nativeQuery = true)
    long siguienteNumero();

    /** Bloqueo pesimista para que dos actuaciones simultáneas no se pisen. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tramite t where t.id = :id")
    Optional<Tramite> bloquear(@Param("id") Integer id);

    List<Tramite> findByExpedienteIdOrderByFechaRecepcionDesc(Integer idExpediente);
}
