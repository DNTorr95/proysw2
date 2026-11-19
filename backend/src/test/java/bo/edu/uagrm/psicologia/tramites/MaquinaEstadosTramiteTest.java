package bo.edu.uagrm.psicologia.tramites;

import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.tramites.maquina.ContextoTransicion;
import bo.edu.uagrm.psicologia.tramites.maquina.MaquinaEstadosTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.TransicionEstado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas unitarias de la máquina de estados (mismo catálogo que inserta el script complementario). */
class MaquinaEstadosTramiteTest {

    private static final EstadoTramite RECEP = new EstadoTramite(1, "RECEPCIONADO", false);
    private static final EstadoTramite REV = new EstadoTramite(2, "EN_REVISION", false);
    private static final EstadoTramite OBS = new EstadoTramite(3, "OBSERVADO", false);
    private static final EstadoTramite APR = new EstadoTramite(4, "APROBADO", false);
    private static final EstadoTramite REC = new EstadoTramite(5, "RECHAZADO", false);
    private static final EstadoTramite ARC = new EstadoTramite(6, "ARCHIVADO", true);

    private final MaquinaEstadosTramite maquina = new MaquinaEstadosTramite(
            () -> List.of(
                    new TransicionEstado(1, RECEP, REV, "derivar", "requisitos registrados"),
                    new TransicionEstado(2, REV, OBS, "observar", "justificación obligatoria"),
                    new TransicionEstado(3, OBS, REV, "subsanar", "documentación completa"),
                    new TransicionEstado(4, REV, APR, "aprobar", "rol DIRECTOR_CARRERA"),
                    new TransicionEstado(5, REV, REC, "rechazar", "rol DIRECTOR_CARRERA y justificación"),
                    new TransicionEstado(6, APR, ARC, "archivar", null),
                    new TransicionEstado(7, REC, ARC, "archivar", null)),
            () -> List.of(RECEP, REV, OBS, APR, REC, ARC));

    private static ContextoTransicion coord(String justificacion, int pendientes) {
        return new ContextoTransicion(Rol.COORDINADOR, justificacion, pendientes);
    }

    private static ContextoTransicion director(String justificacion) {
        return new ContextoTransicion(Rol.DIRECTOR, justificacion, 0);
    }

    // ---------- Transiciones válidas ----------

    @Test
    void flujoCompletoConObservacionYAprobacion() {
        assertThat(maquina.disparar("RECEPCIONADO", "derivar", coord(null, 0)).destino()).isEqualTo("EN_REVISION");
        assertThat(maquina.disparar("EN_REVISION", "observar", coord("Falta el récord académico legible", 0)).destino()).isEqualTo("OBSERVADO");
        assertThat(maquina.disparar("OBSERVADO", "subsanar", coord(null, 0)).destino()).isEqualTo("EN_REVISION");
        MaquinaEstadosTramite.Resultado r = maquina.disparar("EN_REVISION", "aprobar", director(null));
        assertThat(r.destino()).isEqualTo("APROBADO");
        assertThat(r.idTransicion()).isEqualTo(4);
        assertThat(maquina.disparar("APROBADO", "archivar", coord(null, 0)).destino()).isEqualTo("ARCHIVADO");
    }

    @Test
    void rechazoConJustificacionYArchivo() {
        assertThat(maquina.disparar("EN_REVISION", "rechazar", director("No cumple el artículo 12 del reglamento")).destino())
                .isEqualTo("RECHAZADO");
        MaquinaEstadosTramite.Resultado r = maquina.disparar("RECHAZADO", "archivar", coord(null, 0));
        assertThat(r.destino()).isEqualTo("ARCHIVADO");
        assertThat(r.idTransicion()).isEqualTo(7);
    }

    // ---------- Transiciones inválidas (no existen en el catálogo) ----------

    @ParameterizedTest(name = "{1} desde {0} no está permitido")
    @CsvSource({
            "RECEPCIONADO, aprobar",
            "RECEPCIONADO, archivar",
            "RECEPCIONADO, observar",
            "EN_REVISION, derivar",
            "EN_REVISION, archivar",
            "OBSERVADO, aprobar",
            "APROBADO, rechazar",
            "APROBADO, derivar",
            "ARCHIVADO, derivar",
            "ARCHIVADO, archivar",
            "EN_REVISION, inventado",
    })
    void transicionesInexistentesSeRechazanCon409(String estado, String evento) {
        assertThatThrownBy(() -> maquina.disparar(estado, evento, director("Justificación suficientemente larga")))
                .isInstanceOf(NegocioException.class)
                .satisfies(e -> assertThat(((NegocioException) e).getEstado()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------- Guardas ----------

    @Test
    void derivarSinRequisitosEsRechazado() {
        assertThatThrownBy(() -> maquina.disparar("RECEPCIONADO", "derivar", coord(null, 2)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("faltan 2 requisito");
    }

    @Test
    void observarSinJustificacionEsRechazado() {
        assertThatThrownBy(() -> maquina.disparar("EN_REVISION", "observar", coord("corta", 0)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("justificación")
                .satisfies(e -> assertThat(((NegocioException) e).getEstado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void subsanarConDocumentacionIncompletaEsRechazado() {
        assertThatThrownBy(() -> maquina.disparar("OBSERVADO", "subsanar", coord(null, 1)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("incompleta");
    }

    @Test
    void coordinadorNoPuedeAprobarNiRechazar() {
        assertThatThrownBy(() -> maquina.disparar("EN_REVISION", "aprobar", coord(null, 0)))
                .hasMessageContaining("Solo el Director");
        assertThatThrownBy(() -> maquina.disparar("EN_REVISION", "rechazar", coord("Justificación suficientemente larga", 0)))
                .hasMessageContaining("Solo el Director");
    }

    @Test
    void rechazarSinJustificacionEsRechazado() {
        assertThatThrownBy(() -> maquina.disparar("EN_REVISION", "rechazar", director(" ")))
                .hasMessageContaining("justificación");
    }

    @Test
    void eventosDisponiblesPorEstado() {
        assertThat(maquina.eventosDesde("EN_REVISION")).containsExactlyInAnyOrder("observar", "aprobar", "rechazar");
        assertThat(maquina.eventosDesde("ARCHIVADO")).isEmpty();
    }
}
