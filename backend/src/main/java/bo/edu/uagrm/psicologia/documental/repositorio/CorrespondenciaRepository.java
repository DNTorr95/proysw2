package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CorrespondenciaRepository extends JpaRepository<Correspondencia, Integer>, JpaSpecificationExecutor<Correspondencia> {

    Optional<Correspondencia> findByCodigoIgnoreCase(String codigo);

    @Query(value = "select nextval('seq_correspondencia_codigo')", nativeQuery = true)
    long siguienteNumero();

    /** Oficios del periodo para el RPA: solo los autorizados (el tipo es opcional). */
    @Query("""
            select c from Correspondencia c join fetch c.destinatario
            where c.autorizado = true and c.fechaDocumento between :desde and :hasta
              and (:tipo is null or c.tipo = :tipo)
            order by c.fechaDocumento, c.codigo
            """)
    List<Correspondencia> autorizadasDelPeriodo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                                                @Param("tipo") Correspondencia.Tipo tipo);

    @Query("""
            select count(c) from Correspondencia c
            where c.autorizado = :autorizado and c.fechaDocumento between :desde and :hasta
              and (:tipo is null or c.tipo = :tipo)
            """)
    long contarDelPeriodo(@Param("autorizado") boolean autorizado, @Param("desde") LocalDate desde,
                          @Param("hasta") LocalDate hasta, @Param("tipo") Correspondencia.Tipo tipo);
}
