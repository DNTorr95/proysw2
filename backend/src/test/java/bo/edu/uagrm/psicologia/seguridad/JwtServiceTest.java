package bo.edu.uagrm.psicologia.seguridad;

import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.seguridad.jwt.JwtService;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRETO = "secreto_de_prueba_con_mas_de_treinta_y_dos_caracteres_123";

    private static JwtService servicio(String secreto, int horas) {
        return new JwtService(new PropiedadesApp(
                new PropiedadesApp.Seguridad(secreto, horas, 5, List.of("http://localhost")), null, null, null, null));
    }

    private static Usuario usuario() {
        Rol rol = Mockito.mock(Rol.class);
        Mockito.when(rol.getNombre()).thenReturn(Rol.APOYO);
        Usuario u = Mockito.mock(Usuario.class);
        Mockito.when(u.getId()).thenReturn(42);
        Mockito.when(u.getNombreUsuario()).thenReturn("apoyo");
        Mockito.when(u.getRol()).thenReturn(rol);
        return u;
    }

    @Test
    void tokenValidoDevuelveIdDeUsuarioYExpiraEnOchoHoras() {
        JwtService jwt = servicio(SECRETO, 8);
        JwtService.TokenEmitido t = jwt.emitir(usuario());
        assertThat(jwt.validar(t.token())).contains(42);
        assertThat(Duration.between(Instant.now(), t.expira()).toMinutes()).isBetween(479L, 480L);
    }

    @Test
    void tokenAlteradoEsRechazado() {
        JwtService jwt = servicio(SECRETO, 8);
        String token = jwt.emitir(usuario()).token();
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertThat(jwt.validar(alterado)).isEmpty();
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        String token = servicio(SECRETO, 8).emitir(usuario()).token();
        assertThat(servicio(SECRETO + "_otro", 8).validar(token)).isEmpty();
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService jwt = servicio(SECRETO, 0);   // expira en el mismo instante de emisión
        String token = jwt.emitir(usuario()).token();
        assertThat(jwt.validar(token)).isEmpty();
    }

    @Test
    void basuraEsRechazada() {
        assertThat(servicio(SECRETO, 8).validar("no-es-un-jwt")).isEmpty();
    }

    @Test
    void secretoCortoNoSePermite() {
        assertThatThrownBy(() -> servicio("corto", 8)).isInstanceOf(IllegalStateException.class);
    }
}
