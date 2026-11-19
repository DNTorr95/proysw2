package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-03 / HU-22: el texto extraído por OCR solo es buscable después de que una persona lo valida (CU-22).
 * Antes de validar, el documento no aparece por su texto ni se muestra un fragmento sin revisar,
 * y el texto tampoco entra en el corpus de la búsqueda semántica.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class BusquedaOcrValidadoIntegracionTest extends PruebaIntegracionBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @MockitoBean
    private ClienteServiciosIa ia;

    @Autowired
    private BusquedaService busqueda;

    private JsonNode nodo(Object valor) {
        return json.valueToTree(valor);
    }

    private String textoEnCorpus(int idArchivo) {
        return busqueda.corpus().stream().filter(d -> d.get("id").equals("DOC-" + idArchivo)).findFirst().orElseThrow().get("texto");
    }

    @Test
    void textoOcrSoloEsBuscableDespuesDeValidarlo() throws Exception {
        String apoyo = tokenApoyo();
        String r = mvc.perform(multipart("/api/documentos").file(new MockMultipartFile("archivo", "acta-colibri.png", "image/png", PNG))
                        .param("descripcion", "Acta escaneada").header("Authorization", bearer(apoyo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();
        when(ia.post(eq("/interno/ocr"), any()))
                .thenReturn(nodo(Map.of("texto", "Convenio con el centro comunitario, control tucanqui", "confianza", 91.0)));
        mvc.perform(post("/api/ocr/documentos/" + id + "/procesamiento").header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validado").value(false));

        // Antes de validar: el texto OCR no encuentra el documento ni entra al índice semántico
        mvc.perform(get("/api/busqueda").param("texto", "tucanqui").header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposConsultados").value(hasItem("DOCUMENTO")))
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'DOCUMENTO' && @.id == " + id + ")]").doesNotExist());
        assertThat(textoEnCorpus(id)).doesNotContain("tucanqui");
        // Si se encuentra por su nombre, el detalle no muestra el texto sin revisar
        mvc.perform(get("/api/busqueda").param("texto", "acta-colibri").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.resultados[0].id").value(id))
                .andExpect(jsonPath("$.resultados[0].detalle").value(containsString("OCR pendiente de validación")))
                .andExpect(jsonPath("$.resultados[0].detalle").value(not(containsString("tucanqui"))));

        // Validación humana con una corrección
        mvc.perform(put("/api/ocr/documentos/" + id + "/validacion").header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Convenio con el centro comunitario, control tucanqui. Revisado por apoyo\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validado").value(true));

        // Después de validar: se encuentra por el texto original y por la corrección
        mvc.perform(get("/api/busqueda").param("texto", "tucanqui").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'DOCUMENTO' && @.id == " + id + ")]").exists())
                .andExpect(jsonPath("$.resultados[?(@.id == " + id + ")].detalle").value(hasItem(containsString("OCR validado"))));
        mvc.perform(get("/api/busqueda").param("texto", "revisado por apoyo").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'DOCUMENTO' && @.id == " + id + ")]").exists());
        assertThat(textoEnCorpus(id)).contains("tucanqui", "Revisado por apoyo");
    }
}
