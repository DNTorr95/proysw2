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

/** Carga docente: asignación de un docente a un grupo (TITULAR, INTERINO o COBERTURA) (CU-16). */
@Entity
@Table(name = "asignacion_docente")
public class AsignacionDocente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_asignacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_docente", nullable = false)
    private Docente docente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "horas_asignadas", nullable = false)
    private Integer horasAsignadas;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "observacion", length = 300)
    private String observacion;

    protected AsignacionDocente() {
    }

    public AsignacionDocente(Docente docente, Grupo grupo, String tipo, int horas, LocalDate inicio, LocalDate fin, String observacion) {
        this.docente = docente;
        this.grupo = grupo;
        this.tipo = tipo;
        this.horasAsignadas = horas;
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        this.observacion = observacion;
    }

    /** Finaliza la asignación (antecedente de cobertura) sin borrarla. */
    public void finalizar(LocalDate fecha, String observacion) {
        this.fechaFin = fecha;
        if (observacion != null) this.observacion = observacion;
    }

    public boolean vigente(LocalDate fecha) {
        return fechaFin == null || !fecha.isAfter(fechaFin);
    }

    public Integer getId() { return id; }
    public Docente getDocente() { return docente; }
    public Grupo getGrupo() { return grupo; }
    public String getTipo() { return tipo; }
    public Integer getHorasAsignadas() { return horasAsignadas; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public String getObservacion() { return observacion; }
}
