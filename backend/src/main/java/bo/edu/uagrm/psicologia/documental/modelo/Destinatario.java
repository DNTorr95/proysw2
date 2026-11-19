package bo.edu.uagrm.psicologia.documental.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Dependencia o persona de origen/destino de la correspondencia. */
@Entity
@Table(name = "destinatario")
public class Destinatario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_destinatario")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "unidad", nullable = false, length = 150)
    private String unidad;

    @Column(name = "institucion", nullable = false, length = 150)
    private String institucion;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Destinatario() {
    }

    public Destinatario(String nombre, String cargo, String unidad, String institucion, String correo) {
        actualizar(nombre, cargo, unidad, institucion, correo, true);
    }

    public void actualizar(String nombre, String cargo, String unidad, String institucion, String correo, boolean activo) {
        this.nombre = nombre;
        this.cargo = cargo;
        this.unidad = unidad;
        this.institucion = institucion == null || institucion.isBlank() ? "UAGRM" : institucion;
        this.correo = correo;
        this.activo = activo;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCargo() { return cargo; }
    public String getUnidad() { return unidad; }
    public String getInstitucion() { return institucion; }
    public String getCorreo() { return correo; }
    public boolean isActivo() { return activo; }
}
