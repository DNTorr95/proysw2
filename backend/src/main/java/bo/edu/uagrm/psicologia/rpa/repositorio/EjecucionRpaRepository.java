package bo.edu.uagrm.psicologia.rpa.repositorio;

import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EjecucionRpaRepository extends JpaRepository<EjecucionRpa, Integer>, JpaSpecificationExecutor<EjecucionRpa> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EjecucionRpa e where e.id = :id")
    Optional<EjecucionRpa> bloquear(@Param("id") Integer id);

    Optional<EjecucionRpa> findFirstByIdEjecucionOrigen(Integer idEjecucionOrigen);

    List<EjecucionRpa> findByEstadoIn(Collection<String> estados);

    boolean existsByProcesoIdAndPeriodoDesdeAndPeriodoHastaAndTipoCorrespondenciaAndEstadoIn(
            Integer idProceso, LocalDate desde, LocalDate hasta, String tipo, Collection<String> estados);
}
