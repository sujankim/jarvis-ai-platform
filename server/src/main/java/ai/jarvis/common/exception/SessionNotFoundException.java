package ai.jarvis.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SessionNotFoundException extends JarvisException {

    public SessionNotFoundException(UUID sessionId) {
        super(
                "SESSION_NOT_FOUND",
                "Session not found: " + sessionId,
                HttpStatus.NOT_FOUND
        );
    }
}
