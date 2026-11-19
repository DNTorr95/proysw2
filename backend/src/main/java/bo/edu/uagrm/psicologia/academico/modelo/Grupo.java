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

/** Grupo de una materia en un periodo, con cupo e inscritos (CU-14). */
@Entity
@Table(name = "grupo")
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_grupo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia", nullable = false)
    private Materia materia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_periodo", nullable = false)
    private PeriodoAcademico periodo;

    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "cupo", nullable = false)
    private Integer cupo;

    @Column(name = "inscritos", nullable = false)
    private Integer inscritos = 0;

    @Column(name = "turno", nullable = false, length = 10)
    private String turno;

    @Column(name = "horario", length = 100)
    private String horario;

    @Column(name = "aula", length = 30)
    private String aula;

    protected Grupo() {
    }

    public Grupo(Materia materia, PeriodoAcademico periodo, String codigo, int cupo, String turno, String horario, String aula) {
        this.materia = materia;
        this.periodo = periodo;
        this.codigo = codigo;
        actualizar(cupo, turno, horario, aula);
    }

    public void actualizar(int cupo, String turno, String horario, String aula) {
        this.cupo = cupo;
        this.turno = turno;
        this.horario = horario;
        this.aula = aula;
    }

    public boolean tieneCupo() {
        return inscritos < cupo;
    }

    public void sumarInscrito() {
        this.inscritos++;
    }

    public void restarInscrito() {
        if (inscritos > 0) this.inscritos--;
    }

    public Integer getId() { return id; }
    public Materia getMateria() { return materia; }
    public PeriodoAcademico getPeriodo() { return periodo; }
    public String getCodigo() { return codigo; }
    public Integer getCupo() { return cupo; }
    public Integer getInscritos() { return inscritos; }
    public String getTurno() { return turno; }
    public String getHorario() { return horario; }
    public String getAula() { return aula; }
}
