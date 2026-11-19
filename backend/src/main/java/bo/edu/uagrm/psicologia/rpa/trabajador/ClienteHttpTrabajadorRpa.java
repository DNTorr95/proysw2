package bo.edu.uagrm.psicologia.rpa.trabajador;

import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Cliente REST interno hacia rpa-worker. Envía la cabecera X-Service-Key y corta la llamada si se
 * supera el tiempo máximo del proceso (incidencia TIEMPO_EXCEDIDO).
 */
@Component
public class ClienteHttpTrabajadorRpa implements TrabajadorRpa {

    private final String url;
    private final String clave;
    /** HTTP/1.1 explícito: con HTTP/2 (h2c) sobre texto plano uvicorn rechaza la actualización y descarta el cuerpo. */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ClienteHttpTrabajadorRpa(PropiedadesApp props) {
        this.url = props.servicios().rpaUrl();
        this.clave = props.servicios().clave();
    }

    @Override
    public ContratoTrabajador.Resultado ejecutar(ContratoTrabajador.Solicitud solicitud, Duration limite) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(limite);
        RestClient cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
        try {
            return cliente.post().uri("/interno/rpa/ejecuciones")
                    .header("X-Service-Key", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(solicitud)
                    .retrieve()
                    .body(ContratoTrabajador.Resultado.class);
        } catch (ResourceAccessException e) {
            if (tieneCausa(e, HttpTimeoutException.class)) {
                throw new FalloTrabajador("TIEMPO_EXCEDIDO",
                        "El trabajador RPA no respondió dentro del tiempo máximo (" + limite.toSeconds() + " s)", e);
            }
            if (tieneCausa(e, ConnectException.class)) {
                throw new FalloTrabajador("SERVICIO_NO_DISPONIBLE", "El trabajador RPA no está disponible", e);
            }
            throw new FalloTrabajador("SERVICIO_NO_DISPONIBLE", "Error de comunicación con el trabajador RPA: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new FalloTrabajador("ERROR_INTERNO",
                    "El trabajador RPA respondió " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    private static boolean tieneCausa(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (tipo.isInstance(c)) return true;
        }
        return false;
    }
}
