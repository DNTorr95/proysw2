package bo.edu.uagrm.psicologia.institucional.modelo;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Investigación o trabajo de grado (CU-18). */
@Entity
@Table(name = "trabajo_grado")
public class TrabajoGrado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_trabajo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @Column(name = "titulo", nullable = false, length = 300)
    private String titulo;

    @Column(name = "tutor", length = 150)
    private String tutor;

    @Column(name = "gestion", nullable = false)
    private Integer gestion;

    @Column(name = "palabras_clave", length = 300)
    private String palabrasClave;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_archivo")
    private ArchivoAdjunto archivo;

    protected TrabajoGrado() {
    }

    public TrabajoGrado(Estudiante estudiante, String titulo, String tutor, int gestion, String palabrasClave) {
        this.estudiante = estudiante;
        actualizar(titulo, tutor, gestion, palabrasClave);
    }

    public void actualizar(String titulo, String tutor, int gestion, String palabrasClave) {
        this.titulo = titulo;
        this.tutor = tutor;
        this.gestion = gestion;
        this.palabrasClave = palabrasClave;
    }

    public void adjuntar(ArchivoAdjunto archivo) {
        this.archivo = archivo;
    }

    public Integer getId() { return id; }
    public Estudiante getEstudiante() { return estudiante; }
    public String getTitulo() { return titulo; }
    public String getTutor() { return tutor; }
    public Integer getGestion() { return gestion; }
    public String getPalabrasClave() { return palabrasClave; }
    public ArchivoAdjunto getArchivo() { return archivo; }
}
