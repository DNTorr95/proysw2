package bo.edu.uagrm.psicologia.seguridad;

import bo.edu.uagrm.psicologia.seguridad.modelo.EstadoCuenta;
import bo.edu.uagrm.psicologia.seguridad.modelo.Persona;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla de CU-01: bloqueo tras 5 intentos fallidos consecutivos. */
class UsuarioBloqueoTest {

    private Usuario nuevo() {
        return new Usuario(new Persona("1", "A", "B", null, null), null, "prueba", "hash");
    }

    @Test
    void cuatroIntentosNoBloquean() {
        Usuario u = nuevo();
        for (int i = 0; i < 4; i++) {
            assertThat(u.registrarIntentoFallido(5)).isFalse();
        }
        assertThat(u.getEstado()).isEqualTo(EstadoCuenta.ACTIVO);
        assertThat(u.getIntentosFallidos()).isEqualTo(4);
    }

    @Test
    void quintoIntentoBloquea() {
        Usuario u = nuevo();
        for (int i = 0; i < 4; i++) u.registrarIntentoFallido(5);
        assertThat(u.registrarIntentoFallido(5)).isTrue();
        assertThat(u.getEstado()).isEqualTo(EstadoCuenta.BLOQUEADO);
    }

    @Test
    void accesoExitosoReiniciaContador() {
        Usuario u = nuevo();
        u.registrarIntentoFallido(5);
        u.registrarIntentoFallido(5);
        u.registrarAccesoExitoso();
        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.getUltimoAcceso()).isNotNull();
    }

    @Test
    void desbloquearReiniciaContador() {
        Usuario u = nuevo();
        for (int i = 0; i < 5; i++) u.registrarIntentoFallido(5);
        u.cambiarEstado(EstadoCuenta.ACTIVO);
        assertThat(u.getEstado()).isEqualTo(EstadoCuenta.ACTIVO);
        assertThat(u.getIntentosFallidos()).isZero();
    }
}
