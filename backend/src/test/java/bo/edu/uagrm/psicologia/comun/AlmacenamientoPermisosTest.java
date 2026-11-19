package bo.edu.uagrm.psicologia.comun;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-02 / HU-21: los archivos del volumen documental deben poder leerse por los servicios inteligentes
 * (otro usuario del mismo grupo) sin quedar abiertos a otros usuarios. Se espera 0640 (rw-r-----).
 * Requiere un sistema de archivos POSIX (Linux, como en los contenedores); en Windows se omite.
 */
class AlmacenamientoPermisosTest {

    @TempDir
    Path dir;

    @Test
    void archivoGuardadoQuedaLegibleParaElGrupoYCerradoParaOtros() throws IOException {
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"), "requiere POSIX");
        AlmacenamientoService servicio = new AlmacenamientoService(
                new PropiedadesApp(null, null, new PropiedadesApp.Almacenamiento(dir.toString()), null, null));
        byte[] pdf = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        AlmacenamientoService.ArchivoGuardado g = servicio.guardar("acta.pdf", pdf, "documentos");

        Set<PosixFilePermission> permisos = Files.getPosixFilePermissions(dir.resolve(g.ruta()));
        assertThat(PosixFilePermissions.toString(permisos)).isEqualTo("rw-r-----");
        assertThat(permisos).contains(PosixFilePermission.GROUP_READ)
                .doesNotContain(PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
                        PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.GROUP_WRITE);
        assertThat(Files.readAllBytes(dir.resolve(g.ruta()))).isEqualTo(pdf);
    }
}
