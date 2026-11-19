package bo.edu.uagrm.psicologia.academico.modelo;

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

/** Migración de un estudiante entre planes 148-1 y 148-2 (CU-15). */
@Entity
@Table(name = "registro_migracion")
public class RegistroMigracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_migracion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @Column(name = "plan_origen", nullable = false, length = 20)
    private String planOrigen;

    @Column(name = "plan_destino", nullable = false, length = 20)
    private String planDestino;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    protected RegistroMigracion() {
    }

    public RegistroMigracion(Estudiante estudiante, String planOrigen, String planDestino, LocalDate fecha, String observacion) {
        this.estudiante = estudiante;
        this.planOrigen = planOrigen;
        this.planDestino = planDestino;
        this.fecha = fecha;
        this.observacion = observacion;
    }

    public Integer getId() { return id; }
    public Estudiante getEstudiante() { return estudiante; }
    public String getPlanOrigen() { return planOrigen; }
    public String getPlanDestino() { return planDestino; }
    public LocalDate getFecha() { return fecha; }
    public String getObservacion() { return observacion; }
}
