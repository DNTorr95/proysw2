package bo.edu.uagrm.psicologia.institucional;

import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import bo.edu.uagrm.psicologia.institucional.modelo.Poa;
import bo.edu.uagrm.psicologia.seguridad.modelo.TransferenciaAutoridad;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas puras de POA (CU-19) y transferencia de autoridad (CU-04). */
class ReglasInstitucionalesTest {

    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    private static ActividadPoa actividad(Poa p, String inicio, String fin) {
        return new ActividadPoa(p, "Actividad", "Meta", null, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    @Test
    void estadoDeActividadSegunAvanceYFechas() {
        Poa p = new Poa(2026, "Objetivo");
        ActividadPoa futura = actividad(p, "2026-09-01", "2026-12-01");
        futura.recalcularEstado(HOY);
        assertThat(futura.getEstado()).isEqualTo(ActividadPoa.PENDIENTE);

        ActividadPoa enCurso = actividad(p, "2026-03-01", "2026-12-01");
        enCurso.registrarAvance(new BigDecimal("40"), HOY);
        assertThat(enCurso.getEstado()).isEqualTo(ActividadPoa.EN_CURSO);

        ActividadPoa vencida = actividad(p, "2026-01-10", "2026-05-30");
        vencida.registrarAvance(new BigDecimal("80"), HOY);
        assertThat(vencida.getEstado()).isEqualTo(ActividadPoa.RETRASADA);

        vencida.registrarAvance(new BigDecimal("100"), HOY);
        assertThat(vencida.getEstado()).isEqualTo(ActividadPoa.CONCLUIDA);
    }

    @Test
    void avanceDelPoaEsElPromedioDeSusActividades() {
        Poa p = new Poa(2026, "Objetivo");
        assertThat(p.avance()).isEqualByComparingTo("0");
        ActividadPoa a = actividad(p, "2026-01-01", "2026-12-01");
        ActividadPoa b = actividad(p, "2026-01-01", "2026-12-01");
        ActividadPoa c = actividad(p, "2026-01-01", "2026-12-01");
        a.registrarAvance(new BigDecimal("100"), HOY);
        b.registrarAvance(new BigDecimal("50"), HOY);
        p.getActividades().addAll(java.util.List.of(a, b, c));
        assertThat(p.avance()).isEqualByComparingTo("50.00");
    }

    @Test
    void transferenciaActivaSoloDentroDeSusFechasYMientrasEsteVigente() {
        TransferenciaAutoridad t = new TransferenciaAutoridad(null, null, "Viaje oficial", null,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20));
        assertThat(t.activaEn(LocalDate.of(2026, 6, 9))).isFalse();
        assertThat(t.activaEn(LocalDate.of(2026, 6, 10))).isTrue();
        assertThat(t.activaEn(LocalDate.of(2026, 6, 20))).isTrue();
        assertThat(t.activaEn(LocalDate.of(2026, 6, 21))).isFalse();
        t.revocar(LocalDate.of(2026, 6, 15));
        assertThat(t.activaEn(LocalDate.of(2026, 6, 15))).isFalse();
        assertThat(t.getEstado()).isEqualTo(TransferenciaAutoridad.REVOCADA);

        TransferenciaAutoridad indefinida = new TransferenciaAutoridad(null, null, "Licencia", null, LocalDate.of(2026, 1, 1), null);
        assertThat(indefinida.activaEn(LocalDate.of(2030, 1, 1))).isTrue();
    }
}
