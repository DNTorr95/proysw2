package bo.edu.uagrm.psicologia.documental.servicio;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ActualizarExpedienteRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CrearExpedienteRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ExpedienteDetalleDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ExpedienteDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.TramiteResumenDto;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.modelo.Expediente;
import bo.edu.uagrm.psicologia.documental.repositorio.ArchivoAdjuntoRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.ExpedienteRepository;
import bo.edu.uagrm.psicologia.tramites.modelo.DocumentoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.TramiteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** CU-08 Gestionar expedientes administrativos. */
@Service
public class ExpedienteService {

    private final ExpedienteRepository expedientes;
    private final EstudianteRepository estudiantes;
    private final TramiteRepository tramites;
    private final ArchivoAdjuntoRepository archivos;
    private final DocumentoService documentos;
    private final AuditoriaService auditoria;

    public ExpedienteService(ExpedienteRepository expedientes, EstudianteRepository estudiantes,
                             TramiteRepository tramites, ArchivoAdjuntoRepository archivos,
                             DocumentoService documentos, AuditoriaService auditoria) {
        this.expedientes = expedientes;
        this.estudiantes = estudiantes;
        this.tramites = tramites;
        this.archivos = archivos;
        this.documentos = documentos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Pagina<ExpedienteDto> listar(String texto, String estado, int pagina, int tamano) {
        String t = texto == null || texto.isBlank() ? null : texto.trim();
        String e = estado == null || estado.isBlank() ? null : estado;
        return Pagina.de(expedientes.buscar(t, e, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaApertura", "id"))), ExpedienteService::aDto);
    }

    /** Detalle: trámites del expediente y todos sus documentos (propios y de los trámites). */
    @Transactional(readOnly = true)
    public ExpedienteDetalleDto obtener(Integer id) {
        Expediente x = buscar(id);
        List<Tramite> lista = tramites.findByExpedienteIdOrderByFechaRecepcionDesc(id);
        Map<Integer, ArchivoAdjunto> docs = new LinkedHashMap<>();
        archivos.findByIdExpedienteOrderByFechaCargaDesc(id).forEach(a -> docs.put(a.getId(), a));
        lista.stream().flatMap(t -> t.getDocumentos().stream()).map(DocumentoTramite::getArchivo)
                .filter(Objects::nonNull).forEach(a -> docs.putIfAbsent(a.getId(), a));
        List<ArchivoDto> documentosDto = new ArrayList<>(docs.values().stream().map(ArchivoDto::de).toList());
        documentosDto.sort(Comparator.comparing(ArchivoDto::fechaCarga).reversed());
        return new ExpedienteDetalleDto(aDto(x),
                lista.stream().map(t -> new TramiteResumenDto(t.getId(), t.getNumero(), t.getTipoTramite().getNombre(),
                        t.getEstadoActual(), t.getFechaRecepcion(), t.getFechaLimite())).toList(),
                documentosDto);
    }

    @Transactional
    public ExpedienteDto crear(CrearExpedienteRequest req) {
        Estudiante est = req.idEstudiante() == null ? null : estudiantes.findById(req.idEstudiante())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", req.idEstudiante()));
        String codigo = "EXP-" + LocalDate.now().getYear() + "-" + String.format("%04d", expedientes.siguienteNumero());
        Expediente x = expedientes.save(new Expediente(codigo, est, req.titulo().trim(), vacioANulo(req.descripcion())));
        auditoria.registrar("CREAR", "EXPEDIENTE", x.getId(), codigo + " · " + x.getTitulo());
        return aDto(x);
    }

    @Transactional
    public ExpedienteDto actualizar(Integer id, ActualizarExpedienteRequest req) {
        Expediente x = buscarAbierto(id);
        x.actualizar(req.titulo().trim(), vacioANulo(req.descripcion()));
        auditoria.registrar("ACTUALIZAR", "EXPEDIENTE", x.getId(), x.getCodigo());
        return aDto(x);
    }

    @Transactional
    public ExpedienteDto cambiarEstado(Integer id, String estado, String motivo) {
        if (!Expediente.ABIERTO.equals(estado) && !Expediente.CERRADO.equals(estado)) {
            throw new NegocioException("Estado de expediente inválido: use ABIERTO o CERRADO");
        }
        Expediente x = buscar(id);
        x.cambiarEstado(estado);
        auditoria.registrar(Expediente.CERRADO.equals(estado) ? "CERRAR" : "REABRIR", "EXPEDIENTE", x.getId(),
                x.getCodigo() + ". Motivo: " + motivo);
        return aDto(x);
    }

    @Transactional
    public ExpedienteDto vincularTramite(Integer id, Integer idTramite) {
        Expediente x = buscarAbierto(id);
        Tramite t = tramites.findById(idTramite).orElseThrow(() -> new RecursoNoEncontradoException("Trámite", idTramite));
        if (x.getEstudiante() != null && !x.getEstudiante().getId().equals(t.getEstudiante().getId())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El trámite pertenece a otro estudiante");
        }
        t.asignarExpediente(x);
        auditoria.registrar("VINCULAR_TRAMITE", "EXPEDIENTE", x.getId(), x.getCodigo() + " <- " + t.getNumero());
        return aDto(x);
    }

    @Transactional
    public ArchivoDto adjuntar(Integer id, MultipartFile archivo, String descripcion) {
        Expediente x = buscarAbierto(id);
        return ArchivoDto.de(documentos.guardar(archivo, "expedientes", null, x.getId(), descripcion));
    }

    private Expediente buscar(Integer id) {
        return expedientes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Expediente", id));
    }

    private Expediente buscarAbierto(Integer id) {
        Expediente x = buscar(id);
        if (!x.estaAbierto()) throw new NegocioException(HttpStatus.CONFLICT, "El expediente " + x.getCodigo() + " está cerrado");
        return x;
    }

    static ExpedienteDto aDto(Expediente x) {
        Estudiante e = x.getEstudiante();
        return new ExpedienteDto(x.getId(), x.getCodigo(), e == null ? null : e.getId(),
                e == null ? null : e.nombreCompleto(), e == null ? null : e.getRegistro(),
                x.getTitulo(), x.getDescripcion(), x.getEstado(), x.getFechaApertura());
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
