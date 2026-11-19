package bo.edu.uagrm.psicologia.seguridad.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Permiso atómico. Los endpoints se protegen con @PreAuthorize("hasAuthority('CODIGO')"). */
@Entity
@Table(name = "permiso")
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 80)
    private String codigo;

    @Column(name = "modulo", nullable = false, length = 80)
    private String modulo;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    protected Permiso() {
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getModulo() { return modulo; }
    public String getDescripcion() { return descripcion; }
}
