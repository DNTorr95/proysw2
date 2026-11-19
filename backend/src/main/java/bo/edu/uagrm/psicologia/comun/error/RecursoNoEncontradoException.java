package bo.edu.uagrm.psicologia.comun.error;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends NegocioException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(HttpStatus.NOT_FOUND, recurso + " no encontrado: " + id);
    }
}
