package bo.edu.uagrm.psicologia.comun.almacenamiento;

import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Volumen documental (/var/storage/documentos). Valida tipo y tamaño de cada archivo,
 * calcula su hash SHA-256 y lo guarda con un nombre aleatorio (nunca con el nombre del usuario).
 * Tipos admitidos: PDF, JPG, PNG y DOCX; máximo 10 MB.
 */
@Service
public class AlmacenamientoService {

    public static final long TAMANO_MAXIMO = 10L * 1024 * 1024;

    /** Permisos de los archivos guardados: lectura y escritura del dueño, lectura del grupo (0640). */
    public static final Set<PosixFilePermission> PERMISOS_ARCHIVO = Set.copyOf(PosixFilePermissions.fromString("rw-r-----"));

    /** Extensión permitida -> tipo MIME canónico. */
    static final Map<String, String> TIPOS = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final Path base;

    public AlmacenamientoService(PropiedadesApp props) {
        this.base = Path.of(props.almacenamiento().directorio()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de almacenamiento " + base, e);
        }
    }

    /** Resultado de guardar un archivo: ruta relativa al volumen y metadatos verificados. */
    public record ArchivoGuardado(String nombreOriginal, String ruta, String tipoMime, long tamanoBytes, String hashSha256) {
    }

    public ArchivoGuardado guardar(MultipartFile archivo, String carpeta) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("Debe adjuntar un archivo");
        }
        try {
            return guardar(archivo.getOriginalFilename(), archivo.getBytes(), carpeta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ArchivoGuardado guardar(String nombreOriginal, byte[] contenido, String carpeta) {
        String nombre = limpiarNombre(nombreOriginal);
        if (contenido.length == 0) {
            throw new NegocioException("El archivo está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new NegocioException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo de 10 MB");
        }
        String extension = extension(nombre);
        String mime = TIPOS.get(extension);
        if (mime == null) {
            throw new NegocioException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de archivo no permitido. Solo PDF, JPG, PNG o DOCX");
        }
        if (!contenidoCoincide(extension, contenido)) {
            throw new NegocioException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "El contenido del archivo no corresponde a la extensión ." + extension);
        }

        LocalDate hoy = LocalDate.now();
        String relativa = String.format("%s/%d/%02d/%s.%s", carpeta, hoy.getYear(), hoy.getMonthValue(),
                UUID.randomUUID(), extension.equals("jpeg") ? "jpg" : extension);
        Path destino = resolver(relativa);
        try {
            Files.createDirectories(destino.getParent());
            Path temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            Files.write(temporal, contenido);
            aplicarPermisos(temporal);
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
        return new ArchivoGuardado(nombre, relativa, mime, contenido.length, sha256(contenido));
    }

    public InputStream abrir(String ruta) {
        Path p = resolver(ruta);
        if (!Files.isRegularFile(p)) {
            throw new NegocioException(HttpStatus.NOT_FOUND, "El archivo no existe en el volumen documental");
        }
        try {
            return Files.newInputStream(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] leer(String ruta) {
        try (InputStream in = abrir(ruta)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void eliminar(String ruta) {
        try {
            Files.deleteIfExists(resolver(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path directorioBase() {
        return base;
    }

    /**
     * createTempFile crea el archivo con 0600 y ATOMIC_MOVE conserva ese permiso, por lo que los
     * servicios inteligentes (otro usuario, mismo grupo del volumen) no podían leerlo para el OCR.
     * Se deja lectura para el grupo y ningún permiso para otros (0640). En sistemas sin POSIX no aplica.
     */
    private static void aplicarPermisos(Path archivo) throws IOException {
        if (archivo.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(archivo, PERMISOS_ARCHIVO);
        }
    }

    /** Resuelve una ruta relativa impidiendo salir del volumen (path traversal). */
    Path resolver(String relativa) {
        Path p = base.resolve(relativa).normalize();
        if (!p.startsWith(base)) {
            throw new NegocioException(HttpStatus.BAD_REQUEST, "Ruta de archivo inválida");
        }
        return p;
    }

    public static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String extension(String nombre) {
        int i = nombre.lastIndexOf('.');
        return i < 0 ? "" : nombre.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new NegocioException("El archivo debe tener nombre");
        }
        String soloNombre = nombre.replace('\\', '/');
        soloNombre = soloNombre.substring(soloNombre.lastIndexOf('/') + 1);
        soloNombre = soloNombre.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        return soloNombre.length() > 200 ? soloNombre.substring(soloNombre.length() - 200) : soloNombre;
    }

    /** Verifica la firma binaria ("magic bytes") del archivo. */
    static boolean contenidoCoincide(String extension, byte[] c) {
        return switch (extension) {
            case "pdf" -> empiezaCon(c, 0x25, 0x50, 0x44, 0x46);                       // %PDF
            case "png" -> empiezaCon(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> empiezaCon(c, 0xFF, 0xD8, 0xFF);
            case "docx" -> empiezaCon(c, 0x50, 0x4B, 0x03, 0x04) && esDocumentoWord(c);
            default -> false;
        };
    }

    private static boolean empiezaCon(byte[] c, int... firma) {
        if (c.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if ((c[i] & 0xFF) != firma[i]) return false;
        }
        return true;
    }

    private static boolean esDocumentoWord(byte[] c) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(c))) {
            ZipEntry e;
            int revisadas = 0;
            while ((e = zip.getNextEntry()) != null && revisadas++ < 200) {
                if (e.getName().equals("word/document.xml")) return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
