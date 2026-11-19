package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {

    @Query("""
            select e from Estudiante e join e.persona p
            where :texto is null
               or e.registro like concat('%', cast(:texto as string), '%')
               or p.ci like concat('%', cast(:texto as string), '%')
               or lower(concat(p.nombres, ' ', p.apellidos)) like lower(concat('%', cast(:texto as string), '%'))
            """)
    Page<Estudiante> buscar(@Param("texto") String texto, Pageable pageable);
}
