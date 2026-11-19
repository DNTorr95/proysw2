package bo.edu.uagrm.psicologia.rpa.repositorio;

import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificacionCorreoRepository extends JpaRepository<NotificacionCorreo, Integer> {

    Optional<NotificacionCorreo> findByInformeId(Integer idInforme);

    /** Bloqueo de la fila durante el envío: dos aprobaciones simultáneas no pueden enviar dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificacionCorreo n where n.informe.id = :idInforme")
    Optional<NotificacionCorreo> bloquearPorInforme(@Param("idInforme") Integer idInforme);

    long countByEstado(String estado);

    List<NotificacionCorreo> findByEstado(String estado);
}
