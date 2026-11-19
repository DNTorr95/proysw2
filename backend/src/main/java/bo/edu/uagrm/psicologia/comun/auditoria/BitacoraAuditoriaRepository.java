package bo.edu.uagrm.psicologia.comun.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BitacoraAuditoriaRepository
        extends JpaRepository<BitacoraAuditoria, Long>, JpaSpecificationExecutor<BitacoraAuditoria> {

    @Query("select distinct b.accion from BitacoraAuditoria b order by b.accion")
    List<String> acciones();

    @Query("select distinct b.entidad from BitacoraAuditoria b order by b.entidad")
    List<String> entidades();
}
