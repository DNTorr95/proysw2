package bo.edu.uagrm.psicologia.documental;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-01 / HU-06: un oficio de respuesta o de reiteración se vincula con el oficio original (por su código)
 * y el vínculo se ve desde los dos oficios. Se validan autovínculos, duplicados, códigos inexistentes y permisos.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class VinculosCorrespondenciaIntegracionTest extends PruebaIntegracionBase {

    private JsonNode crearOficio(String token, String tipo, String asunto) throws Exception {
        String r = mvc.perform(post("/api/correspondencia").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", tipo, "asunto", asunto,
                                "fechaDocumento", LocalDate.now().toString(), "idDestinatario", 3))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r);
    }

    private String vinculo(String tipo, String codigo) throws Exception {
        return json.writeValueAsString(Map.of("tipo", tipo, "codigo", codigo));
    }

    @Test
    void respuestaYReiteracionQuedanVinculadasAlOficioOriginal() throws Exception {
        String apoyo = tokenApoyo();
        JsonNode original = crearOficio(apoyo, "RECIBIDA", "Solicitud de informe de matrícula");
        JsonNode respuesta = crearOficio(apoyo, "EMITIDA", "Respuesta a la solicitud de informe de matrícula");
        JsonNode reiteracion = crearOficio(apoyo, "RECIBIDA", "Reiteración de la solicitud de informe de matrícula");
        int idOriginal = original.get("id").asInt();
        String urlOriginal = "/api/correspondencia/" + idOriginal + "/vinculos";

        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("RESPUESTA", respuesta.get("codigo").asText())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RESPUESTA"))
                .andExpect(jsonPath("$.sentido").value("VINCULADO"))
                .andExpect(jsonPath("$.codigo").value(respuesta.get("codigo").asText()));
        // el código se busca sin distinguir mayúsculas
        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("REITERACION", reiteracion.get("codigo").asText().toLowerCase())))
                .andExpect(status().isCreated());

        // Desde el original se ven la respuesta y la reiteración
        mvc.perform(get(urlOriginal).header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].tipo").value(hasItems("RESPUESTA", "REITERACION")))
                .andExpect(jsonPath("$[*].sentido").value(hasItems("VINCULADO")));
        // Desde la respuesta se ve el oficio original
        mvc.perform(get("/api/correspondencia/" + respuesta.get("id").asInt() + "/vinculos").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sentido").value("ORIGINAL"))
                .andExpect(jsonPath("$[0].codigo").value(original.get("codigo").asText()))
                .andExpect(jsonPath("$[0].registradoPor").value("apoyo"));

        // Validaciones: consigo mismo, duplicado (también en sentido inverso), código inexistente y tipo inválido
        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("RESPUESTA", original.get("codigo").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Un oficio no puede vincularse consigo mismo"));
        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("REITERACION", respuesta.get("codigo").asText())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("ya están vinculados")));
        mvc.perform(post("/api/correspondencia/" + respuesta.get("id").asInt() + "/vinculos").header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON).content(vinculo("RESPUESTA", original.get("codigo").asText())))
                .andExpect(status().isConflict());
        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("RESPUESTA", "DCP-EMI-1999-9999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un oficio con el código DCP-EMI-1999-9999"));
        mvc.perform(post(urlOriginal).header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + respuesta.get("codigo").asText() + "\"}"))
                .andExpect(status().isBadRequest());

        // Permisos: el coordinador consulta pero no vincula; sin token no hay acceso
        String coord = tokenCoordinador();
        mvc.perform(get(urlOriginal).header("Authorization", bearer(coord))).andExpect(status().isOk());
        mvc.perform(post(urlOriginal).header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content(vinculo("RESPUESTA", reiteracion.get("codigo").asText())))
                .andExpect(status().isForbidden());
        mvc.perform(get(urlOriginal)).andExpect(status().isUnauthorized());

        // La acción queda en la auditoría
        mvc.perform(get("/api/auditoria").param("accion", "VINCULAR").param("entidad", "CORRESPONDENCIA")
                        .header("Authorization", bearer(tokenDirector())))
                .andExpect(jsonPath("$.contenido[0].idEntidad").value(idOriginal))
                .andExpect(jsonPath("$.contenido[0].detalle").value(containsString(
                        original.get("codigo").asText() + " <- REITERACION " + reiteracion.get("codigo").asText())));
    }
}
