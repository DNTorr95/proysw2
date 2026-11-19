package bo.edu.uagrm.psicologia.comun.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Respuestas de descarga de archivos con nombre UTF-8 seguro. */
public final class Descargas {

    private Descargas() {
    }

    public static ResponseEntity<byte[]> archivo(String nombre, String tipoMime, byte[] contenido, boolean enLinea) {
        ContentDisposition cd = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nombre, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(tipoMime))
                .contentLength(contenido.length)
                .body(contenido);
    }
}
