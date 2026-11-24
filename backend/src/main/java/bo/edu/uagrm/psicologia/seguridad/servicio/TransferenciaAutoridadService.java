package bo.edu.uagrm.psicologia.seguridad.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.repositorio.ArchivoAdjuntoRepository;
import bo.edu.uagrm.psicologia.documental.servicio.DocumentoService;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.modelo.TransferenciaAutoridad;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.TransferenciaAutoridadRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** CU-04 Transferir autoridad de Dirección (delegación temporal o indefinida, revocable). */
@Service
public class TransferenciaAutoridadService {

    private static final Logger log = LoggerFactory.getLogger(TransferenciaAutoridadService.class);

    private final TransferenciaAutoridadRepository transferencias;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final DocumentoService documentos;
    private final ArchivoAdjuntoRepository archivos;

    public TransferenciaAutoridadService(TransferenciaAutoridadRepository transferencias, UsuarioRepository usuarios,
                                         AuditoriaService auditoria, DocumentoService documentos, ArchivoAdjuntoRepository archivos) {
        this.transferencias = transferencias;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.documentos = documentos;
        this.archivos = archivos;
    }

    public record TransferirRequest(
            @NotNull(message = "El usuario que recibe la autoridad es obligatorio") Integer idUsuarioDestino,
            @NotBlank(message = "El motivo es obligatorio") @Size(min = 10, max = 300, message = "El motivo debe tener entre 10 y 300 caracteres") String motivo,
            @Size(max = 100) String resolucion,
            @NotNull(message = "La fecha de inicio es obligatoria") LocalDate fechaInicio,
            LocalDate fechaFin) {
    }

    public record TransferenciaDto(Integer id, Integer idOrigen, String origen, Integer idDestino, String destino,
                                   String rolDestino, String motivo, String resolucion, LocalDate fechaInicio,
                                   LocalDate fechaFin, String estado, boolean activaHoy, ArchivoDto respaldo) {
        static TransferenciaDto de(TransferenciaAutoridad t, ArchivoAdjunto respaldo) {
            return new TransferenciaDto(t.getId(), t.getOrigen().getId(), t.getOrigen().getPersona().nombreCompleto(),
                    t.getDestino().getId(), t.getDestino().getPersona().nombreCompleto(), t.getDestino().getRol().getNombre(),
                    t.getMotivo(), t.getResolucion(), t.getFechaInicio(), t.getFechaFin(), t.getEstado(),
                    t.activaEn(LocalDate.now()), respaldo == null ? null : ArchivoDto.de(respaldo));
        }
    }

    @Transactional(readOnly = true)
    public List<TransferenciaDto> listar() {
        List<TransferenciaAutoridad> lista = transferencias.findAllByOrderByFechaInicioDescIdDesc();
        Map<Integer, ArchivoAdjunto> respaldos = archivos.findAllById(lista.stream().map(TransferenciaAutoridad::getIdArchivoRespaldo)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(ArchivoAdjunto::getId, Function.identity()));
        return lista.stream().map(t -> TransferenciaDto.de(t, respaldos.get(t.getIdArchivoRespaldo()))).toList();
    }

    private TransferenciaDto aDto(TransferenciaAutoridad t) {
        return TransferenciaDto.de(t, t.getIdArchivoRespaldo() == null ? null : archivos.findById(t.getIdArchivoRespaldo()).orElse(null));
    }

    /**
     * Adjunta (o reemplaza) el documento de respaldo de una transferencia vigente: resolución, memorándum u otro
     * documento escaneado. El archivo se valida como cualquier documento (tipo real, 10 MB, SHA-256).
     */
    @Transactional
    public TransferenciaDto adjuntarRespaldo(Integer id, MultipartFile archivo) {
        TransferenciaAutoridad t = transferencias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Transferencia", id));
        if (!TransferenciaAutoridad.VIGENTE.equals(t.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "Solo se adjunta el respaldo de una transferencia vigente");
        }
        ArchivoAdjunto a = documentos.guardar(archivo, "autoridad", null, null, "Respaldo de la transferencia de autoridad #" + id);
        t.adjuntarRespaldo(a.getId());
        auditoria.registrar("ADJUNTAR_RESPALDO_AUTORIDAD", "TRANSFERENCIA_AUTORIDAD", t.getId(),
                a.getNombreArchivo() + " (SHA-256 " + a.getHashSha256() + ")");
        return TransferenciaDto.de(t, a);
    }

