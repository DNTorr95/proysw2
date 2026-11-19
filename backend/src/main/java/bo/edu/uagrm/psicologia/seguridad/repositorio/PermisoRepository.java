package bo.edu.uagrm.psicologia.seguridad.repositorio;

import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

    List<Permiso> findByCodigoIn(Collection<String> codigos);

    List<Permiso> findAllByOrderByModuloAscCodigoAsc();
}
