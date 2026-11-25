package bo.edu.uagrm.psicologia.documental.servicio;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.comun.web.Pagina;
import bo.edu.uagrm.psicologia.documental.dto.DocumentalDtos.ArchivoDto;
import bo.edu.uagrm.psicologia.documental.modelo.ArchivoAdjunto;
import bo.edu.uagrm.psicologia.documental.repositorio.ArchivoAdjuntoRepository;
import bo.edu.uagrm.psicologia.institucional.repositorio.TrabajoGradoRepository;
import bo.edu.uagrm.psicologia.ocr.ExtraccionOcrRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import bo.edu.uagrm.psicologia.tramites.modelo.DocumentoTramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.DocumentoTramiteRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * CU-07 Gestionar documentos digitales (PDF/JPG/PNG/DOCX, máx. 10 MB, hash SHA-256).
 * También lo usan correspondencia (adjuntos), expedientes y requisitos de trámite.
 */
@Service
public class DocumentoService {

    private final ArchivoAdjuntoRepository archivos;
    private final DocumentoTramiteRepository documentosTramite;
    private final TrabajoGradoRepository trabajosGrado;
    private final ExtraccionOcrRepository extraccionesOcr;
    private final AlmacenamientoService almacenamiento;
    private final AuditoriaService auditoria;

    public DocumentoService(ArchivoAdjuntoRepository archivos, DocumentoTramiteRepository documentosTramite,
                            TrabajoGradoRepository trabajosGrado, ExtraccionOcrRepository extraccionesOcr,
                            AlmacenamientoService almacenamiento, AuditoriaService auditoria) {
        this.archivos = archivos;
        this.documentosTramite = documentosTramite;
        this.trabajosGrado = trabajosGrado;
        this.extraccionesOcr = extraccionesOcr;
        this.almacenamiento = almacenamiento;
        this.auditoria = auditoria;
    }

    /** Guarda el archivo en el volumen y registra sus metadatos. Si la transacción falla, el archivo se borra. */
    @Transactional
    public ArchivoAdjunto guardar(MultipartFile archivo, String carpeta, Integer idCorrespondencia,
                                  Integer idExpediente, String descripcion) {
        AlmacenamientoService.ArchivoGuardado g = almacenamiento.guardar(archivo, carpeta);
        borrarSiSeRevierte(g.ruta());
        ArchivoAdjunto a = archivos.save(new ArchivoAdjunto(g.nombreOriginal(), g.tipoMime(), g.tamanoBytes(),
                g.hashSha256(), g.ruta(), UsuarioActual.id(), idCorrespondencia, idExpediente, recortar(descripcion)));
        auditoria.registrar("SUBIR_DOCUMENTO", "ARCHIVO_ADJUNTO", a.getId(),
                a.getNombreArchivo() + " (" + a.getTipoMime() + ", " + a.getTamanoBytes() + " bytes, SHA-256 " + a.getHashSha256() + ")");
        return a;
    }

    @Transactional
    public ArchivoDto subir(MultipartFile archivo, String descripcion, Integer idExpediente) {
        return ArchivoDto.de(guardar(archivo, "documentos", null, idExpediente, descripcion));
    }

