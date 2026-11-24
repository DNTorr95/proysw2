package bo.edu.uagrm.psicologia.reportes.alertas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Versión entrenada del modelo predictivo de demoras (CU-29). El archivo vive en el volumen de servicios-ia. */
@Entity
@Table(name = "modelo_prediccion")
public class ModeloPrediccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo")
    private Integer id;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "algoritmo", nullable = false, length = 50)
    private String algoritmo;

    @Column(name = "fecha_entrenamiento", nullable = false)
    private LocalDateTime fechaEntrenamiento = LocalDateTime.now();

    @Column(name = "ejemplos", nullable = false)
    private Integer ejemplos;

    @Column(name = "demorados", nullable = false)
    private Integer demorados;

    @Column(name = "exactitud", precision = 4, scale = 3)
    private BigDecimal exactitud;

    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "vigente", nullable = false)
    private boolean vigente;

    protected ModeloPrediccion() {
    }

    public ModeloPrediccion(Integer version, String algoritmo, Integer ejemplos, Integer demorados, BigDecimal exactitud,
                            String hashSha256, Integer idUsuario) {
        this.version = version;
        this.algoritmo = algoritmo;
        this.ejemplos = ejemplos;
        this.demorados = demorados;
        this.exactitud = exactitud;
        this.hashSha256 = hashSha256;
        this.idUsuario = idUsuario;
        this.vigente = true;
    }

    public void retirar() {
        this.vigente = false;
    }

    public Integer getId() { return id; }
    public Integer getVersion() { return version; }
    public String getAlgoritmo() { return algoritmo; }
    public LocalDateTime getFechaEntrenamiento() { return fechaEntrenamiento; }
    public Integer getEjemplos() { return ejemplos; }
    public Integer getDemorados() { return demorados; }
    public BigDecimal getExactitud() { return exactitud; }
    public String getHashSha256() { return hashSha256; }
    public Integer getIdUsuario() { return idUsuario; }
    public boolean isVigente() { return vigente; }
}
