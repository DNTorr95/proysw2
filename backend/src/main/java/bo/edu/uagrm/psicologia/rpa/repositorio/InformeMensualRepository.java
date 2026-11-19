package bo.edu.uagrm.psicologia.rpa.repositorio;

import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InformeMensualRepository extends JpaRepository<InformeMensual, Integer> {

    Optional<InformeMensual> findByEjecucionId(Integer idEjecucion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InformeMensual i where i.id = :id")
    Optional<InformeMensual> bloquear(@Param("id") Integer id);

    /** Informes vigentes (pendientes o aprobados) del mismo proceso, periodo y tipo: evita duplicados. */
    @Query("""
            select i from InformeMensual i join i.ejecucion e
            where e.proceso.id = :idProceso and i.periodoDesde = :desde and i.periodoHasta = :hasta
              and e.tipoCorrespondencia = :tipo and i.estadoRevision in :estados
            """)
    List<InformeMensual> vigentes(@Param("idProceso") Integer idProceso, @Param("desde") LocalDate desde,
                                  @Param("hasta") LocalDate hasta, @Param("tipo") String tipo,
                                  @Param("estados") Collection<String> estados);

    @Query("select i from InformeMensual i where (:estado is null or i.estadoRevision = :estado)")
    Page<InformeMensual> buscar(@Param("estado") String estado, Pageable pageable);

    long countByEstadoRevision(String estado);
}
