package bo.edu.uagrm.psicologia.documental.servicio;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ActualizarCorrespondenciaRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CorrespondenciaDetalleDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CorrespondenciaDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.CrearCorrespondenciaRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.DestinatarioDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.DestinatarioRequest;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.SeguimientoDto;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.SeguimientoRequest;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.modelo.Destinatario;
import bo.edu.uagrm.psicologia.documental.modelo.SeguimientoCorrespondencia;
import bo.edu.uagrm.psicologia.documental.repositorio.ArchivoAdjuntoRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.CorrespondenciaRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.DestinatarioRepository;
import bo.edu.uagrm.psicologia.documental.repositorio.SeguimientoCorrespondenciaRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
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
import java.util.ArrayList;
import java.util.List;

/** CU-05 Gestionar correspondencia institucional y CU-06 Gestionar su seguimiento. */
@Service
public class CorrespondenciaService {

    private final CorrespondenciaRepository correspondencias;
    private final DestinatarioRepository destinatarios;
    private final SeguimientoCorrespondenciaRepository seguimientos;
    private final ArchivoAdjuntoRepository archivos;
    private final UsuarioRepository usuarios;
    private final DocumentoService documentos;
    private final AuditoriaService auditoria;

    public CorrespondenciaService(CorrespondenciaRepository correspondencias, DestinatarioRepository destinatarios,
                                  SeguimientoCorrespondenciaRepository seguimientos, ArchivoAdjuntoRepository archivos,
                                  UsuarioRepository usuarios, DocumentoService documentos, AuditoriaService auditoria) {
        this.correspondencias = correspondencias;
        this.destinatarios = destinatarios;
        this.seguimientos = seguimientos;
        this.archivos = archivos;
        this.usuarios = usuarios;
        this.documentos = documentos;
        this.auditoria = auditoria;
    }

    // ---------- Consulta ----------

