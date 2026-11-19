package bo.edu.uagrm.psicologia.academico.modelo;

import bo.edu.uagrm.psicologia.seguridad.modelo.Persona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Map;

/** Docente (especialización de persona). */
@Entity
@Table(name = "docente")
public class Docente {

    /**
     * Límite de horas asignables por semestre según la dedicación (CU-16). Las materias tienen 64–96 h
     * semestrales: equivale a unas 5–6 materias a tiempo completo, 4 a medio tiempo y 3 por horario.
     */
    public static final Map<String, Integer> LIMITE_HORAS = Map.of(
            "TIEMPO_COMPLETO", 480, "MEDIO_TIEMPO", 360, "HORARIO", 280);

    @Id
    @Column(name = "id_persona")
    private Integer id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_persona", insertable = false, updatable = false)
    private Persona persona;

    @Column(name = "codigo_docente", nullable = false, unique = true, length = 20)
    private String codigoDocente;

    @Column(name = "categoria", length = 30)
    private String categoria;

    @Column(name = "dedicacion", length = 30)
    private String dedicacion;

    @Column(name = "especialidad", length = 100)
    private String especialidad;

    protected Docente() {
    }

    public int limiteHoras() {
        return LIMITE_HORAS.getOrDefault(dedicacion == null ? "" : dedicacion, 160);
    }

    public Integer getId() { return id; }
    public Persona getPersona() { return persona; }
    public String getCodigoDocente() { return codigoDocente; }
    public String getCategoria() { return categoria; }
    public String getDedicacion() { return dedicacion; }
    public String getEspecialidad() { return especialidad; }
}
