package bo.edu.uagrm.psicologia.documental.repositorio;

import bo.edu.uagrm.psicologia.documental.modelo.Expediente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpedienteRepository extends JpaRepository<Expediente, Integer> {

    @Query(value = "select nextval('seq_expediente_codigo')", nativeQuery = true)
    long siguienteNumero();

    @Query("""
            select x from Expediente x left join x.estudiante e left join e.persona p
            where (:estado is null or x.estado = :estado)
              and (:texto is null
                   or lower(x.codigo) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(x.titulo) like lower(concat('%', cast(:texto as string), '%'))
                   or e.registro like concat('%', cast(:texto as string), '%')
                   or lower(concat(p.nombres, ' ', p.apellidos)) like lower(concat('%', cast(:texto as string), '%')))
            """)
    Page<Expediente> buscar(@Param("texto") String texto, @Param("estado") String estado, Pageable pageable);
}
