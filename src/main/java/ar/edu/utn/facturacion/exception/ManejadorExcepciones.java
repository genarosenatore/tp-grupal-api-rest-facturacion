package ar.edu.utn.facturacion.exception;

import ar.edu.utn.facturacion.dto.ErrorDTO;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
@RestControllerAdvice
public class ManejadorExcepciones {
    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorDTO> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(404, e.getMessage(), List.of());
    }
    @ExceptionHandler({ConflictoNegocioException.class, ConcurrencyFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorDTO> conflicto(Exception e) {
        String mensaje = e instanceof ConflictoNegocioException ? e.getMessage()
            : "Conflicto de integridad o concurrencia. No se guardó una operación parcial.";
        return respuesta(409, mensaje, List.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorDTO> validacion(MethodArgumentNotValidException e) {
        return respuesta(400, "Solicitud inválida", e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList());
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorDTO> restricciones(ConstraintViolationException e) {
        return respuesta(400, "Solicitud inválida", e.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().toList());
    }
    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<ErrorDTO> solicitud(SolicitudInvalidaException e) {
        return respuesta(400, e.getMessage(), List.of());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorDTO> formato(Exception e) {
        return respuesta(400, "JSON, parámetro o tipo de dato inválido", List.of());
    }
    private ResponseEntity<ErrorDTO> respuesta(int status, String mensaje, List<String> errores) {
        return ResponseEntity.status(status).body(new ErrorDTO(Instant.now(), status, mensaje, errores));
    }
}
