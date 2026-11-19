package bo.edu.uagrm.psicologia.rpa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Incidencia de una ejecución RPA (CU-36/CU-37). id_reejecucion apunta a la ejecución que la atendió. */
@Entity
@Table(name = "incidencia_rpa")
public class IncidenciaRpa {

    public static final String ABIERTA = "ABIERTA";
    public static final String EN_REEJECUCION = "EN_REEJECUCION";
    public static final String CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_incidencia")
    private Integer id;

    @Column(name = "id_ejecucion", nullable = false)
    private Integer idEjecucion;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado = ABIERTA;

    @Column(name = "id_reejecucion")
    private Integer idReejecucion;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    protected IncidenciaRpa() {
    }

    public IncidenciaRpa(Integer idEjecucion, String tipo, String descripcion) {
        this.idEjecucion = idEjecucion;
        this.tipo = tipo;
        this.descripcion = descripcion;
    }

    public void enviarAReejecucion(Integer idReejecucion) {
        this.estado = EN_REEJECUCION;
        this.idReejecucion = idReejecucion;
    }

    public void cerrar() {
        this.estado = CERRADA;
    }

    public Integer getId() { return id; }
    public Integer getIdEjecucion() { return idEjecucion; }
    public String getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getEstado() { return estado; }
    public Integer getIdReejecucion() { return idReejecucion; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
