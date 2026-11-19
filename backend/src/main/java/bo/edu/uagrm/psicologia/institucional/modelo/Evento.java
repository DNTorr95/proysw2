package bo.edu.uagrm.psicologia.institucional.modelo;

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

/** Evento o taller institucional (CU-20), opcionalmente vinculado a una actividad del POA. */
@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "responsable", length = 150)
    private String responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actividad")
    private ActividadPoa actividad;

    protected Evento() {
    }

    public Evento(String nombre, String tipo, LocalDate fecha, String responsable, ActividadPoa actividad) {
        actualizar(nombre, tipo, fecha, responsable, actividad);
    }

    public void actualizar(String nombre, String tipo, LocalDate fecha, String responsable, ActividadPoa actividad) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.fecha = fecha;
        this.responsable = responsable;
        this.actividad = actividad;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public LocalDate getFecha() { return fecha; }
    public String getResponsable() { return responsable; }
    public ActividadPoa getActividad() { return actividad; }
}
