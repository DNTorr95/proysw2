package bo.edu.uagrm.psicologia.rpa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Procedimiento automatizable (p. ej. "Informe mensual de correspondencia"). */
@Entity
@Table(name = "proceso_rpa")
public class ProcesoRpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proceso")
    private Integer id;

    @Column(name = "nombre", nullable = false, unique = true, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "tipo_proceso", nullable = false, length = 50)
    private String tipoProceso;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "tiempo_max_segundos", nullable = false)
    private Integer tiempoMaxSegundos;

    @Column(name = "tiempo_manual_minutos", nullable = false)
    private Integer tiempoManualMinutos;

    protected ProcesoRpa() {
    }

    public boolean estaHabilitado() {
        return "HABILITADO".equals(estado);
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getVersion() { return version; }
    public String getTipoProceso() { return tipoProceso; }
    public String getEstado() { return estado; }
    public Integer getTiempoMaxSegundos() { return tiempoMaxSegundos; }
    public Integer getTiempoManualMinutos() { return tiempoManualMinutos; }
}
