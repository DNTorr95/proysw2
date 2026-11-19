package bo.edu.uagrm.psicologia.institucional.modelo;

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
import java.time.LocalDate;

/** Actividad del POA con su avance (0–100 %) y estado calculado. */
@Entity
@Table(name = "actividad_poa")
public class ActividadPoa {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String EN_CURSO = "EN_CURSO";
    public static final String CONCLUIDA = "CONCLUIDA";
    public static final String RETRASADA = "RETRASADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_poa", nullable = false)
    private Poa poa;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    @Column(name = "meta", nullable = false, length = 200)
    private String meta;

    @Column(name = "responsable", length = 150)
    private String responsable;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "avance", nullable = false, precision = 5, scale = 2)
    private BigDecimal avance = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    protected ActividadPoa() {
    }

    public ActividadPoa(Poa poa, String descripcion, String meta, String responsable, LocalDate inicio, LocalDate fin) {
        this.poa = poa;
        actualizar(descripcion, meta, responsable, inicio, fin);
    }

    public void actualizar(String descripcion, String meta, String responsable, LocalDate inicio, LocalDate fin) {
        this.descripcion = descripcion;
        this.meta = meta;
        this.responsable = responsable;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        recalcularEstado(LocalDate.now());
    }

    /** Registra un avance (seguimiento) y recalcula el estado. */
    public void registrarAvance(BigDecimal nuevo, LocalDate hoy) {
        this.avance = nuevo;
        recalcularEstado(hoy);
    }

    /** CONCLUIDA si llega a 100; RETRASADA si venció sin concluir; EN_CURSO si tiene avance o ya inició; si no, PENDIENTE. */
    public void recalcularEstado(LocalDate hoy) {
        if (avance.compareTo(BigDecimal.valueOf(100)) >= 0) estado = CONCLUIDA;
        else if (hoy.isAfter(fechaFin)) estado = RETRASADA;
        else if (avance.signum() > 0 || !hoy.isBefore(fechaInicio)) estado = EN_CURSO;
        else estado = PENDIENTE;
    }

    public Integer getId() { return id; }
    public Poa getPoa() { return poa; }
    public String getDescripcion() { return descripcion; }
    public String getMeta() { return meta; }
    public String getResponsable() { return responsable; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public BigDecimal getAvance() { return avance; }
    public String getEstado() { return estado; }
}
