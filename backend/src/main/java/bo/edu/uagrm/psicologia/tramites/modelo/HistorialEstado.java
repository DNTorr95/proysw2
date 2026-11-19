package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/** Historial de transiciones de un trámite (solo inserción). */
@Entity
@Immutable
@Table(name = "historial_estado")
public class HistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Integer id;

    @Column(name = "id_tramite", nullable = false)
    private Integer idTramite;

    @Column(name = "id_transicion")
    private Integer idTransicion;

    @Column(name = "estado_origen", length = 30)
    private String estadoOrigen;

    @Column(name = "estado_destino", nullable = false, length = 30)
    private String estadoDestino;

    @Column(name = "evento", nullable = false, length = 50)
    private String evento;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    protected HistorialEstado() {
    }

    public HistorialEstado(Integer idTramite, Integer idTransicion, String estadoOrigen, String estadoDestino,
                           String evento, Integer idUsuario, String observacion) {
        this.idTramite = idTramite;
        this.idTransicion = idTransicion;
        this.estadoOrigen = estadoOrigen;
        this.estadoDestino = estadoDestino;
        this.evento = evento;
        this.idUsuario = idUsuario;
        this.observacion = observacion;
    }

    public Integer getId() { return id; }
    public Integer getIdTramite() { return idTramite; }
    public Integer getIdTransicion() { return idTransicion; }
    public String getEstadoOrigen() { return estadoOrigen; }
    public String getEstadoDestino() { return estadoDestino; }
    public String getEvento() { return evento; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getObservacion() { return observacion; }
}
