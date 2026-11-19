package bo.edu.uagrm.psicologia.seguridad.repositorio;

import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByNombreUsuarioIgnoreCase(String nombreUsuario);

    boolean existsByNombreUsuarioIgnoreCase(String nombreUsuario);

    long countByRolNombreAndEstado(String nombreRol, EstadoCuenta estado);

    @Query("""
            select u from Usuario u join u.persona p join u.rol r
            where (:texto is null
                   or lower(u.nombreUsuario) like lower(concat('%', cast(:texto as string), '%'))
                   or lower(concat(p.nombres, ' ', p.apellidos)) like lower(concat('%', cast(:texto as string), '%'))
                   or p.ci like concat('%', cast(:texto as string), '%'))
              and (:idRol is null or r.id = :idRol)
              and (:estado is null or u.estado = :estado)
            """)
    Page<Usuario> buscar(@Param("texto") String texto, @Param("idRol") Integer idRol,
                         @Param("estado") EstadoCuenta estado, Pageable pageable);
}
