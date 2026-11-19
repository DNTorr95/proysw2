package bo.edu.uagrm.psicologia.seguridad.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

/** Rol institucional: DIRECTOR_CARRERA, COORDINADOR_CARRERA o APOYO_PROFESIONAL. */
@Entity
@Table(name = "rol")
public class Rol {

    public static final String DIRECTOR = "DIRECTOR_CARRERA";
    public static final String COORDINADOR = "COORDINADOR_CARRERA";
    public static final String APOYO = "APOYO_PROFESIONAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer id;

    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "id_rol"),
            inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<Permiso> permisos = new HashSet<>();

    protected Rol() {
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Set<Permiso> getPermisos() { return permisos; }

    public void reemplazarPermisos(Set<Permiso> nuevos) {
        permisos.clear();
        permisos.addAll(nuevos);
    }
}
