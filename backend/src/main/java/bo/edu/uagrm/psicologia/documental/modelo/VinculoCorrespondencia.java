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

import java.time.LocalDateTime;

/** Vínculo entre un oficio original y su respuesta o reiteración (HU-06 / CU-06). */
@Entity
@Table(name = "vinculo_correspondencia")
public class VinculoCorrespondencia {

    public enum Tipo { RESPUESTA, REITERACION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vinculo")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_correspondencia", nullable = false)
    private Correspondencia original;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_vinculado", nullable = false)
    private Correspondencia vinculado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    protected VinculoCorrespondencia() {
    }

    public VinculoCorrespondencia(Correspondencia original, Correspondencia vinculado, Tipo tipo, Usuario usuario) {
        this.original = original;
        this.vinculado = vinculado;
        this.tipo = tipo;
        this.usuario = usuario;
    }

    public Integer getId() { return id; }
    public Correspondencia getOriginal() { return original; }
    public Correspondencia getVinculado() { return vinculado; }
    public Tipo getTipo() { return tipo; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
}
