package bo.edu.uagrm.psicologia.reportes.alertas;

import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Alerta administrativa (origen PLAZO) o predictiva (origen PREDICCION) sobre un trámite (CU-28). */
@Entity
@Table(name = "alerta")
public class Alerta {

    public static final String PLAZO = "PLAZO";
    public static final String PREDICCION = "PREDICCION";
    public static final String ABIERTA = "ABIERTA";
    public static final String EN_ATENCION = "EN_ATENCION";
    public static final String ATENDIDA = "ATENDIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tramite", nullable = false)
    private Tramite tramite;

    @Column(name = "origen", nullable = false, length = 20)
    private String origen;

    @Column(name = "nivel", nullable = false, length = 20)
    private String nivel;

    @Column(name = "probabilidad", precision = 5, scale = 2)
    private BigDecimal probabilidad;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ABIERTA;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion = LocalDateTime.now();

    @Column(name = "id_usuario_atencion")
    private Integer idUsuarioAtencion;

    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;

    protected Alerta() {
    }

    public Alerta(Tramite tramite, String origen, String nivel, BigDecimal probabilidad, String motivo) {
        this.tramite = tramite;
        this.origen = origen;
        actualizar(nivel, probabilidad, motivo);
    }

    public void actualizar(String nivel, BigDecimal probabilidad, String motivo) {
        this.nivel = nivel;
        this.probabilidad = probabilidad;
        this.motivo = motivo.length() > 200 ? motivo.substring(0, 200) : motivo;
    }

    public void atender(String nuevoEstado, Integer idUsuario) {
        this.estado = nuevoEstado;
        this.idUsuarioAtencion = idUsuario;
        this.fechaAtencion = LocalDateTime.now();
    }

    public boolean abierta() {
        return !ATENDIDA.equals(estado);
    }

    public Integer getId() { return id; }
    public Tramite getTramite() { return tramite; }
    public String getOrigen() { return origen; }
    public String getNivel() { return nivel; }
    public BigDecimal getProbabilidad() { return probabilidad; }
    public String getMotivo() { return motivo; }
    public String getEstado() { return estado; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public Integer getIdUsuarioAtencion() { return idUsuarioAtencion; }
    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
}
