package bo.edu.uagrm.psicologia.rpa.servicio;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.CorreoDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.DetalleDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.EditarBorradorRequest;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.IncidenciaDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.InformeDetalleDto;
import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.InformeDto;
import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import bo.edu.uagrm.psicologia.rpa.repositorio.IncidenciaRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

/**
 * CU-32 Revisar y validar resultados de ejecución RPA: vista previa, edición del borrador,
 * aprobación con autorización de envío, devolución y reintento de envío.
 */
@Service
public class RevisionRpaService {

    private final InformeMensualRepository informes;
    private final NotificacionCorreoRepository correos;
    private final IncidenciaRpaRepository incidencias;
    private final UsuarioRepository usuarios;
    private final EnvioCorreoService envio;
    private final AlmacenamientoService almacenamiento;
    private final AuditoriaService auditoria;
    private final TransactionTemplate tx;

    public RevisionRpaService(InformeMensualRepository informes, NotificacionCorreoRepository correos,
                              IncidenciaRpaRepository incidencias, UsuarioRepository usuarios, EnvioCorreoService envio,
                              AlmacenamientoService almacenamiento, AuditoriaService auditoria, TransactionTemplate tx) {
        this.informes = informes;
        this.correos = correos;
        this.incidencias = incidencias;
        this.usuarios = usuarios;
        this.envio = envio;
        this.almacenamiento = almacenamiento;
        this.auditoria = auditoria;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public Pagina<InformeDto> listar(String estado, int pagina, int tamano) {
        String e = estado == null || estado.isBlank() ? null : estado;
        return Pagina.de(informes.buscar(e, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaGeneracion", "id"))), this::aDto);
    }

    @Transactional(readOnly = true)
    public InformeDetalleDto obtener(Integer id) {
        InformeMensual i = buscar(id);
        UsuarioAutenticado u = UsuarioActual.obtener();
        Optional<NotificacionCorreo> c = correos.findByInformeId(id);
        boolean pendiente = i.pendienteDeRevision();
        boolean revisa = u.tienePermiso("REVISAR_RPA") || u.tienePermiso("AUTORIZAR_ENVIO_RPA");
        return new InformeDetalleDto(aDto(i),
                i.getDetalles().stream().map(d -> new DetalleDto(d.getCategoria(), d.getClave(), d.getCantidad())).toList(),
                c.map(n -> correoDto(n, i)).orElse(null),
                incidencias.findByIdEjecucionOrderByIdAsc(i.getEjecucion().getId()).stream().map(IncidenciaDto::de).toList(),
                pendiente && u.tienePermiso("AUTORIZAR_ENVIO_RPA"),
                pendiente && revisa,
                pendiente && revisa && c.map(n -> NotificacionCorreo.BORRADOR.equals(n.getEstado())).orElse(false),
                u.tienePermiso("AUTORIZAR_ENVIO_RPA") && c.map(n -> NotificacionCorreo.ERROR.equals(n.getEstado())).orElse(false));
    }

    /** PDF del informe para la vista previa (verifica el hash). */
    @Transactional(readOnly = true)
    public byte[] pdf(Integer id) {
        InformeMensual i = buscar(id);
        byte[] contenido = almacenamiento.leer(i.getRutaPdf());
        if (i.getHashSha256() != null && !i.getHashSha256().equals(AlmacenamientoService.sha256(contenido))) {
            throw new NegocioException(HttpStatus.CONFLICT, "La integridad del PDF no pudo verificarse (hash distinto)");
        }
        return contenido;
    }

    @Transactional
    public InformeDetalleDto editarBorrador(Integer id, EditarBorradorRequest req) {
        InformeMensual i = informes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Informe", id));
        exigirPendiente(i);
        NotificacionCorreo n = correos.bloquearPorInforme(id).orElseThrow(() -> new RecursoNoEncontradoException("Correo", id));
        if (!NotificacionCorreo.BORRADOR.equals(n.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El correo ya no está en borrador");
        }
        n.editar(req.destinatarios().stream().map(String::trim).map(String::toLowerCase).distinct().toList(),
                req.asunto().trim(), req.cuerpo().trim());
        auditoria.registrar("EDITAR_BORRADOR", "NOTIFICACION_CORREO", n.getId(), "Destinatarios: " + n.getDestinatarios());
        return obtener(id);
    }

    /**
     * Aprueba el informe y autoriza el envío (guarda id_usuario_autoriza y fecha_autorizacion). Recién entonces
     * el backend envía el correo por SMTP/TLS y lo marca ENVIADO (o ERROR para reintentar).
     */
    public InformeDetalleDto aprobarYAutorizar(Integer id) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        tx.executeWithoutResult(estado -> {
            InformeMensual i = informes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Informe", id));
            exigirPendiente(i);
            NotificacionCorreo n = correos.bloquearPorInforme(id).orElseThrow(() -> new RecursoNoEncontradoException("Correo", id));
            if (!NotificacionCorreo.BORRADOR.equals(n.getEstado())) {
                throw new NegocioException(HttpStatus.CONFLICT, "El correo no está en borrador (estado " + n.getEstado() + ")");
            }
            if (n.listaDestinatarios().isEmpty()) {
                throw new NegocioException("El borrador no tiene destinatarios");
            }
            i.aprobar();
            n.autorizar(u.id());
            auditoria.registrar("APROBAR_INFORME", "INFORME_MENSUAL", i.getId(), "Periodo " + i.getPeriodoDesde() + " a " + i.getPeriodoHasta());
            auditoria.registrar("AUTORIZAR_ENVIO", "NOTIFICACION_CORREO", n.getId(), "Destinatarios: " + n.getDestinatarios());
        });
        envio.enviar(id, u.id());
        return detalleEnTransaccion(id);
    }

    /** Las llamadas internas no pasan por el proxy transaccional: se abre una transacción de lectura explícita. */
    private InformeDetalleDto detalleEnTransaccion(Integer id) {
        return tx.execute(estado -> obtener(id));
    }

    public InformeDetalleDto reintentarEnvio(Integer id) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        NotificacionCorreo n = correos.findByInformeId(id).orElseThrow(() -> new RecursoNoEncontradoException("Correo", id));
        if (!NotificacionCorreo.ERROR.equals(n.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Solo se reintenta un correo con error de envío");
        }
        auditoria.registrar("REINTENTAR_ENVIO", "NOTIFICACION_CORREO", n.getId(), "Informe " + id);
        envio.enviar(id, u.id());
        return detalleEnTransaccion(id);
    }

    @Transactional
    public InformeDetalleDto devolver(Integer id, String observaciones) {
        InformeMensual i = informes.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("Informe", id));
        exigirPendiente(i);
        i.devolver(observaciones.trim());
        correos.bloquearPorInforme(id).ifPresent(NotificacionCorreo::anular);
        auditoria.registrar("DEVOLVER_INFORME", "INFORME_MENSUAL", i.getId(), observaciones.trim());
        return obtener(id);
    }

    // ------------------------------------------------------------------ Apoyo

    private static void exigirPendiente(InformeMensual i) {
        if (!i.pendienteDeRevision()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El informe " + i.getId() + " ya fue revisado (estado " + i.getEstadoRevision() + ")");
        }
    }

    private InformeMensual buscar(Integer id) {
        return informes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Informe", id));
    }

    private String nombre(Integer idUsuario) {
        return idUsuario == null ? null : usuarios.findById(idUsuario).map(x -> x.getPersona().nombreCompleto()).orElse(null);
    }

    private InformeDto aDto(InformeMensual i) {
        EjecucionRpa e = i.getEjecucion();
        return new InformeDto(i.getId(), e.getId(), i.getPeriodoDesde(), i.getPeriodoHasta(), e.getTipoCorrespondencia(),
                i.getTotalRecibidos(), i.getTotalEmitidos(), i.getHashSha256(), i.getFechaGeneracion(), i.getEstadoRevision(),
                i.getObservacionesRevision(), i.getFechaRevision(), e.getIdUsuarioRevisor(), nombre(e.getIdUsuarioRevisor()),
                correos.findByInformeId(i.getId()).map(NotificacionCorreo::getEstado).orElse(null));
    }

    private CorreoDto correoDto(NotificacionCorreo n, InformeMensual i) {
        return new CorreoDto(n.getId(), n.listaDestinatarios(), n.getAsunto(), n.getCuerpo(), n.getEstado(),
                n.getIdUsuarioAutoriza(), nombre(n.getIdUsuarioAutoriza()), n.getFechaAutorizacion(), n.getFechaEnvio(),
                n.getMensajeError(), EnvioCorreoService.nombreAdjunto(i));
    }
}
