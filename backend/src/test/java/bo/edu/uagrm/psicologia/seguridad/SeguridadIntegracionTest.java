package bo.edu.uagrm.psicologia.seguridad;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.auditoria.BitacoraAuditoriaRepository;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pruebas de integración y de seguridad de CU-01, CU-02 y CU-03 sobre PostgreSQL real. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class SeguridadIntegracionTest extends PruebaIntegracionBase {

    @Autowired
    private BitacoraAuditoriaRepository bitacora;

    @Autowired
    private UsuarioRepository usuarios;

    /** Matriz de acceso: cada rol solo accede a lo suyo (200 permitido, 403 denegado, 401 sin sesión). */
    @ParameterizedTest(name = "{0} {1} -> director {2}, coordinador {3}, apoyo {4}, anónimo {5}")
    @CsvSource({
            "GET, /api/usuarios,   200, 403, 403, 401",
            "GET, /api/roles,      200, 403, 403, 401",
            "GET, /api/permisos,   200, 403, 403, 401",
            "GET, /api/auditoria,  200, 403, 403, 401",
            "GET, /api/auditoria/catalogos, 200, 403, 403, 401",
            "GET, /api/auth/perfil, 200, 200, 200, 401",
            // Iteración 2
            "GET, /api/correspondencia, 200, 200, 200, 401",
            "GET, /api/destinatarios,   200, 200, 200, 401",
            "GET, /api/documentos,      403, 403, 200, 401",
            "GET, /api/expedientes,     200, 200, 200, 401",
            "GET, /api/tramites,        200, 200, 200, 401",
            "GET, /api/tipos-tramite,   200, 200, 200, 401",
            "GET, /api/estudiantes,     200, 200, 200, 401",
            // Iteración 3
            "GET, /api/rpa/ejecuciones, 200, 200, 200, 401",
            "GET, /api/rpa/informes,    200, 200, 200, 401",
            "GET, /api/rpa/metricas,    200, 200, 200, 401",
            "GET, /api/rpa/procesos,    200, 200, 200, 401",
            "GET, /api/rpa/revisores,   403, 200, 200, 401",
            "GET, /api/consultas/correspondencia, 200, 200, 200, 401",
            "GET, /api/consultas/tramites,        200, 200, 200, 401",
            "GET, /api/notificaciones,  200, 200, 200, 401",
            // Iteración 4
            "GET, /api/periodos,        200, 200, 200, 401",
            "GET, /api/materias,        200, 200, 200, 401",
            "GET, /api/migraciones,     200, 200, 200, 401",
            "GET, /api/docentes,        200, 200, 200, 401",
            "GET, /api/titulados,       200, 200, 200, 401",
            "GET, /api/trabajos-grado,  200, 200, 200, 401",
            "GET, /api/poa,             200, 200, 403, 401",
            "GET, /api/eventos,         200, 200, 200, 401",
            "GET, /api/transferencias-autoridad, 200, 403, 403, 401",
            // Iteración 5
            "GET, /api/ocr/documentos,  403, 403, 200, 401",
            "GET, /api/dashboard,       200, 200, 403, 401",
            "GET, /api/alertas,         200, 200, 403, 401",
            "GET, /api/alertas/resumen, 200, 200, 403, 401",
    })
    void matrizDeAcceso(String metodo, String ruta, int director, int coordinador, int apoyo, int anonimo) throws Exception {
        assertThat(estado(ruta, tokenDirector())).isEqualTo(director);
        assertThat(estado(ruta, tokenCoordinador())).isEqualTo(coordinador);
        assertThat(estado(ruta, tokenApoyo())).isEqualTo(apoyo);
        assertThat(estado(ruta, null)).isEqualTo(anonimo);
    }

    private int estado(String ruta, String token) throws Exception {
        var req = get(ruta);
        if (token != null) req.header("Authorization", bearer(token));
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void apoyoNoPuedeModificarPermisosNiUsuarios() throws Exception {
        String t = tokenApoyo();
        mvc.perform(put("/api/roles/3/permisos").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permisos\":[\"CONSULTAR_AUDITORIA\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/1/estado").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BLOQUEADO\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * Regresión: con la auditoría en transacción anidada sobre el mismo pool, 20+ inicios de sesión simultáneos
     * agotaban las conexiones y se bloqueaban mutuamente. Deben completarse todos y quedar auditados.
     */
    @Test
    void iniciosDeSesionConcurrentesNoBloqueanElPool() throws Exception {
        long antes = bitacora.count();
        var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(30);
        try {
            var tareas = new java.util.ArrayList<java.util.concurrent.Callable<Integer>>();
            for (int i = 0; i < 30; i++) {
                String[][] cuentas = {{"director", "Director2026*"}, {"coordinador", "Coordinador2026*"}, {"apoyo", "Apoyo2026*"}};
                String[] c = cuentas[i % 3];
                tareas.add(() -> mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombreUsuario\":\"" + c[0] + "\",\"contrasena\":\"" + c[1] + "\"}"))
                        .andReturn().getResponse().getStatus());
            }
            long t0 = System.currentTimeMillis();
            var resultados = ejecutor.invokeAll(tareas, 20, java.util.concurrent.TimeUnit.SECONDS);
            for (var r : resultados) {
                assertThat(r.isCancelled()).as("login bloqueado").isFalse();
                assertThat(r.get()).isEqualTo(200);
            }
            assertThat(System.currentTimeMillis() - t0).isLessThan(15_000);
        } finally {
            ejecutor.shutdownNow();
        }
        assertThat(bitacora.count() - antes).isGreaterThanOrEqualTo(30);
    }

    @Test
    void tokenInvalidoEsRechazado() throws Exception {
        mvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDevuelvePerfilConPermisosDelRol() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"apoyo\",\"contrasena\":\"Apoyo2026*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.perfil.rol").value("APOYO_PROFESIONAL"))
                .andExpect(jsonPath("$.perfil.permisos[?(@ == 'EJECUTAR_RPA')]").exists())
                .andExpect(jsonPath("$.perfil.permisos[?(@ == 'GESTIONAR_USUARIOS')]").doesNotExist());
    }

    /** CU-01: tras 5 intentos fallidos la cuenta se bloquea, se audita y el Director puede desbloquearla. */
    @Test
    void bloqueoTrasCincoIntentosYDesbloqueoPorDirector() throws Exception {
        String director = tokenDirector();
        String nombre = "u" + UUID.randomUUID().toString().substring(0, 8);
        String crear = json.writeValueAsString(Map.of(
                "ci", "CI" + nombre.substring(1), "nombres", "Prueba", "apellidos", "Bloqueo",
                "correo", nombre + "@ejemplo.edu.bo", "cargo", "Apoyo", "nombreUsuario", nombre,
                "contrasenaInicial", "Inicial2026", "idRol", 3));
        String resp = mvc.perform(post("/api/usuarios").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content(crear))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = json.readTree(resp).get("id").asInt();

        String malo = json.writeValueAsString(Map.of("nombreUsuario", nombre, "contrasena", "incorrecta1"));
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isLocked());
        // Con la contraseña correcta también se rechaza mientras siga bloqueada
        String bueno = json.writeValueAsString(Map.of("nombreUsuario", nombre, "contrasena", "Inicial2026"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bueno))
                .andExpect(status().isLocked());

        assertThat(usuarios.findById(id).orElseThrow().getEstado().name()).isEqualTo("BLOQUEADO");
        assertThat(bitacora.findAll()).anyMatch(b -> "CUENTA_BLOQUEADA".equals(b.getAccion())
                && String.valueOf(id).equals(b.getIdEntidad()));

        mvc.perform(patch("/api/usuarios/" + id + "/estado").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ACTIVO\",\"motivo\":\"Identidad verificada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentosFallidos").value(0));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bueno))
                .andExpect(status().isOk());
        assertThat(bitacora.findAll()).anyMatch(b -> "DESBLOQUEAR".equals(b.getAccion())
                && String.valueOf(id).equals(b.getIdEntidad()) && b.getIdUsuario() == 1);
    }

    /** CU-02: el cambio de permisos rige de inmediato, incluso con un token ya emitido. */
    @Test
    void cambioDePermisosRigeDeInmediato() throws Exception {
        String director = tokenDirector();
        String apoyo = tokenApoyo();
        String original = mvc.perform(get("/api/roles").header("Authorization", bearer(director)))
                .andReturn().getResponse().getContentAsString();
        var permisosApoyo = new java.util.TreeSet<String>();
        json.readTree(original).forEach(r -> {
            if (r.get("id").asInt() == 3) r.get("permisos").forEach(p -> permisosApoyo.add(p.asText()));
        });

        var ampliados = new java.util.TreeSet<>(permisosApoyo);
        ampliados.add("CONSULTAR_AUDITORIA");
        try {
            mvc.perform(put("/api/roles/3/permisos").header("Authorization", bearer(director))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("permisos", ampliados))))
                    .andExpect(status().isOk());
            assertThat(estado("/api/auditoria", apoyo)).isEqualTo(200);
        } finally {
            mvc.perform(put("/api/roles/3/permisos").header("Authorization", bearer(director))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("permisos", permisosApoyo))))
                    .andExpect(status().isOk());
        }
        assertThat(estado("/api/auditoria", apoyo)).isEqualTo(403);
    }

    @Test
    void directorNoPuedeQuitarseLaAdministracion() throws Exception {
        mvc.perform(put("/api/roles/1/permisos").header("Authorization", bearer(tokenDirector()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permisos\":[\"VER_DASHBOARD\"]}"))
                .andExpect(status().isConflict());
    }

    /** CU-03: la bitácora es de solo lectura y filtra por acción. */
    @Test
    void auditoriaFiltraYNoAdmiteEscritura() throws Exception {
        String director = tokenDirector();
        mvc.perform(get("/api/auditoria").param("accion", "LOGIN").header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].accion").value("LOGIN"))
                .andExpect(jsonPath("$.contenido[0].direccionIp").isNotEmpty());
        int estadoPost = mvc.perform(post("/api/auditoria").header("Authorization", bearer(director))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
        assertThat(estadoPost).isIn(404, 405);
    }
}
