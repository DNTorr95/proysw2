package bo.edu.uagrm.psicologia.comun;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CU-07: validación de tipo real, tamaño máximo, hash SHA-256 y protección de rutas. */
class AlmacenamientoServiceTest {

    @TempDir
    Path dir;

    private AlmacenamientoService servicio() {
        return new AlmacenamientoService(new PropiedadesApp(null, null, new PropiedadesApp.Almacenamiento(dir.toString()), null, null));
    }

    static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] docx(boolean conDocumento) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (conDocumento) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void guardaPdfConHashYRutaAleatoria() throws IOException {
        AlmacenamientoService s = servicio();
        var g = s.guardar("C:\\Usuarios\\x\\Oficio 15.pdf", pdf(), "correspondencia");
        assertThat(g.nombreOriginal()).isEqualTo("Oficio 15.pdf");
        assertThat(g.tipoMime()).isEqualTo("application/pdf");
        assertThat(g.hashSha256()).hasSize(64).isEqualTo(AlmacenamientoService.sha256(pdf()));
        assertThat(g.ruta()).startsWith("correspondencia/").endsWith(".pdf").doesNotContain("Oficio");
        assertThat(Files.readAllBytes(dir.resolve(g.ruta()))).isEqualTo(pdf());
        assertThat(s.leer(g.ruta())).isEqualTo(pdf());
    }

    @Test
    void aceptaPngJpgYDocx() throws IOException {
        AlmacenamientoService s = servicio();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertThat(s.guardar("a.png", png, "x").tipoMime()).isEqualTo("image/png");
        assertThat(s.guardar("a.JPEG", jpg, "x").tipoMime()).isEqualTo("image/jpeg");
        assertThat(s.guardar("a.docx", docx(true), "x").tipoMime()).contains("wordprocessingml");
    }

    @Test
    void rechazaExtensionNoPermitida() {
        assertThatThrownBy(() -> servicio().guardar("virus.exe", new byte[]{'M', 'Z', 0}, "x"))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getEstado()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void rechazaContenidoQueNoCoincideConLaExtension() throws IOException {
        assertThatThrownBy(() -> servicio().guardar("falso.pdf", new byte[]{'M', 'Z', 1, 2, 3}, "x"))
                .hasMessageContaining("no corresponde");
        assertThatThrownBy(() -> servicio().guardar("falso.docx", docx(false), "x"))
                .hasMessageContaining("no corresponde");
    }

    @Test
    void rechazaArchivosMayoresA10Mb() {
        byte[] grande = new byte[(int) AlmacenamientoService.TAMANO_MAXIMO + 1];
        System.arraycopy(pdf(), 0, grande, 0, 4);
        assertThatThrownBy(() -> servicio().guardar("grande.pdf", grande, "x"))
                .satisfies(e -> assertThat(((NegocioException) e).getEstado()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rechazaArchivoVacio() {
        assertThatThrownBy(() -> servicio().guardar("vacio.pdf", new byte[0], "x")).hasMessageContaining("vacío");
    }

    @Test
    void impideSalirDelVolumen() {
        assertThatThrownBy(() -> servicio().leer("../../etc/passwd"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("inválida");
    }
}
