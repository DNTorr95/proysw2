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

/** Inscripción de un estudiante en un grupo. es_caso_especial: adición fuera de fecha o cambio de grupo. */
@Entity
@Table(name = "inscripcion")
public class Inscripcion {

    public static final String INSCRITO = "INSCRITO";
    public static final String RETIRADO = "RETIRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDate fechaInscripcion = LocalDate.now();

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = INSCRITO;

    @Column(name = "es_caso_especial", nullable = false)
    private boolean esCasoEspecial;

    protected Inscripcion() {
    }

    public Inscripcion(Estudiante estudiante, Grupo grupo, boolean esCasoEspecial) {
        this.estudiante = estudiante;
        this.grupo = grupo;
        this.esCasoEspecial = esCasoEspecial;
    }

    public void retirar() {
        this.estado = RETIRADO;
    }

    public boolean activa() {
        return INSCRITO.equals(estado);
    }

    public Integer getId() { return id; }
    public Estudiante getEstudiante() { return estudiante; }
    public Grupo getGrupo() { return grupo; }
    public LocalDate getFechaInscripcion() { return fechaInscripcion; }
    public String getEstado() { return estado; }
    public boolean isEsCasoEspecial() { return esCasoEspecial; }
}
