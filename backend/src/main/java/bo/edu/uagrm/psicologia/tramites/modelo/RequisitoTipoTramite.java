package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "requisito_tipo_tramite")
public class RequisitoTipoTramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requisito")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_tramite", nullable = false)
    private TipoTramite tipoTramite;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "obligatorio", nullable = false)
    private boolean obligatorio;

    protected RequisitoTipoTramite() {
    }

    public Integer getId() { return id; }
    public TipoTramite getTipoTramite() { return tipoTramite; }
    public String getDescripcion() { return descripcion; }
    public boolean isObligatorio() { return obligatorio; }
}