    @Transactional(readOnly = true)
    public Pagina<ArchivoDto> listar(String texto, String tipoMime, Integer idExpediente, Integer idCorrespondencia,
                                     LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Specification<ArchivoAdjunto> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("nombreArchivo")), patron),
                        cb.like(cb.lower(cb.coalesce(root.get("descripcion"), "")), patron),
                        cb.like(root.get("hashSha256"), texto.trim().toLowerCase() + "%")));
            }
            if (tipoMime != null && !tipoMime.isBlank()) p.add(cb.equal(root.get("tipoMime"), tipoMime));
            if (idExpediente != null) p.add(cb.equal(root.get("idExpediente"), idExpediente));
            if (idCorrespondencia != null) p.add(cb.equal(root.get("idCorrespondencia"), idCorrespondencia));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaCarga"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaCarga"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return Pagina.de(archivos.findAll(spec, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaCarga", "id"))), ArchivoDto::de);
    }

    @Transactional(readOnly = true)
    public ArchivoDto obtener(Integer id) {
        ArchivoAdjunto a = buscar(id);
        verificarAccesoLectura(a);
        return ArchivoDto.de(a);
    }

    /** Contenido para descarga, verificando que el usuario pueda ver el contexto del archivo. */
    @Transactional(readOnly = true)
    public Descarga descargar(Integer id) {
        ArchivoAdjunto a = buscar(id);
        verificarAccesoLectura(a);
        byte[] contenido = almacenamiento.leer(a.getRuta());
        if (!AlmacenamientoService.sha256(contenido).equals(a.getHashSha256())) {
            auditoria.registrar("INTEGRIDAD_FALLIDA", "ARCHIVO_ADJUNTO", a.getId(), "El hash del archivo no coincide con el registrado");
            throw new NegocioException(HttpStatus.CONFLICT, "La integridad del archivo no pudo verificarse (hash distinto)");
        }
        auditoria.registrar("DESCARGAR_DOCUMENTO", "ARCHIVO_ADJUNTO", a.getId(), a.getNombreArchivo());
        return new Descarga(a.getNombreArchivo(), a.getTipoMime(), contenido);
    }

    public record Descarga(String nombre, String tipoMime, byte[] contenido) {
    }

    @Transactional
    public ArchivoDto actualizarDescripcion(Integer id, String descripcion) {
        ArchivoAdjunto a = buscar(id);
        a.actualizarDescripcion(recortar(descripcion));
        auditoria.registrar("ACTUALIZAR_DOCUMENTO", "ARCHIVO_ADJUNTO", a.getId(), "Descripción actualizada");
        return ArchivoDto.de(a);
    }

    @Transactional
    public void eliminar(Integer id) {
        ArchivoAdjunto a = buscar(id);
        if (documentosTramite.existsByArchivoId(id)) {
            throw new NegocioException(HttpStatus.CONFLICT, "El documento está registrado como requisito de un trámite y no puede eliminarse");
        }
        if (trabajosGrado.existsByArchivoId(id)) {
            throw new NegocioException(HttpStatus.CONFLICT, "El documento es el archivo de un trabajo de grado y no puede eliminarse");
        }
        // El texto extraído por OCR se obtuvo de este archivo: sin el archivo no tiene sentido conservarlo
        extraccionesOcr.findByIdArchivo(id).ifPresent(extraccionesOcr::delete);
        extraccionesOcr.flush();
        archivos.delete(a);
        String ruta = a.getRuta();
        // El archivo físico se borra solo si la transacción se confirma
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                almacenamiento.eliminar(ruta);
            }
        });
        auditoria.registrar("ELIMINAR_DOCUMENTO", "ARCHIVO_ADJUNTO", id, a.getNombreArchivo() + " SHA-256 " + a.getHashSha256());
    }

    /**
     * Reglas de lectura: quien gestiona documentos ve todos; si el archivo pertenece a una correspondencia,
     * a un expediente o a un trámite, basta con el permiso de consulta de ese contexto.
     */
    private void verificarAccesoLectura(ArchivoAdjunto a) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        if (u.tienePermiso("GESTIONAR_DOCUMENTOS")) return;
        if (a.getIdCorrespondencia() != null
                && (u.tienePermiso("CONSULTAR_CORRESPONDENCIA") || u.tienePermiso("GESTIONAR_CORRESPONDENCIA"))) return;
        if (a.getIdExpediente() != null
                && (u.tienePermiso("CONSULTAR_EXPEDIENTES") || u.tienePermiso("GESTIONAR_EXPEDIENTES"))) return;
        List<DocumentoTramite> enTramites = documentosTramite.findByArchivoId(a.getId());
        if (!enTramites.isEmpty() && (u.tienePermiso("CONSULTAR_TRAMITES") || u.tienePermiso("GESTIONAR_REQUISITOS"))) return;
        throw new NegocioException(HttpStatus.FORBIDDEN, "No tiene permiso para acceder a este documento");
    }

    private ArchivoAdjunto buscar(Integer id) {
        return archivos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Documento", id));
    }

    private void borrarSiSeRevierte(String ruta) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado == STATUS_ROLLED_BACK) almacenamiento.eliminar(ruta);
                }
            });
        }
    }

    private static String recortar(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
