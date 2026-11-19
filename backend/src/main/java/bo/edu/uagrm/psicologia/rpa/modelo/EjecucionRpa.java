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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ejecución de un proceso RPA (CU-31). id_ejecucion_origen enlaza una reejecución con la ejecución anterior. */
@Entity
@Table(name = "ejecucion_rpa")
public class EjecucionRpa {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String FALLIDA = "FALLIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ejecucion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proceso", nullable = false)
    private ProcesoRpa proceso;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "id_usuario_revisor", nullable = false)
    private Integer idUsuarioRevisor;

    @Column(name = "id_ejecucion_origen")
    private Integer idEjecucionOrigen;

    @Column(name = "periodo_desde", nullable = false)
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta", nullable = false)
    private LocalDate periodoHasta;

    @Column(name = "tipo_correspondencia", nullable = false, length = 20)
    private String tipoCorrespondencia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(name = "registros_procesados", nullable = false)
    private int registrosProcesados;

    @Column(name = "registros_validos", nullable = false)
    private int registrosValidos;

    @Column(name = "errores_detectados", nullable = false)
    private int erroresDetectados;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "log_pasos")
    private String logPasos;

    @Column(name = "mensaje_error", columnDefinition = "text")
    private String mensajeError;

    protected EjecucionRpa() {
    }

    public EjecucionRpa(ProcesoRpa proceso, Integer idUsuario, Integer idUsuarioRevisor, Integer idEjecucionOrigen,
                        LocalDate desde, LocalDate hasta, String tipoCorrespondencia) {
        this.proceso = proceso;
        this.idUsuario = idUsuario;
        this.idUsuarioRevisor = idUsuarioRevisor;
        this.idEjecucionOrigen = idEjecucionOrigen;
        this.periodoDesde = desde;
        this.periodoHasta = hasta;
        this.tipoCorrespondencia = tipoCorrespondencia;
    }

    public void iniciarProceso(String logInicial) {
        this.estado = EN_PROCESO;
        this.logPasos = logInicial;
    }

    public void finalizar(String estadoFinal, int procesados, int validos, int errores, String logPasos, String mensajeError) {
        this.estado = estadoFinal;
        this.fechaFin = LocalDateTime.now();
        this.duracionMs = Duration.between(fechaInicio, fechaFin).toMillis();
        this.registrosProcesados = procesados;
        this.registrosValidos = validos;
        this.erroresDetectados = errores;
        this.logPasos = logPasos;
        this.mensajeError = mensajeError;
    }

    public boolean estaEnCurso() {
        return PENDIENTE.equals(estado) || EN_PROCESO.equals(estado);
    }

    public Integer getId() { return id; }
    public ProcesoRpa getProceso() { return proceso; }
    public Integer getIdUsuario() { return idUsuario; }
    public Integer getIdUsuarioRevisor() { return idUsuarioRevisor; }
    public Integer getIdEjecucionOrigen() { return idEjecucionOrigen; }
    public LocalDate getPeriodoDesde() { return periodoDesde; }
    public LocalDate getPeriodoHasta() { return periodoHasta; }
    public String getTipoCorrespondencia() { return tipoCorrespondencia; }
    public String getEstado() { return estado; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public Long getDuracionMs() { return duracionMs; }
    public int getRegistrosProcesados() { return registrosProcesados; }
    public int getRegistrosValidos() { return registrosValidos; }
    public int getErroresDetectados() { return erroresDetectados; }
    public String getLogPasos() { return logPasos; }
    public String getMensajeError() { return mensajeError; }
}
