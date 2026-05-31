package ai.jarvis.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ai.jarvis.common.model.ErrorResponse;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Our custom exceptions ─────────────────────

    @ExceptionHandler(JarvisException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleJarvisException(
            JarvisException ex,
            ServerWebExchange exchange){

        log.error("Jarvis error: code={} message={}",
                ex.getErrorCode(), ex.getMessage());

        return Mono.just(ResponseEntity
                .status(ex.getStatus())
                .body(ErrorResponse.of(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        exchange.getRequest()
                                .getPath().value()
                )));
    }

    // ── Spring's ResponseStatusException ─────────

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(
            ResponseStatusException ex,
            ServerWebExchange exchange){

        HttpStatus status = HttpStatus.valueOf(
                ex.getStatusCode().value());

        return Mono.just(ResponseEntity
                .status(status)
                .body(ErrorResponse.of(
                        status.name(),
                        ex.getReason(),
                        exchange.getRequest()
                                .getPath().value()
                )));
    }

    // ── Validation errors (@Valid failures) ──────

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(
            WebExchangeBindException ex,
            ServerWebExchange exchange){

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        details,
                        exchange.getRequest().getPath().value()
                )));
    }

    // ── Catch-all for unexpected errors ──────────

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneral(
            Exception ex,
            ServerWebExchange exchange) {

        log.error("Unexpected error at {}: {}",
                exchange.getRequest().getPath(),
                ex.getMessage(), ex);

        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        exchange.getRequest()
                                .getPath().value()
                )));
    }
}

