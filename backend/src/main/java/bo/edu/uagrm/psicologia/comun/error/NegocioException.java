package bo.edu.uagrm.psicologia.comun.error;

import org.springframework.http.HttpStatus;

/** Violación de una regla de negocio. Se traduce a una respuesta HTTP con mensaje en español. */
public class NegocioException extends RuntimeException {

    private final HttpStatus estado;

    public NegocioException(String mensaje) {
        this(HttpStatus.BAD_REQUEST, mensaje);
    }

    public NegocioException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
