package bo.edu.uagrm.psicologia.seguridad.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Transferencia (delegación) de la autoridad de Dirección (CU-04). Mientras está VIGENTE y dentro de sus fechas,
 * el usuario destino actúa con el rol y los permisos de DIRECTOR_CARRERA, sin modificar su rol propio.
 */
@Entity
@Table(name = "transferencia_autoridad")
public class TransferenciaAutoridad {

    public static final String VIGENTE = "VIGENTE";
    public static final String FINALIZADA = "FINALIZADA";
    public static final String REVOCADA = "REVOCADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transferencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_usuario_origen", nullable = false)
    private Usuario origen;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_usuario_destino", nullable = false)
    private Usuario destino;

    @Column(name = "motivo", nullable = false, length = 300)
    private String motivo;

    @Column(name = "resolucion", length = 100)
    private String resolucion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = VIGENTE;

    /** Documento de respaldo escaneado (resolución, memorándum), opcional. */
    @Column(name = "id_archivo_respaldo")
    private Integer idArchivoRespaldo;

    protected TransferenciaAutoridad() {
    }

    public TransferenciaAutoridad(Usuario origen, Usuario destino, String motivo, String resolucion,
                                  LocalDate inicio, LocalDate fin) {
        this.origen = origen;
        this.destino = destino;
        this.motivo = motivo;
        this.resolucion = resolucion;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
    }

    /** ¿Otorga autoridad en la fecha indicada? */
    public boolean activaEn(LocalDate fecha) {
        return VIGENTE.equals(estado) && !fecha.isBefore(fechaInicio) && (fechaFin == null || !fecha.isAfter(fechaFin));
    }

    public void revocar(LocalDate fecha) {
        this.estado = REVOCADA;
        this.fechaFin = fecha.isBefore(fechaInicio) ? fechaInicio : fecha;
    }

    public void finalizar() {
        this.estado = FINALIZADA;
    }

    public Integer getId() { return id; }
    public Usuario getOrigen() { return origen; }
    public Usuario getDestino() { return destino; }
    public String getMotivo() { return motivo; }
    public String getResolucion() { return resolucion; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public String getEstado() { return estado; }
    public Integer getIdArchivoRespaldo() { return idArchivoRespaldo; }

    public void adjuntarRespaldo(Integer idArchivo) {
        this.idArchivoRespaldo = idArchivo;
    }
}
