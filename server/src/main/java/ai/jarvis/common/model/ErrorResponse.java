package ai.jarvis.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        List<String> details,
        String path,
        Instant timestamp
) {
    public static ErrorResponse of(
            String errorCode,
            String message,
            String path) {
        return new ErrorResponse(
                errorCode, message, null, path, Instant.now());
    }

    public static ErrorResponse of(
            String errorCode,
            String message,
            List<String> details,
            String path) {
        return new ErrorResponse(
                errorCode, message, details, path, Instant.now());
    }
}
