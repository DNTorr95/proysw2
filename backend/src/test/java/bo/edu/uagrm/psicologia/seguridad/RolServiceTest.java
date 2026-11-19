package bo.edu.uagrm.psicologia.seguridad;

import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.seguridad.modelo.Permiso;
import bo.edu.uagrm.psicologia.seguridad.modelo.Rol;
import bo.edu.uagrm.psicologia.seguridad.repositorio.PermisoRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.RolRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.RolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolServiceTest {

    private RolRepository roles;
    private PermisoRepository permisos;
    private AuditoriaService auditoria;
    private RolService servicio;
    private Rol director;

    @BeforeEach
    void preparar() {
        roles = mock(RolRepository.class);
        permisos = mock(PermisoRepository.class);
        auditoria = mock(AuditoriaService.class);
        servicio = new RolService(roles, permisos, mock(UsuarioRepository.class), auditoria);
        director = mock(Rol.class);
        when(director.getId()).thenReturn(1);
        when(director.getNombre()).thenReturn(Rol.DIRECTOR);
        when(director.getPermisos()).thenReturn(new HashSet<>());
        when(roles.findById(1)).thenReturn(Optional.of(director));
    }

    private static Permiso permiso(String codigo) {
        Permiso p = mock(Permiso.class);
        when(p.getCodigo()).thenReturn(codigo);
        return p;
    }

    @Test
    void directorNoPuedePerderPermisosDeAdministracion() {
        List<Permiso> encontrados = List.of(permiso("VER_DASHBOARD"));
        when(permisos.findByCodigoIn(any())).thenReturn(encontrados);
        assertThatThrownBy(() -> servicio.actualizarPermisos(1, Set.of("VER_DASHBOARD")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("DIRECTOR_CARRERA debe conservar");
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), anyString());
    }

    @Test
    void permisoInexistenteEsRechazado() {
        List<Permiso> encontrados = List.of(permiso("GESTIONAR_USUARIOS"));
        when(permisos.findByCodigoIn(any())).thenReturn(encontrados);
        assertThatThrownBy(() -> servicio.actualizarPermisos(1, Set.of("GESTIONAR_USUARIOS", "INVENTADO")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("INVENTADO");
    }

    @Test
    void cambioValidoSeAudita() {
        Set<String> codigos = Set.of("GESTIONAR_USUARIOS", "GESTIONAR_ROLES_PERMISOS", "VER_DASHBOARD");
        List<Permiso> encontrados = codigos.stream().map(RolServiceTest::permiso).toList();
        when(permisos.findByCodigoIn(any())).thenReturn(encontrados);
        servicio.actualizarPermisos(1, codigos);
        verify(director).reemplazarPermisos(any());
        verify(auditoria).registrar(eq("ACTUALIZAR_PERMISOS"), eq("ROL"), eq(1), anyString());
    }
}
