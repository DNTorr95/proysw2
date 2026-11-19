package bo.edu.uagrm.psicologia.institucional.servicio;

import bo.edu.uagrm.psicologia.academico.modelo.Estudiante;
import bo.edu.uagrm.psicologia.academico.repositorio.EstudianteRepository;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.documental.servicio.DocumentoService;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TituladoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TituladoRequest;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TrabajoGradoDto;
import bo.edu.uagrm.psicologia.institucional.dto.InstitucionalDtos.TrabajoGradoRequest;
import bo.edu.uagrm.psicologia.institucional.modelo.Titulado;
import bo.edu.uagrm.psicologia.institucional.modelo.TrabajoGrado;
import bo.edu.uagrm.psicologia.institucional.repositorio.TituladoRepository;
import bo.edu.uagrm.psicologia.institucional.repositorio.TrabajoGradoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/** CU-17 Gestionar registros de titulados · CU-18 Gestionar investigaciones y trabajos de grado. */
@Service
public class TituladoTrabajoService {

    private static final Set<String> ESTADOS_TITULABLES = Set.of("EGRESADO", "REGULAR");

    private final TituladoRepository titulados;
    private final TrabajoGradoRepository trabajos;
    private final EstudianteRepository estudiantes;
    private final DocumentoService documentos;
    private final AuditoriaService auditoria;

    public TituladoTrabajoService(TituladoRepository titulados, TrabajoGradoRepository trabajos,
                                  EstudianteRepository estudiantes, DocumentoService documentos, AuditoriaService auditoria) {
        this.titulados = titulados;
        this.trabajos = trabajos;
        this.estudiantes = estudiantes;
        this.documentos = documentos;
        this.auditoria = auditoria;
    }

    // ---------- CU-17 ----------

    @Transactional(readOnly = true)
    public List<TituladoDto> titulados() {
        return titulados.findAllByOrderByGestionDescFechaDefensaDesc().stream().map(TituladoDto::de).toList();
    }

    @Transactional
    public TituladoDto registrarTitulado(TituladoRequest req) {
        Estudiante e = estudiante(req.idEstudiante());
        if (titulados.existsByEstudianteId(e.getId())) {
            throw new NegocioException(HttpStatus.CONFLICT, "El estudiante " + e.getRegistro() + " ya tiene un registro de titulación");
        }
        if (!ESTADOS_TITULABLES.contains(e.getEstado())) {
            throw new NegocioException("El estudiante está en estado " + e.getEstado() + " y no puede registrarse como titulado");
        }
        TrabajoGrado trabajo = trabajoDelEstudiante(req.idTrabajo(), e);
        validarModalidad(req.modalidad(), trabajo);
        Titulado t = titulados.save(new Titulado(e, trabajo, req.gestion(), req.modalidad(), req.fechaDefensa(), vacio(req.nroDiploma())));
        e.cambiarEstado("TITULADO");
        auditoria.registrar("REGISTRAR_TITULADO", "ESTUDIANTE", e.getId(), e.getRegistro() + " · " + req.modalidad() + " · " + req.gestion());
        return TituladoDto.de(t);
    }

    @Transactional
    public TituladoDto actualizarTitulado(Integer id, TituladoRequest req) {
        Titulado t = titulados.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Titulado", id));
        if (!t.getEstudiante().getId().equals(req.idEstudiante())) {
            throw new NegocioException("No se puede cambiar el estudiante de un registro de titulación");
        }
        TrabajoGrado trabajo = trabajoDelEstudiante(req.idTrabajo(), t.getEstudiante());
        validarModalidad(req.modalidad(), trabajo);
        t.actualizar(trabajo, req.gestion(), req.modalidad(), req.fechaDefensa(), vacio(req.nroDiploma()));
        auditoria.registrar("ACTUALIZAR_TITULADO", "ESTUDIANTE", t.getEstudiante().getId(), t.getEstudiante().getRegistro());
        return TituladoDto.de(t);
    }

    // ---------- CU-18 ----------

    @Transactional(readOnly = true)
    public List<TrabajoGradoDto> trabajos(String texto, Integer gestion) {
        return trabajos.buscar(texto == null || texto.isBlank() ? null : texto.trim(), gestion).stream().map(TrabajoGradoDto::de).toList();
    }

    @Transactional
    public TrabajoGradoDto crearTrabajo(TrabajoGradoRequest req) {
        Estudiante e = estudiante(req.idEstudiante());
        TrabajoGrado t = trabajos.save(new TrabajoGrado(e, req.titulo().trim(), vacio(req.tutor()), req.gestion(), normalizarClaves(req.palabrasClave())));
        auditoria.registrar("CREAR", "TRABAJO_GRADO", t.getId(), e.getRegistro() + " · " + t.getTitulo());
        return TrabajoGradoDto.de(t);
    }

    @Transactional
    public TrabajoGradoDto actualizarTrabajo(Integer id, TrabajoGradoRequest req) {
        TrabajoGrado t = trabajo(id);
        if (!t.getEstudiante().getId().equals(req.idEstudiante())) {
            throw new NegocioException("No se puede cambiar el autor del trabajo de grado");
        }
        t.actualizar(req.titulo().trim(), vacio(req.tutor()), req.gestion(), normalizarClaves(req.palabrasClave()));
        auditoria.registrar("ACTUALIZAR", "TRABAJO_GRADO", t.getId(), t.getTitulo());
        return TrabajoGradoDto.de(t);
    }

    /** Adjunta el documento final (PDF o DOCX) del trabajo de grado al volumen documental. */
    @Transactional
    public TrabajoGradoDto adjuntar(Integer id, MultipartFile archivo) {
        TrabajoGrado t = trabajo(id);
        t.adjuntar(documentos.guardar(archivo, "trabajos-grado", null, null, "Trabajo de grado: " + recortar(t.getTitulo(), 250)));
        auditoria.registrar("ADJUNTAR", "TRABAJO_GRADO", t.getId(), "Documento " + t.getArchivo().getId());
        return TrabajoGradoDto.de(t);
    }

    // ---------- Apoyo ----------

    private void validarModalidad(String modalidad, TrabajoGrado trabajo) {
        if (("TESIS".equals(modalidad) || "PROYECTO_GRADO".equals(modalidad) || "TRABAJO_DIRIGIDO".equals(modalidad)) && trabajo == null) {
            throw new NegocioException("La modalidad " + modalidad + " requiere vincular el trabajo de grado");
        }
    }

    private TrabajoGrado trabajoDelEstudiante(Integer idTrabajo, Estudiante e) {
        if (idTrabajo == null) return null;
        TrabajoGrado t = trabajo(idTrabajo);
        if (!t.getEstudiante().getId().equals(e.getId())) {
            throw new NegocioException("El trabajo de grado pertenece a otro estudiante");
        }
        return t;
    }

    private Estudiante estudiante(Integer id) {
        return estudiantes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", id));
    }

    private TrabajoGrado trabajo(Integer id) {
        return trabajos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Trabajo de grado", id));
    }

    private static String normalizarClaves(String s) {
        if (s == null || s.isBlank()) return null;
        return String.join(", ", java.util.Arrays.stream(s.split("[,;]")).map(String::trim).filter(x -> !x.isEmpty())
                .map(String::toLowerCase).distinct().toList());
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
