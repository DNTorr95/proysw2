package bo.edu.uagrm.psicologia.documental.modelo;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
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

/** Expediente administrativo (CU-08): agrupa trámites y documentos. */
@Entity
@Table(name = "expediente")
public class Expediente {

    public static final String ABIERTO = "ABIERTO";
    public static final String CERRADO = "CERRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_expediente")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estudiante")
    private Estudiante estudiante;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = ABIERTO;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDate fechaApertura = LocalDate.now();

    protected Expediente() {
    }

    public Expediente(String codigo, Estudiante estudiante, String titulo, String descripcion) {
        this.codigo = codigo;
        this.estudiante = estudiante;
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public void actualizar(String titulo, String descripcion) {
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public void cambiarEstado(String estado) {
        this.estado = estado;
    }

    public boolean estaAbierto() {
        return ABIERTO.equals(estado);
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public Estudiante getEstudiante() { return estudiante; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getEstado() { return estado; }
    public LocalDate getFechaApertura() { return fechaApertura; }
}
