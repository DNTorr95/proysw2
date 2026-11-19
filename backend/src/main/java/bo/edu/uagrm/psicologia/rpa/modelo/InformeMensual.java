package bo.edu.uagrm.psicologia.rpa.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Informe generado por una ejecución RPA (CU-34). Uno por ejecución; nace en PENDIENTE_REVISION. */
@Entity
@Table(name = "informe_mensual")
public class InformeMensual {

    public static final String PENDIENTE_REVISION = "PENDIENTE_REVISION";
    public static final String APROBADO = "APROBADO";
    public static final String DEVUELTO = "DEVUELTO";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_informe")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ejecucion", nullable = false, unique = true)
    private EjecucionRpa ejecucion;

    @Column(name = "id_plantilla", nullable = false)
    private Integer idPlantilla;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(name = "total_recibidos", nullable = false)
    private int totalRecibidos;

    @Column(name = "total_emitidos", nullable = false)
    private int totalEmitidos;

    @Column(name = "ruta_pdf", nullable = false, length = 500)
    private String rutaPdf;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion = LocalDateTime.now();

    @Column(name = "estado_revision", nullable = false, length = 30)
    private String estadoRevision = PENDIENTE_REVISION;

    @Column(name = "observaciones_revision", columnDefinition = "text")
    private String observacionesRevision;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @OneToMany(mappedBy = "informe", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<DetalleInforme> detalles = new ArrayList<>();

    protected InformeMensual() {
    }

    public InformeMensual(EjecucionRpa ejecucion, Integer idPlantilla, int totalRecibidos, int totalEmitidos,
                          String rutaPdf, String hashSha256) {
        this.ejecucion = ejecucion;
        this.idPlantilla = idPlantilla;
        this.periodoDesde = ejecucion.getPeriodoDesde();
        this.periodoHasta = ejecucion.getPeriodoHasta();
        this.totalRecibidos = totalRecibidos;
        this.totalEmitidos = totalEmitidos;
        this.rutaPdf = rutaPdf;
        this.hashSha256 = hashSha256;
    }

    public void aprobar() {
        this.estadoRevision = APROBADO;
        this.fechaRevision = LocalDateTime.now();
    }

    public void devolver(String observaciones) {
        this.estadoRevision = DEVUELTO;
        this.observacionesRevision = observaciones;
        this.fechaRevision = LocalDateTime.now();
    }

    public boolean pendienteDeRevision() {
        return PENDIENTE_REVISION.equals(estadoRevision);
    }

    public Integer getId() { return id; }
    public EjecucionRpa getEjecucion() { return ejecucion; }
    public Integer getIdPlantilla() { return idPlantilla; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public int getTotalRecibidos() { return totalRecibidos; }
    public int getTotalEmitidos() { return totalEmitidos; }
    public String getRutaPdf() { return rutaPdf; }
    public String getHashSha256() { return hashSha256; }
    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; }
    public String getEstadoRevision() { return estadoRevision; }
    public String getObservacionesRevision() { return observacionesRevision; }
    public LocalDateTime getFechaRevision() { return fechaRevision; }
    public List<DetalleInforme> getDetalles() { return detalles; }
}
