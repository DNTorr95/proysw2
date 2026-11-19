package bo.edu.uagrm.psicologia.institucional.modelo;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Registro de titulado (CU-17). Un estudiante se titula una sola vez. */
@Entity
@Table(name = "titulado")
public class Titulado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_titulado")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false, unique = true)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_trabajo")
    private TrabajoGrado trabajo;

    @Column(name = "gestion", nullable = false)
    private Integer gestion;

    @Column(name = "modalidad", nullable = false, length = 30)
    private String modalidad;

    @Column(name = "fecha_defensa")
    private LocalDate fechaDefensa;

    @Column(name = "nro_diploma", length = 30)
    private String nroDiploma;

    protected Titulado() {
    }

    public Titulado(Estudiante estudiante, TrabajoGrado trabajo, int gestion, String modalidad, LocalDate fechaDefensa, String nroDiploma) {
        this.estudiante = estudiante;
        actualizar(trabajo, gestion, modalidad, fechaDefensa, nroDiploma);
    }

    public void actualizar(TrabajoGrado trabajo, int gestion, String modalidad, LocalDate fechaDefensa, String nroDiploma) {
        this.trabajo = trabajo;
        this.gestion = gestion;
        this.modalidad = modalidad;
        this.fechaDefensa = fechaDefensa;
        this.nroDiploma = nroDiploma;
    }

    public Integer getId() { return id; }
    public Estudiante getEstudiante() { return estudiante; }
    public TrabajoGrado getTrabajo() { return trabajo; }
    public Integer getGestion() { return gestion; }
    public String getModalidad() { return modalidad; }
    public LocalDate getFechaDefensa() { return fechaDefensa; }
    public String getNroDiploma() { return nroDiploma; }
}
