package bo.edu.uagrm.psicologia.seguridad.modelo;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Cuenta de acceso. Cada usuario tiene exactamente un rol. */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_persona", nullable = false, unique = true)
    private Persona persona;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 50)
    private String nombreUsuario;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCuenta estado = EstadoCuenta.ACTIVO;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    protected Usuario() {
    }

    public Usuario(Persona persona, Rol rol, String nombreUsuario, String contrasenaHash) {
        this.persona = persona;
        this.rol = rol;
        this.nombreUsuario = nombreUsuario;
        this.contrasenaHash = contrasenaHash;
    }

    /** Registra un intento fallido; devuelve true si la cuenta quedó bloqueada. */
    public boolean registrarIntentoFallido(int maximo) {
        intentosFallidos++;
        if (intentosFallidos >= maximo) {
            estado = EstadoCuenta.BLOQUEADO;
            return true;
        }
        return false;
    }

    public void registrarAccesoExitoso() {
        intentosFallidos = 0;
        ultimoAcceso = LocalDateTime.now();
    }

    public void cambiarEstado(EstadoCuenta nuevo) {
        this.estado = nuevo;
        if (nuevo == EstadoCuenta.ACTIVO) {
            intentosFallidos = 0;
        }
    }

    public void cambiarContrasena(String nuevoHash) {
        this.contrasenaHash = nuevoHash;
    }

    public void cambiarRol(Rol nuevo) {
        this.rol = nuevo;
    }

    public boolean estaActivo() {
        return estado == EstadoCuenta.ACTIVO;
    }

    public Integer getId() { return id; }
    public Persona getPersona() { return persona; }
    public Rol getRol() { return rol; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getContrasenaHash() { return contrasenaHash; }
    public EstadoCuenta getEstado() { return estado; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
