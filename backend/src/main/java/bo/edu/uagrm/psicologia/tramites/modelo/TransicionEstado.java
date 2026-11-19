package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Transición permitida (origen --evento--> destino). La máquina de estados se configura desde esta tabla. */
@Entity
@Immutable
@Table(name = "transicion_estado")
public class TransicionEstado {

    @Id
    @Column(name = "id_transicion")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_estado_origen", nullable = false)
    private EstadoTramite origen;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_estado_destino", nullable = false)
    private EstadoTramite destino;

    @Column(name = "evento", nullable = false, length = 50)
    private String evento;

    @Column(name = "guarda", length = 200)
    private String guarda;

    protected TransicionEstado() {
    }

    public TransicionEstado(Integer id, EstadoTramite origen, EstadoTramite destino, String evento, String guarda) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.evento = evento;
        this.guarda = guarda;
    }

    public Integer getId() { return id; }
    public EstadoTramite getOrigen() { return origen; }
    public EstadoTramite getDestino() { return destino; }
    public String getEvento() { return evento; }
    public String getGuarda() { return guarda; }
}
