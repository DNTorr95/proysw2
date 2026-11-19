package bo.edu.uagrm.psicologia.tramites.servicio;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.modelo.Expediente;
import bo.edu.uagrm.psicologia.documental.repositorio.ExpedienteRepository;
import bo.edu.uagrm.psicologia.documental.servicio.DocumentoService;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.AccionDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DictamenDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DictamenRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.DocumentoTramiteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.EstudianteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.HistorialDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.RegistrarTramiteRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.RequisitoRequest;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TipoTramiteDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TramiteDetalleDto;
import bo.edu.uagrm.psicologia.tramites.dto.TramitesDtos.TramiteDto;
import bo.edu.uagrm.psicologia.tramites.maquina.ContextoTransicion;
import bo.edu.uagrm.psicologia.tramites.maquina.MaquinaEstadosTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Dictamen;
import bo.edu.uagrm.psicologia.tramites.modelo.DocumentoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.HistorialEstado;
import bo.edu.uagrm.psicologia.tramites.modelo.RequisitoTipoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.TipoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.DictamenRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.HistorialEstadoRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.TipoTramiteRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.TramiteRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CU-09 Registrar trámite · CU-10 Requisitos y documentación · CU-11 Actuaciones (máquina de estados)
 * · CU-12 Seguimiento e historial.
 */
@Service
public class TramiteService {

    /** Permiso requerido por cada evento de la máquina de estados. */
    static final Map<String, String> PERMISO_POR_EVENTO = Map.of(
            "derivar", "EJECUTAR_ACTUACION_TRAMITE",
            "observar", "EJECUTAR_ACTUACION_TRAMITE",
            "subsanar", "EJECUTAR_ACTUACION_TRAMITE",
            "archivar", "EJECUTAR_ACTUACION_TRAMITE",
            "aprobar", "APROBAR_RECHAZAR_TRAMITE",
            "rechazar", "APROBAR_RECHAZAR_TRAMITE");

    private static final Map<String, String> ETIQUETAS = Map.of(
            "derivar", "Derivar a revisión", "observar", "Observar", "subsanar", "Registrar subsanación",
            "aprobar", "Aprobar", "rechazar", "Rechazar", "archivar", "Archivar");

    private static final Set<String> CON_JUSTIFICACION = Set.of("observar", "rechazar");
    private static final Set<String> ESTADOS_EDITABLES = Set.of(
            EstadoTramite.RECEPCIONADO, EstadoTramite.EN_REVISION, EstadoTramite.OBSERVADO);

    private final TramiteRepository tramites;
    private final TipoTramiteRepository tipos;
    private final EstudianteRepository estudiantes;
    private final ExpedienteRepository expedientes;
    private final HistorialEstadoRepository historial;
    private final DictamenRepository dictamenes;
    private final UsuarioRepository usuarios;
    private final DocumentoService documentos;
    private final MaquinaEstadosTramite maquina;
    private final AuditoriaService auditoria;

