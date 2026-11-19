package bo.edu.uagrm.psicologia.rpa.servicio;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.modelo.PlantillaDocumento;
import bo.edu.uagrm.psicologia.documental.repositorio.CorrespondenciaRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.PlantillaDocumentoRepository;
import bo.edu.uagrm.psicologia.rpa.modelo.DetalleInforme;
import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.IncidenciaRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import bo.edu.uagrm.psicologia.rpa.repositorio.EjecucionRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.IncidenciaRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import bo.edu.uagrm.psicologia.rpa.trabajador.ContratoTrabajador;
import bo.edu.uagrm.psicologia.rpa.trabajador.TrabajadorRpa;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ejecuta en segundo plano una ejecución RPA ya creada en PENDIENTE (CU-31, incluye CU-34 y CU-35).
 * <ol>
 *   <li>Prepara los datos mínimos (solo oficios autorizados del periodo) y marca EN_PROCESO.</li>
 *   <li>Llama al trabajador RPA (fuera de transacción) con el tiempo máximo del proceso.</li>
 *   <li>Registra: ejecución COMPLETADA/FALLIDA, informe PENDIENTE_REVISION, detalle, correo BORRADOR,
 *       incidencias y evidencias. Nunca envía el correo.</li>
 * </ol>
 */
@Service
public class ProcesadorEjecucionRpa {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorEjecucionRpa.class);
    static final String TIPO_PLANTILLA = "INFORME_RPA";

    private final EjecucionRpaRepository ejecuciones;
    private final InformeMensualRepository informes;
    private final NotificacionCorreoRepository correos;
    private final IncidenciaRpaRepository incidencias;
    private final CorrespondenciaRepository correspondencias;
    private final PlantillaDocumentoRepository plantillas;
    private final UsuarioRepository usuarios;
    private final TrabajadorRpa trabajador;
    private final AlmacenamientoService almacenamiento;
    private final AuditoriaService auditoria;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final int margenSegundos;

    public ProcesadorEjecucionRpa(EjecucionRpaRepository ejecuciones, InformeMensualRepository informes,
                                  NotificacionCorreoRepository correos, IncidenciaRpaRepository incidencias,
                                  CorrespondenciaRepository correspondencias, PlantillaDocumentoRepository plantillas,
                                  UsuarioRepository usuarios, TrabajadorRpa trabajador,
                                  AlmacenamientoService almacenamiento, AuditoriaService auditoria,
                                  TransactionTemplate tx, ObjectMapper json, PropiedadesApp props) {
        this.ejecuciones = ejecuciones;
        this.informes = informes;
        this.correos = correos;
        this.incidencias = incidencias;
        this.correspondencias = correspondencias;
        this.plantillas = plantillas;
        this.usuarios = usuarios;
        this.trabajador = trabajador;
        this.almacenamiento = almacenamiento;
        this.auditoria = auditoria;
        this.tx = tx;
        this.json = json;
        this.margenSegundos = props.rpa() == null ? 15 : props.rpa().margenTiempoSegundos();
    }

    private record Preparacion(ContratoTrabajador.Solicitud solicitud, Integer idPlantilla, Duration limite,
                               List<Map<String, Object>> pasos) {
    }

    @Async("ejecutorRpa")
    public void procesarAsync(Integer idEjecucion, List<String> destinatarios) {
        procesar(idEjecucion, destinatarios);
    }

    /** Procesamiento síncrono (lo usa procesarAsync y las pruebas). */
    public void procesar(Integer idEjecucion, List<String> destinatarios) {
        Preparacion prep;
        try {
            prep = tx.execute(estado -> preparar(idEjecucion, destinatarios));
        } catch (RuntimeException e) {
            log.error("No se pudo preparar la ejecución RPA {}", idEjecucion, e);
            registrarFalloBackend(idEjecucion, new ArrayList<>(), "ERROR_INTERNO", "No se pudo preparar la ejecución: " + e.getMessage());
            return;
        }
        if (prep == null) return;

        ContratoTrabajador.Resultado resultado;
        long t0 = System.currentTimeMillis();
        try {
            resultado = trabajador.ejecutar(prep.solicitud(), prep.limite());
        } catch (TrabajadorRpa.FalloTrabajador e) {
            log.warn("Ejecución RPA {} sin resultado del trabajador: {}", idEjecucion, e.getMessage());
            List<Map<String, Object>> pasos = new ArrayList<>(prep.pasos());
            completarPasoEnvio(pasos, "ERROR", e.getMessage(), System.currentTimeMillis() - t0);
            registrarFalloBackend(idEjecucion, pasos, e.getTipoIncidencia(), e.getMessage());
            return;
        }
        long duracionTrabajador = System.currentTimeMillis() - t0;
        try {
            tx.executeWithoutResult(estado -> registrar(idEjecucion, prep, resultado, duracionTrabajador));
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el resultado de la ejecución RPA {}", idEjecucion, e);
            registrarFalloBackend(idEjecucion, new ArrayList<>(prep.pasos()), "ERROR_INTERNO",
                    "No se pudo registrar el resultado: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 1. Preparación

    private Preparacion preparar(Integer idEjecucion, List<String> destinatarios) {
        EjecucionRpa e = ejecuciones.bloquear(idEjecucion).orElse(null);
        if (e == null || !EjecucionRpa.PENDIENTE.equals(e.getEstado())) {
            log.warn("La ejecución {} no está PENDIENTE; se ignora", idEjecucion);
            return null;
        }
        LocalDateTime inicio = LocalDateTime.now();
        Correspondencia.Tipo tipo = "TODAS".equals(e.getTipoCorrespondencia()) ? null
                : Correspondencia.Tipo.valueOf(e.getTipoCorrespondencia());
        List<ContratoTrabajador.Registro> registros = correspondencias.autorizadasDelPeriodo(e.getPeriodoDesde(),
                e.getPeriodoHasta(), tipo).stream().map(c -> new ContratoTrabajador.Registro(c.getCodigo(),
                c.getTipo().name(), c.getAsunto(), c.getFechaDocumento().toString(), c.getEstado().name(),
                c.getDestinatario().getUnidad(), c.getDestinatario().getNombre())).toList();

        PlantillaDocumento plantilla = plantillas.findFirstByTipoAndVigenteTrueOrderByIdDesc(TIPO_PLANTILLA)
                .orElseThrow(() -> new IllegalStateException("No hay una plantilla institucional vigente de tipo " + TIPO_PLANTILLA));
        String revisor = usuarios.findById(e.getIdUsuarioRevisor()).map(u -> u.getPersona().nombreCompleto()).orElse(null);
        int maximo = e.getProceso().getTiempoMaxSegundos();

        List<Map<String, Object>> pasos = new ArrayList<>();
        pasos.add(paso("SOLICITUD", "Consulta de oficios autorizados del periodo (backend)", "OK", inicio, LocalDateTime.now(),
                registros.size() + " oficio(s) autorizados enviados al trabajador; plantilla '" + plantilla.getNombre()
                        + "' v" + plantilla.getVersion()));
        pasos.add(paso("ENVIO_TRABAJADOR", "Procesamiento en el trabajador RPA", "EN_CURSO", LocalDateTime.now(), null,
                "Tiempo máximo: " + maximo + " s"));
        e.iniciarProceso(aJson(pasos));

        ContratoTrabajador.Solicitud s = new ContratoTrabajador.Solicitud(e.getId(), e.getProceso().getNombre(),
                e.getPeriodoDesde().toString(), e.getPeriodoHasta().toString(), e.getTipoCorrespondencia(),
                plantilla.getContenidoHtml(), plantilla.getNombre(), revisor, destinatarios, maximo, registros);
        return new Preparacion(s, plantilla.getId(), Duration.ofSeconds(maximo + (long) margenSegundos), pasos);
    }

    // ------------------------------------------------------------------ 3. Registro

    private void registrar(Integer idEjecucion, Preparacion prep, ContratoTrabajador.Resultado r, long duracionTrabajador) {
        EjecucionRpa e = ejecuciones.bloquear(idEjecucion).orElseThrow();
        List<Map<String, Object>> pasos = new ArrayList<>(prep.pasos());
        completarPasoEnvio(pasos, r.completada() ? "OK" : "ERROR",
                "Resultado del trabajador: " + r.estado(), duracionTrabajador);
        if (r.pasos() != null) {
            r.pasos().forEach(p -> pasos.add(pasoTrabajador(p)));
        }
        List<ContratoTrabajador.Incidencia> lista = new ArrayList<>(r.incidencias() == null ? List.of() : r.incidencias());
        boolean completada = r.completada();
        String mensajeError = r.mensajeError();

        // Integridad: el PDF que reporta el trabajador debe existir en el volumen y coincidir con su hash
        if (completada) {
            String problema = verificarPdf(r.informe());
            if (problema != null) {
                completada = false;
                mensajeError = problema;
                lista.add(new ContratoTrabajador.Incidencia("ERROR_INTERNO", problema));
            }
        }

        LocalDateTime inicioRegistro = LocalDateTime.now();
        ContratoTrabajador.Resumen res = r.resumen() == null ? new ContratoTrabajador.Resumen(0, 0, lista.size(), 0, 0) : r.resumen();
        String detalleRegistro;
        if (completada) {
            InformeMensual informe = new InformeMensual(e, prep.idPlantilla(), res.totalRecibidos(), res.totalEmitidos(),
                    r.informe().ruta(), r.informe().hashSha256());
            if (r.detalle() != null) {
                r.detalle().forEach(d -> informe.getDetalles().add(new DetalleInforme(informe, d.categoria(), d.clave(), d.cantidad())));
            }
            informes.save(informe);
            ContratoTrabajador.Borrador b = r.borrador();
            List<String> destinatarios = b != null && b.destinatarios() != null && !b.destinatarios().isEmpty()
                    ? b.destinatarios() : prep.solicitud().destinatariosCorreo();
            String asunto = b != null && b.asunto() != null ? b.asunto()
                    : "Informe de correspondencia " + e.getPeriodoDesde() + " a " + e.getPeriodoHasta();
            String cuerpo = b != null && b.cuerpo() != null ? b.cuerpo() : "Se adjunta el informe de correspondencia.";
            correos.save(new NotificacionCorreo(informe, destinatarios, recortar(asunto, 300), cuerpo));
            detalleRegistro = "Informe " + informe.getId() + " en PENDIENTE_REVISION y correo en BORRADOR; revisor notificado";
        } else {
            detalleRegistro = "Sin informe ni correo: la ejecución falló";
        }
        lista.forEach(i -> incidencias.save(new IncidenciaRpa(e.getId(), i.tipo(), recortar(i.descripcion(), 2000))));
        cerrarIncidenciasAtendidas(e.getId());
        pasos.add(paso("REGISTRO", "Registro del resultado, informe, borrador e incidencias (backend)", completada ? "OK" : "ERROR",
                inicioRegistro, LocalDateTime.now(), detalleRegistro + "; " + lista.size() + " incidencia(s)"));

        e.finalizar(completada ? EjecucionRpa.COMPLETADA : EjecucionRpa.FALLIDA, res.registrosProcesados(),
                res.registrosValidos(), Math.max(res.erroresDetectados(), lista.size()), aJson(pasos), mensajeError);
        auditoria.registrarComo(e.getIdUsuario(), completada ? "RPA_EJECUCION_COMPLETADA" : "RPA_EJECUCION_FALLIDA",
                "EJECUCION_RPA", e.getId(), "Periodo " + e.getPeriodoDesde() + " a " + e.getPeriodoHasta() + " · "
                        + res.registrosValidos() + "/" + res.registrosProcesados() + " válidos · " + lista.size()
                        + " incidencia(s) · " + e.getDuracionMs() + " ms" + (mensajeError != null ? " · " + mensajeError : ""));
        if (completada) {
            auditoria.registrarComo(e.getIdUsuario(), "NOTIFICAR_REVISOR", "EJECUCION_RPA", e.getId(),
                    "Informe pendiente de revisión asignado al usuario " + e.getIdUsuarioRevisor());
        }
    }

    /** Fallo detectado en el backend (trabajador caído, tiempo excedido, error interno). */
    void registrarFalloBackend(Integer idEjecucion, List<Map<String, Object>> pasos, String tipoIncidencia, String mensaje) {
        tx.executeWithoutResult(estado -> {
            EjecucionRpa e = ejecuciones.bloquear(idEjecucion).orElse(null);
            if (e == null || !e.estaEnCurso()) return;
            incidencias.save(new IncidenciaRpa(e.getId(), tipoIncidencia, recortar(mensaje, 2000)));
            cerrarIncidenciasAtendidas(e.getId());
            pasos.add(paso("REGISTRO", "Registro del fallo (backend)", "ERROR", LocalDateTime.now(), LocalDateTime.now(),
                    tipoIncidencia + ": " + mensaje));
            e.finalizar(EjecucionRpa.FALLIDA, 0, 0, 1, aJson(pasos), mensaje);
            auditoria.registrarComo(e.getIdUsuario(), "RPA_EJECUCION_FALLIDA", "EJECUCION_RPA", e.getId(),
                    tipoIncidencia + ": " + mensaje);
        });
    }

    /** Las incidencias de la ejecución anterior que esta reejecución atendió quedan cerradas. */
    private void cerrarIncidenciasAtendidas(Integer idReejecucion) {
        incidencias.findByIdReejecucion(idReejecucion).forEach(IncidenciaRpa::cerrar);
    }

    private String verificarPdf(ContratoTrabajador.Informe informe) {
        if (informe == null || informe.ruta() == null || informe.hashSha256() == null) {
            return "El trabajador no informó el PDF generado";
        }
        try {
            byte[] contenido = almacenamiento.leer(informe.ruta());
            if (!Objects.equals(AlmacenamientoService.sha256(contenido), informe.hashSha256())) {
                return "El hash del PDF no coincide con el informado por el trabajador";
            }
            return null;
        } catch (RuntimeException ex) {
            return "El PDF informado no se encuentra en el volumen documental: " + informe.ruta();
        }
    }

    // ------------------------------------------------------------------ Evidencias

    static Map<String, Object> paso(String nombre, String descripcion, String estado, LocalDateTime inicio,
                                    LocalDateTime fin, String detalle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paso", nombre);
        m.put("descripcion", descripcion);
        m.put("estado", estado);
        m.put("origen", "BACKEND");
        m.put("inicio", inicio.toString());
        m.put("fin", fin == null ? null : fin.toString());
        m.put("duracionMs", fin == null ? null : Duration.between(inicio, fin).toMillis());
        m.put("detalle", detalle);
        return m;
    }

    private static Map<String, Object> pasoTrabajador(ContratoTrabajador.Paso p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paso", p.paso());
        m.put("descripcion", p.descripcion());
        m.put("estado", p.estado());
        m.put("origen", "TRABAJADOR");
        m.put("inicio", p.inicio());
        m.put("fin", p.fin());
        m.put("duracionMs", p.duracionMs());
        m.put("detalle", p.detalle());
        return m;
    }

    private static void completarPasoEnvio(List<Map<String, Object>> pasos, String estado, String detalle, long duracionMs) {
        pasos.stream().filter(p -> "ENVIO_TRABAJADOR".equals(p.get("paso"))).findFirst().ifPresent(p -> {
            p.put("estado", estado);
            p.put("fin", LocalDateTime.now().toString());
            p.put("duracionMs", duracionMs);
            p.put("detalle", p.get("detalle") + " · " + detalle);
        });
    }

    String aJson(List<Map<String, Object>> pasos) {
        try {
            return json.writeValueAsString(pasos);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    List<Map<String, Object>> deJson(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        try {
            return json.readValue(texto, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private static String recortar(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