    @Transactional(readOnly = true)
    public Pagina<CorrespondenciaDto> listar(String texto, Correspondencia.Tipo tipo, Correspondencia.Estado estado,
                                             Integer idDestinatario, Boolean autorizado, LocalDate desde,
                                             LocalDate hasta, int pagina, int tamano) {
        Specification<Correspondencia> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                var dest = root.join("destinatario", JoinType.INNER);
                p.add(cb.or(cb.like(cb.lower(root.get("codigo")), patron), cb.like(cb.lower(root.get("asunto")), patron),
                        cb.like(cb.lower(dest.get("unidad")), patron), cb.like(cb.lower(dest.get("nombre")), patron)));
            }
            if (tipo != null) p.add(cb.equal(root.get("tipo"), tipo));
            if (estado != null) p.add(cb.equal(root.get("estado"), estado));
            if (idDestinatario != null) p.add(cb.equal(root.get("destinatario").get("id"), idDestinatario));
            if (autorizado != null) p.add(cb.equal(root.get("autorizado"), autorizado));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaDocumento"), desde));
            if (hasta != null) p.add(cb.lessThanOrEqualTo(root.get("fechaDocumento"), hasta));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return Pagina.de(correspondencias.findAll(spec, PageRequest.of(Math.max(pagina, 0),
                        Math.min(Math.max(tamano, 1), 100), Sort.by(Sort.Direction.DESC, "fechaDocumento", "id"))),
                CorrespondenciaDto::de);
    }

    @Transactional(readOnly = true)
    public CorrespondenciaDetalleDto obtener(Integer id) {
        Correspondencia c = buscar(id);
        return new CorrespondenciaDetalleDto(CorrespondenciaDto.de(c),
                archivos.findByIdCorrespondenciaOrderByFechaCargaDesc(id).stream().map(ArchivoDto::de).toList(),
                seguimientos.findByIdCorrespondenciaOrderByFechaDescIdDesc(id).stream().map(SeguimientoDto::de).toList());
    }

    // ---------- Registro (CU-05) ----------

    @Transactional
    public CorrespondenciaDto crear(CrearCorrespondenciaRequest req) {
        Destinatario d = destinatarioActivo(req.idDestinatario());
        String prefijo = req.tipo() == Correspondencia.Tipo.RECIBIDA ? "DCP-REC-" : "DCP-EMI-";
        String codigo = prefijo + LocalDate.now().getYear() + "-" + String.format("%04d", correspondencias.siguienteNumero());
        Correspondencia c = correspondencias.save(new Correspondencia(codigo, req.tipo(), req.asunto().trim(),
                vacioANulo(req.descripcion()), req.fechaDocumento(), d,
                usuarios.getReferenceById(UsuarioActual.id())));
        seguimientos.save(new SeguimientoCorrespondencia(c.getId(), c.getEstado().name(), "Registro inicial del oficio"));
        auditoria.registrar("CREAR", "CORRESPONDENCIA", c.getId(), c.getCodigo() + " · " + c.getAsunto());
        return CorrespondenciaDto.de(c);
    }

    @Transactional
    public CorrespondenciaDto actualizar(Integer id, ActualizarCorrespondenciaRequest req) {
        Correspondencia c = buscar(id);
        exigirNoArchivada(c);
        c.actualizar(req.asunto().trim(), vacioANulo(req.descripcion()), req.fechaDocumento(),
                destinatarioActivo(req.idDestinatario()));
        auditoria.registrar("ACTUALIZAR", "CORRESPONDENCIA", c.getId(), c.getCodigo());
        return CorrespondenciaDto.de(c);
    }

    /** Marca el oficio como autorizado (firmado/visado): solo los autorizados entran a la consolidación RPA. */
    @Transactional
    public CorrespondenciaDto autorizar(Integer id, boolean autorizado) {
        Correspondencia c = buscar(id);
        exigirNoArchivada(c);
        c.autorizar(autorizado);
        auditoria.registrar(autorizado ? "AUTORIZAR" : "QUITAR_AUTORIZACION", "CORRESPONDENCIA", c.getId(), c.getCodigo());
        return CorrespondenciaDto.de(c);
    }

    @Transactional
    public ArchivoDto adjuntar(Integer id, MultipartFile archivo, String descripcion) {
        Correspondencia c = buscar(id);
        exigirNoArchivada(c);
        return ArchivoDto.de(documentos.guardar(archivo, "correspondencia", c.getId(), null, descripcion));
    }

    // ---------- Seguimiento (CU-06) ----------

    @Transactional
    public SeguimientoDto registrarSeguimiento(Integer id, SeguimientoRequest req) {
        Correspondencia c = buscar(id);
        exigirNoArchivada(c);
        Correspondencia.Estado anterior = c.getEstado();
        c.cambiarEstado(req.estado());
        SeguimientoCorrespondencia s = seguimientos.save(
                new SeguimientoCorrespondencia(c.getId(), req.estado().name(), req.observacion().trim()));
        auditoria.registrar("SEGUIMIENTO", "CORRESPONDENCIA", c.getId(),
                c.getCodigo() + ": " + anterior + " -> " + req.estado() + ". " + req.observacion().trim());
        return SeguimientoDto.de(s);
    }

    // ---------- Destinatarios ----------

    @Transactional(readOnly = true)
    public List<DestinatarioDto> destinatarios() {
        return destinatarios.findAllByOrderByUnidadAscNombreAsc().stream().map(DestinatarioDto::de).toList();
    }

    @Transactional
    public DestinatarioDto guardarDestinatario(Integer id, DestinatarioRequest req) {
        Destinatario d;
        if (id == null) {
            d = destinatarios.save(new Destinatario(req.nombre().trim(), vacioANulo(req.cargo()), req.unidad().trim(),
                    vacioANulo(req.institucion()), vacioANulo(req.correo())));
        } else {
            d = destinatarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Destinatario", id));
            d.actualizar(req.nombre().trim(), vacioANulo(req.cargo()), req.unidad().trim(), vacioANulo(req.institucion()),
                    vacioANulo(req.correo()), req.activo() == null || req.activo());
        }
        auditoria.registrar(id == null ? "CREAR" : "ACTUALIZAR", "DESTINATARIO", d.getId(), d.getUnidad() + " · " + d.getNombre());
        return DestinatarioDto.de(d);
    }

    // ---------- Apoyo ----------

    private Correspondencia buscar(Integer id) {
        return correspondencias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Correspondencia", id));
    }

    private Destinatario destinatarioActivo(Integer id) {
        Destinatario d = destinatarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Destinatario", id));
        if (!d.isActivo()) throw new NegocioException("El destinatario seleccionado está inactivo");
        return d;
    }

    private static void exigirNoArchivada(Correspondencia c) {
        if (c.getEstado() == Correspondencia.Estado.ARCHIVADA) {
            throw new NegocioException(HttpStatus.CONFLICT, "La correspondencia " + c.getCodigo() + " está archivada y no admite cambios");
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
