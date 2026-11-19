package bo.edu.uagrm.psicologia.institucional.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Registro de avance de una actividad del POA, con evidencia y evento opcional. */
@Entity
@Table(name = "seguimiento_actividad")
public class SeguimientoActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_seguimiento")
    private Integer id;

    @Column(name = "id_actividad", nullable = false)
    private Integer idActividad;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "avance", nullable = false, precision = 5, scale = 2)
    private BigDecimal avance;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @Column(name = "evidencia", length = 500)
    private String evidencia;

    @Column(name = "id_evento")
    private Integer idEvento;

    protected SeguimientoActividad() {
    }

    public SeguimientoActividad(Integer idActividad, LocalDate fecha, BigDecimal avance, String observacion,
                                String evidencia, Integer idEvento) {
        this.idActividad = idActividad;
        this.fecha = fecha;
        this.avance = avance;
        this.observacion = observacion;
        this.evidencia = evidencia;
        this.idEvento = idEvento;
    }

    public Integer getId() { return id; }
    public Integer getIdActividad() { return idActividad; }
    public LocalDate getFecha() { return fecha; }
    public BigDecimal getAvance() { return avance; }
    public String getObservacion() { return observacion; }
    public String getEvidencia() { return evidencia; }
    public Integer getIdEvento() { return idEvento; }
}
