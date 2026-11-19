package bo.edu.uagrm.psicologia.documental.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plantilla institucional (HTML) usada por el RPA para el informe PDF. */
@Entity
@Table(name = "plantilla_documento")
public class PlantillaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantilla")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "contenido_html", nullable = false, columnDefinition = "text")
    private String contenidoHtml;

    @Column(name = "vigente", nullable = false)
    private boolean vigente;

    protected PlantillaDocumento() {
    }

    public PlantillaDocumento(String nombre, String tipo, String version, String contenidoHtml) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.version = version;
        this.contenidoHtml = contenidoHtml;
        this.vigente = true;
    }

    public void desactivar() {
        this.vigente = false;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getVersion() { return version; }
    public String getContenidoHtml() { return contenidoHtml; }
    public boolean isVigente() { return vigente; }
}
