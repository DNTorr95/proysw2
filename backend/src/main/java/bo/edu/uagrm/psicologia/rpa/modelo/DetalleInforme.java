package bo.edu.uagrm.psicologia.rpa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Totales del informe por categoría (FECHA, TIPO, ESTADO, DEPENDENCIA). */
@Entity
@Table(name = "detalle_informe")
public class DetalleInforme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_informe", nullable = false)
    private InformeMensual informe;

    @Column(name = "categoria", nullable = false, length = 20)
    private String categoria;

    @Column(name = "clave", nullable = false, length = 150)
    private String clave;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    protected DetalleInforme() {
    }

    public DetalleInforme(InformeMensual informe, String categoria, String clave, int cantidad) {
        this.informe = informe;
        this.categoria = categoria;
        this.clave = clave;
        this.cantidad = cantidad;
    }

    public Integer getId() { return id; }
    public String getCategoria() { return categoria; }
    public String getClave() { return clave; }
    public int getCantidad() { return cantidad; }
}
