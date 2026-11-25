package bo.edu.uagrm.psicologia.documental;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import bo.edu.uagrm.psicologia.ocr.ExtraccionOcr;
import bo.edu.uagrm.psicologia.ocr.ExtraccionOcrRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integración de CU-05 a CU-08: correspondencia, seguimiento, documentos y expedientes. */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class DocumentalIntegracionTest extends PruebaIntegracionBase {

    private static final byte[] PDF = "%PDF-1.4 oficio de prueba".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private ExtraccionOcrRepository extraccionesOcr;

    private int crearOficio(String token, String tipo) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("tipo", tipo, "asunto", "Solicitud de ambientes",
                "fechaDocumento", LocalDate.now().toString(), "idDestinatario", 3));
        String r = mvc.perform(post("/api/correspondencia").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.autorizado").value(false))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asInt();
    }

    @Test
    void correspondenciaConCodigoUnicoAdjuntoYSeguimiento() throws Exception {
        String apoyo = tokenApoyo();
        int id1 = crearOficio(apoyo, "RECIBIDA");
        int id2 = crearOficio(apoyo, "EMITIDA");
        String c1 = json.readTree(mvc.perform(get("/api/correspondencia/" + id1).header("Authorization", bearer(apoyo)))
                .andReturn().getResponse().getContentAsString()).get("correspondencia").get("codigo").asText();
        String c2 = json.readTree(mvc.perform(get("/api/correspondencia/" + id2).header("Authorization", bearer(apoyo)))
                .andReturn().getResponse().getContentAsString()).get("correspondencia").get("codigo").asText();
        assertThat(c1).matches("DCP-REC-\\d{4}-\\d{4}");
        assertThat(c2).matches("DCP-EMI-\\d{4}-\\d{4}");
        assertThat(c1.substring(c1.length() - 4)).isNotEqualTo(c2.substring(c2.length() - 4));

        // Adjunto válido con hash
        String adj = mvc.perform(multipart("/api/correspondencia/" + id1 + "/adjuntos")
                        .file(new MockMultipartFile("archivo", "oficio.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hashSha256").value(org.hamcrest.Matchers.hasLength(64)))
                .andReturn().getResponse().getContentAsString();
        int idArchivo = json.readTree(adj).get("id").asInt();

        // Ejecutable disfrazado de PDF
        mvc.perform(multipart("/api/correspondencia/" + id1 + "/adjuntos")
                        .file(new MockMultipartFile("archivo", "oficio.pdf", "application/pdf", new byte[]{'M', 'Z', 0, 1}))
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isUnsupportedMediaType());

        // El Director (consulta) puede descargar; el contenido es idéntico
        mvc.perform(get("/api/documentos/" + idArchivo + "/contenido").header("Authorization", bearer(tokenDirector())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("oficio.pdf")))
                .andExpect(content().bytes(PDF));

        // CU-06 seguimiento
        mvc.perform(post("/api/correspondencia/" + id1 + "/seguimiento").header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"DERIVADA\",\"observacion\":\"Derivado a Coordinación\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/correspondencia/" + id1).header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.correspondencia.estado").value("DERIVADA"))
                .andExpect(jsonPath("$.seguimiento.length()").value(2))
                .andExpect(jsonPath("$.adjuntos.length()").value(1));

        // Autorización para el RPA
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/correspondencia/" + id1 + "/autorizacion")
                        .header("Authorization", bearer(apoyo)).contentType(MediaType.APPLICATION_JSON).content("{\"autorizado\":true}"))
                .andExpect(jsonPath("$.autorizado").value(true));

        // Archivada: no admite cambios
        mvc.perform(post("/api/correspondencia/" + id1 + "/seguimiento").header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ARCHIVADA\",\"observacion\":\"Concluido\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/correspondencia/" + id1).header("Authorization", bearer(apoyo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("asunto", "x", "fechaDocumento", LocalDate.now().toString(), "idDestinatario", 3))))
                .andExpect(status().isConflict());
    }

    @Test
    void permisosDeCorrespondenciaYDocumentos() throws Exception {
        String coord = tokenCoordinador();
        // El Coordinador consulta pero no registra
        mvc.perform(get("/api/correspondencia").header("Authorization", bearer(coord))).andExpect(status().isOk());
        mvc.perform(post("/api/correspondencia").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "RECIBIDA", "asunto", "x",
                                "fechaDocumento", LocalDate.now().toString(), "idDestinatario", 1))))
                .andExpect(status().isForbidden());
        // Gestión de documentos digitales: solo Apoyo
        mvc.perform(get("/api/documentos").header("Authorization", bearer(coord))).andExpect(status().isForbidden());
        mvc.perform(get("/api/documentos").header("Authorization", bearer(tokenApoyo()))).andExpect(status().isOk());
        // Fecha futura inválida
        mvc.perform(post("/api/correspondencia").header("Authorization", bearer(tokenApoyo())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tipo", "RECIBIDA", "asunto", "x",
                                "fechaDocumento", LocalDate.now().plusDays(3).toString(), "idDestinatario", 1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentoDigitalSeSubeDescargaYElimina() throws Exception {
        String apoyo = tokenApoyo();
        String r = mvc.perform(multipart("/api/documentos")
                        .file(new MockMultipartFile("archivo", "resolución.pdf", "application/pdf", PDF))
                        .param("descripcion", "Resolución de Consejo").header("Authorization", bearer(apoyo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();
        mvc.perform(get("/api/documentos").param("texto", "consejo").header("Authorization", bearer(apoyo)))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
        // Un documento suelto no es visible para quien solo consulta trámites
        mvc.perform(get("/api/documentos/" + id + "/contenido").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/documentos/" + id).header("Authorization", bearer(apoyo))).andExpect(status().isNoContent());
        mvc.perform(get("/api/documentos/" + id).header("Authorization", bearer(apoyo))).andExpect(status().isNotFound());
    }

    @Test
    void documentoConTextoOcrSePuedeEliminar() throws Exception {
        String apoyo = tokenApoyo();
        String r = mvc.perform(multipart("/api/documentos")
                        .file(new MockMultipartFile("archivo", "acta escaneada.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();
        ExtraccionOcr ocr = new ExtraccionOcr(id);
        ocr.registrarExtraccion("Acta de reunión del Consejo de Carrera", new BigDecimal("91.50"));
        extraccionesOcr.save(ocr);

        mvc.perform(delete("/api/documentos/" + id).header("Authorization", bearer(apoyo))).andExpect(status().isNoContent());
        mvc.perform(get("/api/documentos/" + id).header("Authorization", bearer(apoyo))).andExpect(status().isNotFound());
        assertThat(extraccionesOcr.findByIdArchivo(id)).isEmpty();
    }

    @Test
    void archivoDeTrabajoDeGradoNoSePuedeEliminar() throws Exception {
        String apoyo = tokenApoyo();
        String r = mvc.perform(multipart("/api/trabajos-grado/2/archivo")
                        .file(new MockMultipartFile("archivo", "tesis.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(apoyo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idArchivo = json.readTree(r).get("archivo").get("id").asInt();

        mvc.perform(delete("/api/documentos/" + idArchivo).header("Authorization", bearer(apoyo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("El documento es el archivo de un trabajo de grado y no puede eliminarse"));
        mvc.perform(get("/api/documentos/" + idArchivo).header("Authorization", bearer(apoyo))).andExpect(status().isOk());
    }

    @Test
    void expedienteAgrupaTramitesYDocumentos() throws Exception {
        String coord = tokenCoordinador();
        String r = mvc.perform(post("/api/expedientes").header("Authorization", bearer(coord)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idEstudiante\":15,\"titulo\":\"Expediente de prueba\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.matchesPattern("EXP-\\d{4}-\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        int id = json.readTree(r).get("id").asInt();

        // Trámite del seed del mismo estudiante (id 15 -> trámites con id_estudiante 15)
        String lista = mvc.perform(get("/api/tramites").param("idEstudiante", "15").header("Authorization", bearer(coord)))
                .andReturn().getResponse().getContentAsString();
        int idTramite = json.readTree(lista).get("contenido").get(0).get("id").asInt();
        mvc.perform(post("/api/expedientes/" + id + "/tramites").header("Authorization", bearer(coord))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idTramite\":" + idTramite + "}"))
                .andExpect(status().isOk());

        // Trámite de otro estudiante: rechazado
        String otra = mvc.perform(get("/api/tramites").param("idEstudiante", "16").header("Authorization", bearer(coord)))
                .andReturn().getResponse().getContentAsString();
        JsonNode otros = json.readTree(otra).get("contenido");
        if (otros.size() > 0) {
            mvc.perform(post("/api/expedientes/" + id + "/tramites").header("Authorization", bearer(coord))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"idTramite\":" + otros.get(0).get("id").asInt() + "}"))
                    .andExpect(status().isConflict());
        }

        mvc.perform(multipart("/api/expedientes/" + id + "/documentos")
                        .file(new MockMultipartFile("archivo", "informe.pdf", "application/pdf", PDF))
                        .header("Authorization", bearer(coord)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/expedientes/" + id).header("Authorization", bearer(coord)))
                .andExpect(jsonPath("$.tramites.length()").value(1))
                .andExpect(jsonPath("$.documentos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // El Apoyo consulta pero no gestiona expedientes
        mvc.perform(get("/api/expedientes/" + id).header("Authorization", bearer(tokenApoyo()))).andExpect(status().isOk());
        mvc.perform(post("/api/expedientes").header("Authorization", bearer(tokenApoyo())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"x\"}")).andExpect(status().isForbidden());
    }
}
