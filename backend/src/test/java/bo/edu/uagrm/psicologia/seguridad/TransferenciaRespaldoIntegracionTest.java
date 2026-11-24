package bo.edu.uagrm.psicologia.seguridad;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-04: documento de respaldo de la transferencia de autoridad (V9): carga, acceso por permiso y protección. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class TransferenciaRespaldoIntegracionTest extends PruebaIntegracionBase {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/pdf", contenido);
    }

    @Test
    void respaldoSeAdjuntaYSoloLoVenQuienesTienenPermiso() throws Exception {
        String director = tokenDirector();
        // Solo puede haber una transferencia vigente: se revoca la que hubiera dejado otra prueba
        for (JsonNode t : json.readTree(mvc.perform(get("/api/transferencias-autoridad").header("Authorization", bearer(director)))
                .andReturn().getResponse().getContentAsString())) {
            if ("VIGENTE".equals(t.get("estado").asText())) {
                mvc.perform(post("/api/transferencias-autoridad/" + t.get("id").asInt() + "/revocacion").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Limpieza de la prueba\"}")).andExpect(status().isOk());
            }
        }
        String r = mvc.perform(post("/api/transferencias-autoridad").header("Authorization", bearer(director)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("idUsuarioDestino", 2, "motivo", "Comisión oficial a congreso nacional",
                                "resolucion", "RD 045/2026", "fechaInicio", LocalDate.now().plusDays(1).toString(),
                                "fechaFin", LocalDate.now().plusDays(5).toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.respaldo").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();
        String url = "/api/transferencias-autoridad/" + id + "/respaldo";

        // Validación del archivo y permiso para adjuntar
        mvc.perform(multipart(url).file(archivo("resolucion.pdf", "texto plano".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(director)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(url).file(archivo("resolucion.pdf", PDF)).header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isForbidden());

        // Carga correcta: queda en la transferencia y en el historial
        String cargada = mvc.perform(multipart(url).file(archivo("resolucion-045.pdf", PDF)).header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.respaldo.nombreArchivo").value("resolucion-045.pdf"))
                .andReturn().getResponse().getContentAsString();
        int idArchivo = json.readTree(cargada).get("respaldo").get("id").asInt();
        mvc.perform(get("/api/transferencias-autoridad").header("Authorization", bearer(director)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].respaldo.id").value(idArchivo));

        // Lectura: el Director (TRANSFERIR_AUTORIDAD) y quien gestiona documentos; el Coordinador no
        byte[] descargado = mvc.perform(get("/api/documentos/" + idArchivo + "/contenido").header("Authorization", bearer(director)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(descargado).isEqualTo(PDF);
        mvc.perform(get("/api/documentos/" + idArchivo + "/contenido").header("Authorization", bearer(tokenApoyo())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/documentos/" + idArchivo + "/contenido").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isForbidden());

        // No puede borrarse desde Documentos digitales mientras sea respaldo
        mvc.perform(delete("/api/documentos/" + idArchivo).header("Authorization", bearer(tokenApoyo())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("respaldo de una transferencia de autoridad")));

        // Revocada, ya no admite cambios de respaldo
        mvc.perform(post("/api/transferencias-autoridad/" + id + "/revocacion").header("Authorization", bearer(director))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Retorno anticipado\"}")).andExpect(status().isOk());
        mvc.perform(multipart(url).file(archivo("otra.pdf", PDF)).header("Authorization", bearer(director)))
                .andExpect(status().isConflict());
    }
}