    @Transactional
    public TransferenciaDto transferir(TransferirRequest req) {
        Usuario origen = usuarios.findById(UsuarioActual.id()).orElseThrow();
        if (!Rol.DIRECTOR.equals(origen.getRol().getNombre())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Solo el Director de Carrera titular puede transferir la autoridad");
        }
        Usuario destino = usuarios.findById(req.idUsuarioDestino())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", req.idUsuarioDestino()));
        if (destino.getId().equals(origen.getId())) throw new NegocioException("No puede transferirse la autoridad a sí mismo");
        if (!destino.estaActivo()) throw new NegocioException("El usuario que recibe la autoridad debe estar activo");
        if (Rol.DIRECTOR.equals(destino.getRol().getNombre())) throw new NegocioException("El usuario ya tiene el rol de Director de Carrera");
        LocalDate hoy = LocalDate.now();
        if (req.fechaInicio().isBefore(hoy)) throw new NegocioException("La fecha de inicio no puede ser anterior a hoy");
        if (req.fechaFin() != null && req.fechaFin().isBefore(req.fechaInicio())) {
            throw new NegocioException("La fecha de fin es anterior a la de inicio");
        }
        if (req.fechaFin() != null && req.fechaInicio().plusDays(366).isBefore(req.fechaFin())) {
            throw new NegocioException("La transferencia no puede superar un año");
        }
        if (!transferencias.findByEstado(TransferenciaAutoridad.VIGENTE).isEmpty()) {
            throw new NegocioException(HttpStatus.CONFLICT, "Ya existe una transferencia de autoridad vigente; revóquela antes de registrar otra");
        }
        TransferenciaAutoridad t = transferencias.save(new TransferenciaAutoridad(origen, destino, req.motivo().trim(),
                req.resolucion() == null || req.resolucion().isBlank() ? null : req.resolucion().trim(),
                req.fechaInicio(), req.fechaFin()));
        auditoria.registrar("TRANSFERIR_AUTORIDAD", "TRANSFERENCIA_AUTORIDAD", t.getId(), origen.getNombreUsuario() + " -> "
                + destino.getNombreUsuario() + " del " + t.getFechaInicio() + (t.getFechaFin() == null ? " (indefinida)" : " al " + t.getFechaFin())
                + ". Motivo: " + t.getMotivo());
        return aDto(t);
    }

    @Transactional
    public TransferenciaDto revocar(Integer id, String motivo) {
        Usuario actor = usuarios.findById(UsuarioActual.id()).orElseThrow();
        if (!Rol.DIRECTOR.equals(actor.getRol().getNombre())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Solo el Director de Carrera titular puede revocar la transferencia");
        }
        TransferenciaAutoridad t = transferencias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Transferencia", id));
        if (!TransferenciaAutoridad.VIGENTE.equals(t.getEstado())) {
            throw new NegocioException(HttpStatus.CONFLICT, "La transferencia ya no está vigente");
        }
        t.revocar(LocalDate.now());
        auditoria.registrar("REVOCAR_AUTORIDAD", "TRANSFERENCIA_AUTORIDAD", t.getId(), "Revocada. Motivo: " + motivo);
        return aDto(t);
    }

    /** Job diario: las transferencias vencidas pasan a FINALIZADA (la autoridad ya expiró por fecha). */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void finalizarVencidas() {
        LocalDate hoy = LocalDate.now();
        transferencias.findByEstado(TransferenciaAutoridad.VIGENTE).stream()
                .filter(t -> t.getFechaFin() != null && hoy.isAfter(t.getFechaFin()))
                .forEach(t -> {
                    t.finalizar();
                    auditoria.registrarComo(null, "FINALIZAR_AUTORIDAD", "TRANSFERENCIA_AUTORIDAD", t.getId(), "Vencimiento automático");
                    log.info("Transferencia de autoridad {} finalizada por vencimiento", t.getId());
                });
    }
}
