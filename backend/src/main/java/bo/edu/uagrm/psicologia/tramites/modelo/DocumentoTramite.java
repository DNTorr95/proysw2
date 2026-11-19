package bo.edu.uagrm.psicologia.tramites.modelo;

import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Requisito o documento presentado en un trámite (CU-10). */
@Entity
@Table(name = "documento_tramite")
public class DocumentoTramite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tramite", nullable = false)
    private Tramite tramite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_requisito")
    private RequisitoTipoTramite requisito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_archivo")
    private ArchivoAdjunto archivo;

    @Column(name = "es_requisito", nullable = false)
    private boolean esRequisito;

    @Column(name = "presentado", nullable = false)
    private boolean presentado;

    @Column(name = "verificado", nullable = false)
    private boolean verificado;

    @Column(name = "observacion", length = 300)
    private String observacion;

    protected DocumentoTramite() {
    }

    /** Requisito del tipo de trámite. */
    public DocumentoTramite(Tramite tramite, RequisitoTipoTramite requisito, boolean presentado) {
        this.tramite = tramite;
        this.requisito = requisito;
        this.esRequisito = true;
        this.presentado = presentado;
    }

    /** Documento adicional (no requisito). */
    public DocumentoTramite(Tramite tramite, ArchivoAdjunto archivo, String observacion) {
        this.tramite = tramite;
        this.archivo = archivo;
        this.esRequisito = false;
        this.presentado = true;
        this.observacion = observacion;
    }

    public void marcarPresentado(boolean presentado) {
        this.presentado = presentado;
        if (!presentado) this.verificado = false;
    }

    public void adjuntar(ArchivoAdjunto archivo) {
        this.archivo = archivo;
        this.presentado = true;
    }

    public void verificar(boolean verificado, String observacion) {
        this.verificado = verificado;
        this.observacion = observacion;
    }

    /** Un requisito obligatorio está cumplido si fue presentado. */
    public boolean esObligatorioPendiente() {
        return esRequisito && requisito != null && requisito.isObligatorio() && !presentado;
    }

    public Integer getId() { return id; }
    public Tramite getTramite() { return tramite; }
    public RequisitoTipoTramite getRequisito() { return requisito; }
    public ArchivoAdjunto getArchivo() { return archivo; }
    public boolean isEsRequisito() { return esRequisito; }
    public boolean isPresentado() { return presentado; }
    public boolean isVerificado() { return verificado; }
    public String getObservacion() { return observacion; }
}
