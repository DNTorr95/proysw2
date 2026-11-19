package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tipo_tramite")
public class TipoTramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_tramite")
    private Integer id;

    @Column(name = "nombre", nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Column(name = "plazo_referencia_dias", nullable = false)
    private Integer plazoReferenciaDias;

    @Column(name = "requiere_dictamen", nullable = false)
    private boolean requiereDictamen;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "tipoTramite", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<RequisitoTipoTramite> requisitos = new ArrayList<>();

    protected TipoTramite() {
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public Integer getPlazoReferenciaDias() { return plazoReferenciaDias; }
    public boolean isRequiereDictamen() { return requiereDictamen; }
    public boolean isActivo() { return activo; }
    public List<RequisitoTipoTramite> getRequisitos() { return requisitos; }
}
