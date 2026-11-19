package bo.edu.uagrm.psicologia.documental.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Registro de seguimiento de un oficio (CU-06). */
@Entity
@Table(name = "seguimiento_correspondencia")
public class SeguimientoCorrespondencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_seguimiento")
    private Integer id;

    @Column(name = "id_correspondencia", nullable = false)
    private Integer idCorrespondencia;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    protected SeguimientoCorrespondencia() {
    }

    public SeguimientoCorrespondencia(Integer idCorrespondencia, String estado, String observacion) {
        this.idCorrespondencia = idCorrespondencia;
        this.estado = estado;
        this.observacion = observacion;
    }

    public Integer getId() { return id; }
    public Integer getIdCorrespondencia() { return idCorrespondencia; }
    public LocalDateTime getFecha() { return fecha; }
    public String getEstado() { return estado; }
    public String getObservacion() { return observacion; }
}
