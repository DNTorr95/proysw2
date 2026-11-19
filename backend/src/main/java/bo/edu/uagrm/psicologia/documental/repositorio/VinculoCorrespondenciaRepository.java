package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.VinculoCorrespondencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VinculoCorrespondenciaRepository extends JpaRepository<VinculoCorrespondencia, Integer> {

    /** Vínculos en los que participa el oficio, como original o como respuesta/reiteración. */
    @Query("""
            select v from VinculoCorrespondencia v
              join fetch v.original o join fetch v.vinculado x join fetch v.usuario
            where o.id = :id or x.id = :id
            order by v.fecha, v.id
            """)
    List<VinculoCorrespondencia> deOficio(@Param("id") Integer idCorrespondencia);

    /** ¿Ya existe un vínculo entre los dos oficios, en cualquier sentido? */
    @Query("""
            select count(v) > 0 from VinculoCorrespondencia v
            where (v.original.id = :a and v.vinculado.id = :b) or (v.original.id = :b and v.vinculado.id = :a)
            """)
    boolean existeEntre(@Param("a") Integer a, @Param("b") Integer b);
}
