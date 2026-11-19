package bo.edu.uagrm.psicologia.academico.modelo;

import bo.edu.uagrm.psicologia.seguridad.modelo.Persona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Estudiante (especialización de persona). */
@Entity
@Table(name = "estudiante")
public class Estudiante {

    @Id
    @Column(name = "id_persona")
    private Integer id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_persona", insertable = false, updatable = false)
    private Persona persona;

    @Column(name = "registro", nullable = false, unique = true, length = 20)
    private String registro;

    @Column(name = "plan", nullable = false, length = 20)
    private String plan;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    protected Estudiante() {
    }

    public Integer getId() { return id; }
    public Persona getPersona() { return persona; }
    public String getRegistro() { return registro; }
    public String getPlan() { return plan; }
    public String getEstado() { return estado; }

    /** CU-15: el plan solo cambia al registrar una migración. */
    public void migrarAPlan(String planDestino) {
        this.plan = planDestino;
    }

    /** CU-17: al registrar la titulación el estudiante pasa a TITULADO. */
    public void cambiarEstado(String nuevo) {
        this.estado = nuevo;
    }

    public String nombreCompleto() {
        return persona.nombreCompleto();
    }
}
