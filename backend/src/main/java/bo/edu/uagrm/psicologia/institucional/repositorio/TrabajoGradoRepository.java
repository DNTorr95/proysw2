package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.TrabajoGrado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrabajoGradoRepository extends JpaRepository<TrabajoGrado, Integer> {

    @org.springframework.data.jpa.repository.Query("""
            select t from TrabajoGrado t join t.estudiante e join e.persona p
            where (:gestion is null or t.gestion = :gestion)
              and (:texto is null
                   or lower(t.titulo) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(coalesce(t.palabrasClave, '')) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(coalesce(t.tutor, '')) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(concat(p.nombres, ' ', p.apellidos)) like lower(concat('%', cast(:texto as string), '%')))
            order by t.gestion desc, t.titulo
            """)
    java.util.List<TrabajoGrado> buscar(@org.springframework.data.repository.query.Param("texto") String texto,
                                        @org.springframework.data.repository.query.Param("gestion") Integer gestion);
}
