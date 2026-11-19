package bo.edu.uagrm.psicologia.ocr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Texto extraído por OCR de un archivo (CU-21). Queda validado=false hasta que una persona lo revisa (CU-22). */
@Entity
@Table(name = "extraccion_ocr")
public class ExtraccionOcr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_extraccion")
    private Integer id;

    @Column(name = "id_archivo", nullable = false, unique = true)
    private Integer idArchivo;

    @Column(name = "texto_extraido", nullable = false, columnDefinition = "text")
    private String textoExtraido;

    @Column(name = "confianza", nullable = false, precision = 5, scale = 2)
    private BigDecimal confianza;

    @Column(name = "validado", nullable = false)
    private boolean validado;

    @Column(name = "id_usuario_validacion")
    private Integer idUsuarioValidacion;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    protected ExtraccionOcr() {
    }

    public ExtraccionOcr(Integer idArchivo) {
        this.idArchivo = idArchivo;
    }

    /** Nuevo resultado del motor OCR: reemplaza el anterior y vuelve a requerir validación. */
    public void registrarExtraccion(String texto, BigDecimal confianza) {
        this.textoExtraido = texto;
        this.confianza = confianza;
        this.validado = false;
        this.idUsuarioValidacion = null;
        this.fechaValidacion = null;
    }

    public void validar(String textoCorregido, Integer idUsuario) {
        this.textoExtraido = textoCorregido;
        this.validado = true;
        this.idUsuarioValidacion = idUsuario;
        this.fechaValidacion = LocalDateTime.now();
    }

    public Integer getId() { return id; }
    public Integer getIdArchivo() { return idArchivo; }
    public String getTextoExtraido() { return textoExtraido; }
    public BigDecimal getConfianza() { return confianza; }
    public boolean isValidado() { return validado; }
    public Integer getIdUsuarioValidacion() { return idUsuarioValidacion; }
    public LocalDateTime getFechaValidacion() { return fechaValidacion; }
}
