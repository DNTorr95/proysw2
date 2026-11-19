package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Catálogo de estados del trámite (cargado por el script complementario). */
@Entity
@Immutable
@Table(name = "estado_tramite")
public class EstadoTramite {

    public static final String RECEPCIONADO = "RECEPCIONADO";
    public static final String EN_REVISION = "EN_REVISION";
    public static final String OBSERVADO = "OBSERVADO";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String ARCHIVADO = "ARCHIVADO";

    @Id
    @Column(name = "id_estado")
    private Integer id;

    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(name = "es_final", nullable = false)
    private boolean esFinal;

    protected EstadoTramite() {
    }

    public EstadoTramite(Integer id, String nombre, boolean esFinal) {
        this.id = id;
        this.nombre = nombre;
        this.esFinal = esFinal;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public boolean isEsFinal() { return esFinal; }
}
