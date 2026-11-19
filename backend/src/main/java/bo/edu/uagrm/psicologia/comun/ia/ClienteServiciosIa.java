package bo.edu.uagrm.psicologia.comun.ia;

import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente REST interno hacia servicios-ia (OCR, búsqueda semántica, predicción, asistente).
 * Si el servicio no está disponible responde 503 con un mensaje claro: el resto del sistema sigue funcionando.
 */
@Component
public class ClienteServiciosIa {

    private static final Logger log = LoggerFactory.getLogger(ClienteServiciosIa.class);

    private final RestClient cliente;
    private final String clave;
    private final ObjectMapper json;

    public ClienteServiciosIa(PropiedadesApp props, ObjectMapper json) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(120));   // OCR de un PDF de varias páginas
        this.cliente = RestClient.builder().baseUrl(props.servicios().iaUrl()).requestFactory(fabrica).build();
        this.clave = props.servicios().clave();
        this.json = json;
    }

    /** Fallo controlado del servicio inteligente (código HTTP devuelto por el servicio y mensaje). */
    public static class RespuestaIa extends NegocioException {
        private final int codigo;

        public RespuestaIa(HttpStatus estado, int codigo, String mensaje) {
            super(estado, mensaje);
            this.codigo = codigo;
        }

        public int getCodigo() {
            return codigo;
        }
    }

    public JsonNode post(String ruta, Object cuerpo) {
        try {
            String r = cliente.post().uri(ruta).header("X-Service-Key", clave).contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo).retrieve().body(String.class);
            return json.readTree(r == null ? "{}" : r);
        } catch (RestClientResponseException e) {
            String detalle = extraerDetalle(e.getResponseBodyAsString());
            int codigo = e.getStatusCode().value();
            HttpStatus estado = codigo == 409 || codigo == 422 ? HttpStatus.valueOf(codigo) : HttpStatus.BAD_GATEWAY;
            throw new RespuestaIa(estado, codigo, detalle);
        } catch (ResourceAccessException e) {
            log.warn("servicios-ia no disponible: {}", e.getMessage());
            throw new RespuestaIa(HttpStatus.SERVICE_UNAVAILABLE, 503,
                    "El servicio inteligente no está disponible en este momento. El resto del sistema sigue funcionando; intente más tarde.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RespuestaIa(HttpStatus.BAD_GATEWAY, 502, "Respuesta inválida del servicio inteligente");
        }
    }

    private String extraerDetalle(String cuerpo) {
        try {
            JsonNode n = json.readTree(cuerpo);
            return n.hasNonNull("detail") && n.get("detail").isTextual() ? n.get("detail").asText() : "Error del servicio inteligente";
        } catch (Exception ex) {
            return "Error del servicio inteligente";
        }
    }
}