    public TramiteService(TramiteRepository tramites, TipoTramiteRepository tipos, EstudianteRepository estudiantes,
                          ExpedienteRepository expedientes, HistorialEstadoRepository historial,
                          DictamenRepository dictamenes, UsuarioRepository usuarios, DocumentoService documentos,
                          MaquinaEstadosTramite maquina, AuditoriaService auditoria) {
        this.tramites = tramites;
        this.tipos = tipos;
        this.estudiantes = estudiantes;
        this.expedientes = expedientes;
        this.historial = historial;
        this.dictamenes = dictamenes;
        this.usuarios = usuarios;
        this.documentos = documentos;
        this.maquina = maquina;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // Catálogos
    // =====================================================================

    @Transactional(readOnly = true)
    public List<TipoTramiteDto> tiposTramite() {
        return tipos.findByActivoTrueOrderByNombre().stream().map(TipoTramiteDto::de).toList();
    }

    @Transactional(readOnly = true)
    public Pagina<EstudianteDto> buscarEstudiantes(String texto, int pagina, int tamano) {
        String t = texto == null || texto.isBlank() ? null : texto.trim();
        return Pagina.de(estudiantes.buscar(t, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 50),
                Sort.by("registro"))), EstudianteDto::de);
    }

    // =====================================================================
    // CU-09 / CU-10 Registro
    // =====================================================================

    @Transactional
    public TramiteDetalleDto registrar(RegistrarTramiteRequest req) {
        Estudiante est = estudiantes.findById(req.idEstudiante())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", req.idEstudiante()));
        TipoTramite tipo = tipos.findById(req.idTipoTramite())
                .orElseThrow(() -> new RecursoNoEncontradoException("Tipo de trámite", req.idTipoTramite()));
        if (!tipo.isActivo()) throw new NegocioException("El tipo de trámite no está habilitado");

        Set<Integer> presentados = req.requisitosPresentados() == null ? Set.of() : new HashSet<>(req.requisitosPresentados());
        Set<Integer> validos = tipo.getRequisitos().stream().map(RequisitoTipoTramite::getId).collect(Collectors.toSet());
        if (!validos.containsAll(presentados)) {
            throw new NegocioException("Algún requisito indicado no corresponde al tipo de trámite seleccionado");
        }

        String numero = "TRM-" + LocalDate.now().getYear() + "-" + String.format("%04d", tramites.siguienteNumero());
        Usuario usuario = usuarios.getReferenceById(UsuarioActual.id());
        Tramite t = new Tramite(numero, tipo, est, usuario, req.prioridad() == null ? "MEDIA" : req.prioridad(),
                vacioANulo(req.observaciones()));
        for (RequisitoTipoTramite r : tipo.getRequisitos()) {
            t.getDocumentos().add(new DocumentoTramite(t, r, presentados.contains(r.getId())));
        }
        if (req.idExpediente() != null) {
            Expediente x = expedientes.findById(req.idExpediente())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", req.idExpediente()));
            if (!x.estaAbierto()) throw new NegocioException(HttpStatus.CONFLICT, "El expediente está cerrado");
            if (x.getEstudiante() != null && !x.getEstudiante().getId().equals(est.getId())) {
                throw new NegocioException(HttpStatus.CONFLICT, "El expediente pertenece a otro estudiante");
            }
            t.asignarExpediente(x);
        }
        t = tramites.save(t);
        historial.save(new HistorialEstado(t.getId(), null, null, EstadoTramite.RECEPCIONADO, "registrar",
                UsuarioActual.id(), "Trámite recepcionado"));
        auditoria.registrar("REGISTRAR", "TRAMITE", t.getId(), numero + " · " + tipo.getNombre() + " · estudiante "
                + est.getRegistro() + " · requisitos presentados " + presentados.size() + "/" + validos.size());
        return detalle(t);
    }

    @Transactional
    public DocumentoTramiteDto actualizarRequisito(Integer idTramite, Integer idDocumento, RequisitoRequest req) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        Tramite t = editable(idTramite);
        DocumentoTramite d = documento(t, idDocumento);
        List<String> cambios = new ArrayList<>();
        if (req.presentado() != null) {
            exigirPermiso(u, "GESTIONAR_REQUISITOS");
            d.marcarPresentado(req.presentado());
            cambios.add("presentado=" + req.presentado());
        }
        if (req.verificado() != null) {
            if (!u.tienePermiso("GESTIONAR_REQUISITOS") && !u.tienePermiso("EJECUTAR_ACTUACION_TRAMITE")) {
                throw new NegocioException(HttpStatus.FORBIDDEN, "No tiene permiso para verificar requisitos");
            }
            if (req.verificado() && !d.isPresentado()) {
                throw new NegocioException("No se puede verificar un requisito que no fue presentado");
            }
            d.verificar(req.verificado(), vacioANulo(req.observacion()));
            cambios.add("verificado=" + req.verificado());
        }
        if (cambios.isEmpty()) throw new NegocioException("No se indicó ningún cambio");
        auditoria.registrar("ACTUALIZAR_REQUISITO", "TRAMITE", t.getId(), t.getNumero() + " · documento "
                + idDocumento + " · " + String.join(", ", cambios));
        return documentoDto(d);
    }

    @Transactional
    public DocumentoTramiteDto adjuntarRequisito(Integer idTramite, Integer idDocumento, MultipartFile archivo) {
        Tramite t = editable(idTramite);
        DocumentoTramite d = documento(t, idDocumento);
        ArchivoAdjunto a = documentos.guardar(archivo, "tramites", null, null,
                t.getNumero() + " · " + (d.getRequisito() != null ? d.getRequisito().getDescripcion() : "documento"));
        d.adjuntar(a);
        auditoria.registrar("ADJUNTAR_REQUISITO", "TRAMITE", t.getId(), t.getNumero() + " · documento " + idDocumento
                + " · archivo " + a.getId());
        return documentoDto(d);
    }

    @Transactional
    public DocumentoTramiteDto agregarDocumento(Integer idTramite, MultipartFile archivo, String observacion) {
        Tramite t = editable(idTramite);
        ArchivoAdjunto a = documentos.guardar(archivo, "tramites", null, null, t.getNumero() + " · documento adicional");
        DocumentoTramite d = new DocumentoTramite(t, a, vacioANulo(observacion));
        t.getDocumentos().add(d);
        tramites.saveAndFlush(t);
        auditoria.registrar("AGREGAR_DOCUMENTO", "TRAMITE", t.getId(), t.getNumero() + " · archivo " + a.getId());
        return documentoDto(d);
    }

    // =====================================================================
    // CU-11 Actuaciones mediante la máquina de estados
    // =====================================================================

    /**
     * Ejecuta una actuación. El estado nunca se modifica con un UPDATE directo: la máquina de estados
     * valida la transición y sus guardas, y solo entonces se aplica el resultado, se escribe el historial
     * y se registra la auditoría, todo en la misma transacción.
     */
    @Transactional
    public TramiteDetalleDto ejecutarEvento(Integer idTramite, String evento, String justificacion) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        String permiso = PERMISO_POR_EVENTO.get(evento);
        if (permiso == null) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Acción desconocida: " + evento);
        }
        exigirPermiso(u, permiso);

        Tramite t = tramites.bloquear(idTramite).orElseThrow(() -> new RecursoNoEncontradoException("Trámite", idTramite));
        int pendientes = (int) t.getDocumentos().stream().filter(DocumentoTramite::esObligatorioPendiente).count();
        MaquinaEstadosTramite.Resultado r = maquina.disparar(t.getEstadoActual(), evento,
                new ContextoTransicion(u.rol(), justificacion, pendientes));

        t.aplicarTransicionAceptada(r.destino());
        String observacion = vacioANulo(justificacion);
        historial.save(new HistorialEstado(t.getId(), r.idTransicion(), r.origen(), r.destino(), evento, u.id(),
                observacion != null ? observacion : ETIQUETAS.getOrDefault(evento, evento)));
        auditoria.registrar("TRAMITE_" + evento.toUpperCase(), "TRAMITE", t.getId(),
                t.getNumero() + ": " + r.origen() + " -> " + r.destino() + (observacion != null ? ". " + observacion : ""));
        return detalle(t);
    }

    @Transactional
    public DictamenDto registrarDictamen(Integer idTramite, DictamenRequest req) {
        Tramite t = tramites.findById(idTramite).orElseThrow(() -> new RecursoNoEncontradoException("Trámite", idTramite));
        if (!EstadoTramite.EN_REVISION.equals(t.getEstadoActual())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El dictamen solo puede registrarse con el trámite EN_REVISION");
        }
        Dictamen d = dictamenes.findByIdTramite(idTramite).orElse(null);
        if (d == null) d = dictamenes.save(new Dictamen(idTramite, req.resultado(), req.fundamento().trim()));
        else d.actualizar(req.resultado(), req.fundamento().trim());
        auditoria.registrar("DICTAMEN", "TRAMITE", idTramite, t.getNumero() + " · " + req.resultado());
        return DictamenDto.de(d);
    }

    // =====================================================================
    // CU-12 Seguimiento e historial
    // =====================================================================

    @Transactional(readOnly = true)
    public Pagina<TramiteDto> listar(String texto, String estado, Integer idTipoTramite, String prioridad,
                                     Integer idEstudiante, LocalDate desde, LocalDate hasta, Boolean vencidos,
                                     int pagina, int tamano) {
        Specification<Tramite> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                var est = root.join("estudiante", JoinType.INNER);
                var per = est.join("persona", JoinType.INNER);
                p.add(cb.or(cb.like(cb.lower(root.get("numero")), patron), cb.like(est.get("registro"), patron),
                        cb.like(cb.lower(cb.concat(cb.concat(per.get("nombres"), " "), per.get("apellidos"))), patron)));
            }
            if (estado != null && !estado.isBlank()) p.add(cb.equal(root.get("estadoActual"), estado));
            if (idTipoTramite != null) p.add(cb.equal(root.get("tipoTramite").get("id"), idTipoTramite));
            if (prioridad != null && !prioridad.isBlank()) p.add(cb.equal(root.get("prioridad"), prioridad));
            if (idEstudiante != null) p.add(cb.equal(root.get("estudiante").get("id"), idEstudiante));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaRecepcion"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaRecepcion"), hasta.plusDays(1).atStartOfDay()));
            if (Boolean.TRUE.equals(vencidos)) {
                p.add(cb.isNull(root.get("fechaCierre")));
                p.add(cb.notEqual(root.get("estadoActual"), EstadoTramite.ARCHIVADO));
                p.add(cb.lessThan(root.get("fechaLimite"), LocalDate.now()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return Pagina.de(tramites.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaRecepcion", "id"))), TramiteService::aDto);
    }

    @Transactional(readOnly = true)
    public TramiteDetalleDto obtener(Integer id) {
        return detalle(tramites.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trámite", id)));
    }

    // =====================================================================
    // Apoyo
    // =====================================================================

    private TramiteDetalleDto detalle(Tramite t) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        List<HistorialEstado> h = historial.findByIdTramiteOrderByFechaHoraAscIdAsc(t.getId());
        Set<Integer> ids = h.stream().map(HistorialEstado::getIdUsuario).collect(Collectors.toSet());
        Map<Integer, String> nombres = usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, x -> x.getPersona().nombreCompleto(), (a, b) -> a, java.util.HashMap::new));
        Map<String, String> destinos = maquina.destinosDesde(t.getEstadoActual());
        List<AccionDto> acciones = destinos.keySet().stream()
                .filter(ev -> u.tienePermiso(PERMISO_POR_EVENTO.getOrDefault(ev, "-")))
                .map(ev -> new AccionDto(ev, ETIQUETAS.getOrDefault(ev, ev), destinos.get(ev), CON_JUSTIFICACION.contains(ev)))
                .toList();
        int pendientes = (int) t.getDocumentos().stream().filter(DocumentoTramite::esObligatorioPendiente).count();
        return new TramiteDetalleDto(aDto(t),
                t.getDocumentos().stream().map(TramiteService::documentoDto).toList(),
                h.stream().map(x -> new HistorialDto(x.getId(), x.getEstadoOrigen(), x.getEstadoDestino(), x.getEvento(),
                        x.getIdUsuario(), nombres.get(x.getIdUsuario()), x.getFechaHora(), x.getObservacion())).toList(),
                DictamenDto.de(dictamenes.findByIdTramite(t.getId()).orElse(null)),
                acciones, pendientes, t.getTipoTramite().isRequiereDictamen());
    }

    static TramiteDto aDto(Tramite t) {
        Estudiante e = t.getEstudiante();
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), t.getFechaLimite());
        return new TramiteDto(t.getId(), t.getNumero(), t.getTipoTramite().getId(), t.getTipoTramite().getNombre(),
                e.getId(), e.nombreCompleto(), e.getRegistro(),
                t.getExpediente() == null ? null : t.getExpediente().getId(),
                t.getExpediente() == null ? null : t.getExpediente().getCodigo(),
                t.getFechaRecepcion(), t.getFechaLimite(), t.getFechaCierre(), t.getEstadoActual(), t.getPrioridad(),
                t.getObservaciones(), t.estaVencido(), dias, t.getUsuarioRegistro().getNombreUsuario());
    }

    static DocumentoTramiteDto documentoDto(DocumentoTramite d) {
        RequisitoTipoTramite r = d.getRequisito();
        return new DocumentoTramiteDto(d.getId(), r == null ? null : r.getId(),
                r != null ? r.getDescripcion() : (d.getArchivo() != null ? d.getArchivo().getNombreArchivo() : "Documento"),
                r != null && r.isObligatorio(), d.isEsRequisito(), d.isPresentado(), d.isVerificado(), d.getObservacion(),
                d.getArchivo() == null ? null : ArchivoDto.de(d.getArchivo()));
    }

    private Tramite editable(Integer id) {
        Tramite t = tramites.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trámite", id));
        if (!ESTADOS_EDITABLES.contains(t.getEstadoActual())) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El trámite " + t.getNumero() + " está " + t.getEstadoActual() + " y ya no admite cambios en su documentación");
        }
        return t;
    }

    private static DocumentoTramite documento(Tramite t, Integer idDocumento) {
        return t.getDocumentos().stream().filter(d -> d.getId().equals(idDocumento)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento del trámite", idDocumento));
    }

    private static void exigirPermiso(UsuarioAutenticado u, String permiso) {
        if (!u.tienePermiso(permiso)) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta operación");
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
