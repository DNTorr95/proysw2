package bo.edu.uagrm.psicologia.academico.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Materia de un plan de estudios (148-1 o 148-2). */
@Entity
@Table(name = "materia")
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_materia")
    private Integer id;

    @Column(name = "sigla", nullable = false, length = 15)
    private String sigla;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "plan", nullable = false, length = 20)
    private String plan;

    @Column(name = "nivel", nullable = false)
    private Integer nivel;

    @Column(name = "horas", nullable = false)
    private Integer horas;

    protected Materia() {
    }

    public Materia(String sigla, String nombre, String plan, int nivel, int horas) {
        this.sigla = sigla;
        this.plan = plan;
        actualizar(nombre, nivel, horas);
    }

    public void actualizar(String nombre, int nivel, int horas) {
        this.nombre = nombre;
        this.nivel = nivel;
        this.horas = horas;
    }

    public Integer getId() { return id; }
    public String getSigla() { return sigla; }
    public String getNombre() { return nombre; }
    public String getPlan() { return plan; }
    public Integer getNivel() { return nivel; }
    public Integer getHoras() { return horas; }
}
