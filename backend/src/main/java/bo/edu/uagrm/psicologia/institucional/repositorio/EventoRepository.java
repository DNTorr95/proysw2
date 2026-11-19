package bo.edu.uagrm.psicologia.institucional.repositorio;

import bo.edu.uagrm.psicologia.institucional.modelo.Evento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, Integer> {

    java.util.List<Evento> findAllByOrderByFechaDesc();

    java.util.List<Evento> findByFechaBetweenOrderByFechaAsc(java.time.LocalDate desde, java.time.LocalDate hasta);
}
