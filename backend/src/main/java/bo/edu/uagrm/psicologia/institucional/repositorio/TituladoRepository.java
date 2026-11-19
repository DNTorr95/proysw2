package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.Titulado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TituladoRepository extends JpaRepository<Titulado, Integer> {

    boolean existsByEstudianteId(Integer idEstudiante);

    java.util.List<Titulado> findAllByOrderByGestionDescFechaDefensaDesc();
}
