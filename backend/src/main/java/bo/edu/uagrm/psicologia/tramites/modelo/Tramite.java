package bo.edu.uagrm.psicologia.tramites.modelo;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.documental.modelo.Expediente;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Trámite o solicitud estudiantil (CU-09).
 * El estado SOLO cambia a través de la máquina de estados (tramites.maquina.MaquinaEstadosTramite):
 * no existe un "setEstado" público.
 */
@Entity
@Table(name = "tramite")
public class Tramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tramite")
    private Integer id;

    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_tramite", nullable = false)
    private TipoTramite tipoTramite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_expediente")
    private Expediente expediente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_registro", nullable = false)
    private Usuario usuarioRegistro;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDateTime fechaRecepcion = LocalDateTime.now();

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "estado_actual", nullable = false, length = 30)
    private String estadoActual = EstadoTramite.RECEPCIONADO;

    @Column(name = "prioridad", nullable = false, length = 10)
    private String prioridad = "MEDIA";

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @OneToMany(mappedBy = "tramite", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<DocumentoTramite> documentos = new ArrayList<>();

    protected Tramite() {
    }

    public Tramite(String numero, TipoTramite tipo, Estudiante estudiante, Usuario usuarioRegistro,
                   String prioridad, String observaciones) {
        this.numero = numero;
        this.tipoTramite = tipo;
        this.estudiante = estudiante;
        this.usuarioRegistro = usuarioRegistro;
        this.prioridad = prioridad;
        this.observaciones = observaciones;
        this.fechaLimite = fechaRecepcion.toLocalDate().plusDays(tipo.getPlazoReferenciaDias());
    }

    /**
     * Aplica el resultado de una transición ya aceptada por la máquina de estados.
     * Uso exclusivo de MaquinaEstadosTramite.
     */
    public void aplicarTransicionAceptada(String nuevoEstado) {
        this.estadoActual = nuevoEstado;
        if (EstadoTramite.APROBADO.equals(nuevoEstado) || EstadoTramite.RECHAZADO.equals(nuevoEstado)) {
            this.fechaCierre = LocalDateTime.now();
        }
    }

    public void asignarExpediente(Expediente expediente) {
        this.expediente = expediente;
    }

    public boolean estaVencido() {
        return fechaCierre == null && !EstadoTramite.ARCHIVADO.equals(estadoActual) && LocalDate.now().isAfter(fechaLimite);
    }

    public Integer getId() { return id; }
    public String getNumero() { return numero; }
    public TipoTramite getTipoTramite() { return tipoTramite; }
    public Estudiante getEstudiante() { return estudiante; }
    public Expediente getExpediente() { return expediente; }
    public Usuario getUsuarioRegistro() { return usuarioRegistro; }
    public LocalDateTime getFechaRecepcion() { return fechaRecepcion; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public String getEstadoActual() { return estadoActual; }
    public String getPrioridad() { return prioridad; }
    public String getObservaciones() { return observaciones; }
    public List<DocumentoTramite> getDocumentos() { return documentos; }
}
