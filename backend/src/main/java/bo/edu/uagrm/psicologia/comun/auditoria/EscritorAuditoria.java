package bo.edu.uagrm.psicologia.comun.auditoria;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/**
 * Escribe la bitácora con un pool de conexiones PROPIO y autocommit.
 * <p>
 * Motivo: la auditoría debe persistir aunque la operación principal se revierta (p. ej. un intento de
 * login fallido). Hacerlo con una transacción anidada (REQUIRES_NEW) sobre el pool principal provoca un
 * bloqueo mutuo bajo carga: con N peticiones concurrentes, las N conexiones quedan tomadas por las
 * transacciones de negocio y cada una espera otra conexión para auditar. Con un pool aparte, las
 * transacciones de negocio nunca esperan por la auditoría.
 */
@Component
public class EscritorAuditoria implements DisposableBean {

    private static final String SQL = """
            insert into bitacora_auditoria (id_usuario, accion, entidad, id_entidad, fecha_hora, direccion_ip, detalle)
            values (?, ?, ?, ?, ?, ?, ?)""";

    private final HikariDataSource fuente;
    private final JdbcTemplate jdbc;

    public EscritorAuditoria(DataSourceProperties props) {
        this.fuente = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        fuente.setPoolName("auditoria");
        fuente.setMaximumPoolSize(5);
        fuente.setMinimumIdle(1);
        fuente.setAutoCommit(true);
        this.jdbc = new JdbcTemplate(fuente);
    }

    public void escribir(Integer idUsuario, String accion, String entidad, String idEntidad, String ip, String detalle) {
        jdbc.update(SQL, ps -> {
            if (idUsuario == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, idUsuario);
            ps.setString(2, accion);
            ps.setString(3, entidad);
            ps.setString(4, idEntidad);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, ip);
            ps.setString(7, detalle);
        });
    }

    @Override
    public void destroy() {
        fuente.close();
    }
}
