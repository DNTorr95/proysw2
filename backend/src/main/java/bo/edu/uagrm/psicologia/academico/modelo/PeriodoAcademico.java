package bo.edu.uagrm.psicologia.academico.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Periodo académico semestral (CU-13). Estados: PLANIFICADO → ABIERTO → CERRADO. */
@Entity
@Table(name = "periodo_academico")
public class PeriodoAcademico {

    public static final String PLANIFICADO = "PLANIFICADO";
    public static final String ABIERTO = "ABIERTO";
    public static final String CERRADO = "CERRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_periodo")
    private Integer id;

    @Column(name = "gestion", nullable = false)
    private Integer gestion;

    @Column(name = "semestre", nullable = false)
    private Integer semestre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "fecha_cierre_inscripcion")
    private LocalDate fechaCierreInscripcion;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PLANIFICADO;

    protected PeriodoAcademico() {
    }

    public PeriodoAcademico(int gestion, int semestre, LocalDate inicio, LocalDate fin, LocalDate cierreInscripcion) {
        this.gestion = gestion;
        this.semestre = semestre;
        actualizarFechas(inicio, fin, cierreInscripcion);
    }

    public void actualizarFechas(LocalDate inicio, LocalDate fin, LocalDate cierreInscripcion) {
        this.fechaInicio = inicio;
        this.fechaFin = fin;
        this.fechaCierreInscripcion = cierreInscripcion;
    }

    public void cambiarEstado(String nuevo) {
        this.estado = nuevo;
    }

    public boolean estaCerrado() {
        return CERRADO.equals(estado);
    }

    /** ¿Admite inscripciones regulares en la fecha indicada? */
    public boolean inscripcionAbierta(LocalDate fecha) {
        return ABIERTO.equals(estado) && (fechaCierreInscripcion == null || !fecha.isAfter(fechaCierreInscripcion));
    }

    public String nombre() {
        return gestion + "-" + semestre;
    }

    public Integer getId() { return id; }
    public Integer getGestion() { return gestion; }
    public Integer getSemestre() { return semestre; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public LocalDate getFechaCierreInscripcion() { return fechaCierreInscripcion; }
    public String getEstado() { return estado; }
}
