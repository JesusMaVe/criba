package com.criba.board;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce errores de dominio a HTTP.
 *
 * <p>El 422 de un descarte sin motivo es el caso que justifica tener comandos síncronos: el usuario
 * se entera en la misma petición, no tres segundos después por el WebSocket.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    record ApiError(String error, String message, Map<String, Object> details) {
    }

    @ExceptionHandler(BoardException.NotFound.class)
    ResponseEntity<ApiError> notFound(BoardException.NotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage(), Map.of()));
    }

    @ExceptionHandler(BoardException.Duplicate.class)
    ResponseEntity<ApiError> duplicate(BoardException.Duplicate e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DUPLICATE_NODE", e.getMessage(),
                        Map.of("existingNodeId", e.existingNodeId())));
    }

    @ExceptionHandler(BoardException.IllegalTransition.class)
    ResponseEntity<ApiError> illegalTransition(BoardException.IllegalTransition e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ILLEGAL_TRANSITION", e.getMessage(), e.details()));
    }

    @ExceptionHandler(BoardException.MissingReason.class)
    ResponseEntity<ApiError> missingReason(BoardException.MissingReason e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("MISSING_REASON", e.getMessage(), Map.of("field", "reason")));
    }

    @ExceptionHandler(BoardException.MissingQuery.class)
    ResponseEntity<ApiError> missingQuery(BoardException.MissingQuery e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("MISSING_QUERY", e.getMessage(), Map.of("field", "query")));
    }

    @ExceptionHandler(BoardException.UnknownState.class)
    ResponseEntity<ApiError> unknownState(BoardException.UnknownState e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("UNKNOWN_STATE", e.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        List<String> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .collect(Collectors.toList());
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_BODY", "Petición inválida", Map.of("fields", fields)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidPath(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_PATH", "Parámetro inválido: " + e.getName(), Map.of()));
    }
}
