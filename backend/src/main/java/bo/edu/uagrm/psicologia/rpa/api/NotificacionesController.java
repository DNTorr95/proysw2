package bo.edu.uagrm.psicologia.rpa.api;

import bo.edu.uagrm.psicologia.rpa.dto.RpaDtos.NotificacionDto;
import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import bo.edu.uagrm.psicologia.rpa.repositorio.EjecucionRpaRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Notificaciones internas de la cabecera. Avisa al revisor de informes pendientes ("notifica al revisor"),
 * y a quien gestiona reejecuciones de ejecuciones fallidas y correos con error. No envía correos.
 */
@RestController
@RequestMapping("/api/notificaciones")
@Tag(name = "Notificaciones")
public class NotificacionesController {

    private final InformeMensualRepository informes;
    private final EjecucionRpaRepository ejecuciones;
    private final NotificacionCorreoRepository correos;

    public NotificacionesController(InformeMensualRepository informes, EjecucionRpaRepository ejecuciones,
                                    NotificacionCorreoRepository correos) {
        this.informes = informes;
        this.ejecuciones = ejecuciones;
        this.correos = correos;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    @Operation(summary = "Notificaciones pendientes del usuario según sus permisos")
    public List<NotificacionDto> pendientes() {
        UsuarioAutenticado u = UsuarioActual.obtener();
        List<NotificacionDto> lista = new ArrayList<>();
        if (u.tienePermiso("REVISAR_RPA") || u.tienePermiso("AUTORIZAR_ENVIO_RPA")) {
            informes.buscar(InformeMensual.PENDIENTE_REVISION, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "fechaGeneracion")))
                    .forEach(i -> {
                        boolean asignado = u.id().equals(i.getEjecucion().getIdUsuarioRevisor());
                        lista.add(new NotificacionDto("REVISION_RPA",
                                (asignado ? "Informe asignado para su revisión" : "Informe pendiente de revisión"),
                                "Correspondencia del " + i.getPeriodoDesde() + " al " + i.getPeriodoHasta(),
                                "/rpa/revision/" + i.getId(), i.getFechaGeneracion()));
                    });
        }
        if (u.tienePermiso("GESTIONAR_REEJECUCION_RPA") || u.tienePermiso("EJECUTAR_RPA")) {
            LocalDateTime desde = LocalDateTime.now().minusDays(7);
            ejecuciones.findByEstadoIn(List.of(EjecucionRpa.FALLIDA)).stream()
                    .filter(e -> e.getFechaFin() != null && e.getFechaFin().isAfter(desde))
                    .filter(e -> ejecuciones.findFirstByIdEjecucionOrigen(e.getId()).isEmpty())
                    .forEach(e -> lista.add(new NotificacionDto("EJECUCION_FALLIDA", "Ejecución RPA fallida",
                            "Ejecución " + e.getId() + ": " + (e.getMensajeError() == null ? "ver incidencias" : e.getMensajeError()),
                            "/rpa/historial?ejecucion=" + e.getId(), e.getFechaFin())));
        }
        if (u.tienePermiso("AUTORIZAR_ENVIO_RPA")) {
            correos.findByEstado(NotificacionCorreo.ERROR).forEach(n -> lista.add(new NotificacionDto("ERROR_ENVIO",
                    "Correo autorizado no enviado", n.getMensajeError(), "/rpa/revision/" + n.getInforme().getId(),
                    n.getFechaAutorizacion())));
        }
        lista.sort(Comparator.comparing(NotificacionDto::fecha, Comparator.nullsLast(Comparator.reverseOrder())));
        return lista;
    }
}
