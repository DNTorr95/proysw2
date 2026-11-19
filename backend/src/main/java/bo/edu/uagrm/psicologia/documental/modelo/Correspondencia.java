package bo.edu.uagrm.psicologia.documental.modelo;

import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Oficio recibido o emitido por la Dirección de Carrera (CU-05). */
@Entity
@Table(name = "correspondencia")
public class Correspondencia {

    public enum Tipo { RECIBIDA, EMITIDA }

    /** Ciclo de vida del oficio. ARCHIVADA es final. */
    public enum Estado { REGISTRADA, DERIVADA, EN_TRAMITE, RESPONDIDA, ARCHIVADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correspondencia")
    private Integer id;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private Tipo tipo;

    @Column(name = "asunto", nullable = false, length = 300)
    private String asunto;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_destinatario", nullable = false)
    private Destinatario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private Estado estado = Estado.REGISTRADA;

    @Column(name = "autorizado", nullable = false)
    private boolean autorizado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    protected Correspondencia() {
    }

    public Correspondencia(String codigo, Tipo tipo, String asunto, String descripcion, LocalDate fechaDocumento,
                           Destinatario destinatario, Usuario usuarioRegistro) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.usuarioRegistro = usuarioRegistro;
        actualizar(asunto, descripcion, fechaDocumento, destinatario);
    }

    public void actualizar(String asunto, String descripcion, LocalDate fechaDocumento, Destinatario destinatario) {
        this.asunto = asunto;
        this.descripcion = descripcion;
        this.fechaDocumento = fechaDocumento;
        this.destinatario = destinatario;
    }

    public void cambiarEstado(Estado nuevo) {
        this.estado = nuevo;
    }

    public void autorizar(boolean valor) {
        this.autorizado = valor;
    }

    public Integer getId() { return id; }
    public String getCodigo() { return codigo; }
    public Tipo getTipo() { return tipo; }
    public String getAsunto() { return asunto; }
    public String getDescripcion() { return descripcion; }
    public LocalDate getFechaDocumento() { return fechaDocumento; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public Destinatario getDestinatario() { return destinatario; }
    public Estado getEstado() { return estado; }
    public boolean isAutorizado() { return autorizado; }
    public Usuario getUsuarioRegistro() { return usuarioRegistro; }
}
