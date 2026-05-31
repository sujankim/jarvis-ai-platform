package ai.jarvis.common.exception;

import org.springframework.http.HttpStatus;

public class JarvisException extends RuntimeException{

    private final String errorCode;
    private final HttpStatus status;

    public JarvisException(
            String errorCode,
            String message,
            HttpStatus status){
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
