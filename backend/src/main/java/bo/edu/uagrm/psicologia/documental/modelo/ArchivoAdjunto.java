package bo.edu.uagrm.psicologia.documental.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Documento digital del volumen documental (CU-07). Puede pertenecer a una correspondencia,
 * a un expediente o enlazarse a un trámite mediante documento_tramite.
 */
@Entity
@Table(name = "archivo_adjunto")
public class ArchivoAdjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_archivo")
    private Integer id;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "ruta", nullable = false, unique = true, length = 500)
    private String ruta;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga = LocalDateTime.now();

    @Column(name = "id_usuario_carga", nullable = false)
    private Integer idUsuarioCarga;

    @Column(name = "id_correspondencia")
    private Integer idCorrespondencia;

    @Column(name = "id_expediente")
    private Integer idExpediente;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    protected ArchivoAdjunto() {
    }

    public ArchivoAdjunto(String nombreArchivo, String tipoMime, long tamanoBytes, String hashSha256, String ruta,
                          Integer idUsuarioCarga, Integer idCorrespondencia, Integer idExpediente, String descripcion) {
        this.nombreArchivo = nombreArchivo;
        this.tipoMime = tipoMime;
        this.tamanoBytes = tamanoBytes;
        this.hashSha256 = hashSha256;
        this.ruta = ruta;
        this.idUsuarioCarga = idUsuarioCarga;
        this.idCorrespondencia = idCorrespondencia;
        this.idExpediente = idExpediente;
        this.descripcion = descripcion;
    }

    public void asignarExpediente(Integer idExpediente) {
        this.idExpediente = idExpediente;
    }

    public void actualizarDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getId() { return id; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getTipoMime() { return tipoMime; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public String getHashSha256() { return hashSha256; }
    public String getRuta() { return ruta; }
    public LocalDateTime getFechaCarga() { return fechaCarga; }
    public Integer getIdUsuarioCarga() { return idUsuarioCarga; }
    public Integer getIdCorrespondencia() { return idCorrespondencia; }
    public Integer getIdExpediente() { return idExpediente; }
    public String getDescripcion() { return descripcion; }
}
