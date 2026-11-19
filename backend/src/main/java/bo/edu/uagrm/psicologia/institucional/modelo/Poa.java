package bo.edu.uagrm.psicologia.institucional.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Plan Operativo Anual de una gestión (CU-19). */
@Entity
@Table(name = "poa")
public class Poa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_poa")
    private Integer id;

    @Column(name = "gestion", nullable = false, unique = true)
    private Integer gestion;

    @Column(name = "objetivo", nullable = false, columnDefinition = "text")
    private String objetivo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "FORMULADO";

    @OneToMany(mappedBy = "poa", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("fechaInicio, id")
    private List<ActividadPoa> actividades = new ArrayList<>();

    protected Poa() {
    }

    public Poa(int gestion, String objetivo) {
        this.gestion = gestion;
        this.objetivo = objetivo;
    }

    public void actualizar(String objetivo, String estado) {
        this.objetivo = objetivo;
        this.estado = estado;
    }

    /** Avance global: promedio simple del avance de las actividades (0 si no tiene). */
    public BigDecimal avance() {
        if (actividades.isEmpty()) return BigDecimal.ZERO.setScale(2);
        BigDecimal suma = actividades.stream().map(ActividadPoa::getAvance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(actividades.size()), 2, RoundingMode.HALF_UP);
    }

    public Integer getId() { return id; }
    public Integer getGestion() { return gestion; }
    public String getObjetivo() { return objetivo; }
    public String getEstado() { return estado; }
    public List<ActividadPoa> getActividades() { return actividades; }
}
