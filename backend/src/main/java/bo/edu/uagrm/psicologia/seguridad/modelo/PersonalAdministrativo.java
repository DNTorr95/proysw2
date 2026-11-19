package bo.edu.uagrm.psicologia.seguridad.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Datos de cargo del personal (Director, Coordinador, Apoyo Profesional). Comparte la PK con persona. */
@Entity
@Table(name = "personal_administrativo")
public class PersonalAdministrativo {

    @Id
    @Column(name = "id_persona")
    private Integer idPersona;

    @Column(name = "cargo", nullable = false, length = 100)
    private String cargo;

    @Column(name = "area", length = 100)
    private String area;

    @Column(name = "fecha_designacion")
    private LocalDate fechaDesignacion;

    @Column(name = "resolucion_designacion", length = 100)
    private String resolucionDesignacion;

    protected PersonalAdministrativo() {
    }

    public PersonalAdministrativo(Integer idPersona, String cargo, String area) {
        this.idPersona = idPersona;
        this.cargo = cargo;
        this.area = area;
        this.fechaDesignacion = LocalDate.now();
    }

    public void actualizar(String cargo, String area) {
        this.cargo = cargo;
        this.area = area;
    }

    public void designar(String resolucion, LocalDate fecha) {
        this.resolucionDesignacion = resolucion;
        this.fechaDesignacion = fecha;
    }

    public Integer getIdPersona() { return idPersona; }
    public String getCargo() { return cargo; }
    public String getArea() { return area; }
    public LocalDate getFechaDesignacion() { return fechaDesignacion; }
    public String getResolucionDesignacion() { return resolucionDesignacion; }
}
