package bo.edu.uagrm.psicologia.tramites.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Dictamen técnico del trámite (para tipos que lo requieren). */
@Entity
@Table(name = "dictamen")
public class Dictamen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dictamen")
    private Integer id;

    @Column(name = "id_tramite", nullable = false, unique = true)
    private Integer idTramite;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha = LocalDate.now();

    @Column(name = "resultado", nullable = false, length = 20)
    private String resultado;

    @Column(name = "fundamento", nullable = false, columnDefinition = "text")
    private String fundamento;

    protected Dictamen() {
    }

    public Dictamen(Integer idTramite, String resultado, String fundamento) {
        this.idTramite = idTramite;
        actualizar(resultado, fundamento);
    }

    public void actualizar(String resultado, String fundamento) {
        this.resultado = resultado;
        this.fundamento = fundamento;
        this.fecha = LocalDate.now();
    }

    public Integer getId() { return id; }
    public Integer getIdTramite() { return idTramite; }
    public LocalDate getFecha() { return fecha; }
    public String getResultado() { return resultado; }
    public String getFundamento() { return fundamento; }
}
