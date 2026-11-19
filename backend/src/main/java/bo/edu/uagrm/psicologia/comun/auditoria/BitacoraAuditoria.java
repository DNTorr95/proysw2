package bo.edu.uagrm.psicologia.comun.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** Registro de auditoría. Solo inserción: la entidad es inmutable para JPA. */
@Entity
@Immutable
@Table(name = "bitacora_auditoria")
public class BitacoraAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "accion", nullable = false, length = 80)
    private String accion;

    @Column(name = "entidad", nullable = false, length = 80)
    private String entidad;

    @Column(name = "id_entidad", length = 50)
    private String idEntidad;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "direccion_ip", length = 45)
    private String direccionIp;

    @Column(name = "detalle", columnDefinition = "text")
    private String detalle;

    protected BitacoraAuditoria() {
    }

    public BitacoraAuditoria(Integer idUsuario, String accion, String entidad, String idEntidad,
                             String direccionIp, String detalle) {
        this.idUsuario = idUsuario;
        this.accion = accion;
        this.entidad = entidad;
        this.idEntidad = idEntidad;
        this.direccionIp = direccionIp;
        this.detalle = detalle;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Integer getIdUsuario() { return idUsuario; }
    public String getAccion() { return accion; }
    public String getEntidad() { return entidad; }
    public String getIdEntidad() { return idEntidad; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getDireccionIp() { return direccionIp; }
    public String getDetalle() { return detalle; }
}
