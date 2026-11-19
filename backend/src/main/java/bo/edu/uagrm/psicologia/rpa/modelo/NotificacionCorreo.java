package bo.edu.uagrm.psicologia.rpa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Correo asociado a un informe (CU-35). Nace en BORRADOR y SOLO pasa a AUTORIZADO con la autorización
 * de una persona (id_usuario_autoriza + fecha_autorizacion); la base además lo exige con un CHECK.
 * Uno por informe: impide envíos duplicados.
 */
@Entity
@Table(name = "notificacion_correo")
public class NotificacionCorreo {

    public static final String BORRADOR = "BORRADOR";
    public static final String AUTORIZADO = "AUTORIZADO";
    public static final String ENVIADO = "ENVIADO";
    public static final String ERROR = "ERROR";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_informe", nullable = false, unique = true)
    private InformeMensual informe;

    @Column(name = "destinatarios", nullable = false, length = 1000)
    private String destinatarios;

    @Column(name = "asunto", nullable = false, length = 300)
    private String asunto;

    @Column(name = "cuerpo", nullable = false, columnDefinition = "text")
    private String cuerpo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = BORRADOR;

    @Column(name = "id_usuario_autoriza")
    private Integer idUsuarioAutoriza;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "mensaje_error", columnDefinition = "text")
    private String mensajeError;

    protected NotificacionCorreo() {
    }

    public NotificacionCorreo(InformeMensual informe, List<String> destinatarios, String asunto, String cuerpo) {
        this.informe = informe;
        editar(destinatarios, asunto, cuerpo);
    }

    public void editar(List<String> destinatarios, String asunto, String cuerpo) {
        this.destinatarios = String.join(",", destinatarios);
        this.asunto = asunto;
        this.cuerpo = cuerpo;
    }

    public void autorizar(Integer idUsuario) {
        this.estado = AUTORIZADO;
        this.idUsuarioAutoriza = idUsuario;
        this.fechaAutorizacion = LocalDateTime.now();
    }

    public void marcarEnviado() {
        this.estado = ENVIADO;
        this.fechaEnvio = LocalDateTime.now();
        this.mensajeError = null;
    }

    public void marcarError(String mensaje) {
        this.estado = ERROR;
        this.mensajeError = mensaje;
    }

    public void anular() {
        this.estado = ANULADO;
    }

    public boolean listoParaEnviar() {
        return (AUTORIZADO.equals(estado) || ERROR.equals(estado)) && idUsuarioAutoriza != null && fechaAutorizacion != null;
    }

    public List<String> listaDestinatarios() {
        return destinatarios == null || destinatarios.isBlank() ? List.of()
                : Arrays.stream(destinatarios.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Integer getId() { return id; }
    public InformeMensual getInforme() { return informe; }
    public String getDestinatarios() { return destinatarios; }
    public String getAsunto() { return asunto; }
    public String getCuerpo() { return cuerpo; }
    public String getEstado() { return estado; }
    public Integer getIdUsuarioAutoriza() { return idUsuarioAutoriza; }
    public LocalDateTime getFechaAutorizacion() { return fechaAutorizacion; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public String getMensajeError() { return mensajeError; }
}
