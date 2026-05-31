package ai.jarvis.cli;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Manages CLI session state.
 * Tracks: current user, JWT token, active session.
 * In-memory only for Phase 1.
 * Phase 3+: persist to ~/.jarvis/auth.json
 */
@Slf4j
@Component
public class CliStateManager {

    @Getter @Setter
    private String accessToken;

    @Getter @Setter
    private String username;

    @Getter @Setter
    private String role;

    @Getter @Setter
    private UUID userId;

    @Getter @Setter
    private UUID activeSessionId;

    @Getter @Setter
    private String activeSessionTitle;

    public boolean isLoggedIn() {
        return accessToken != null
                && !accessToken.isBlank();
    }

    public boolean hasActiveSession() {
        return activeSessionId != null;
    }

    public void clear() {
        accessToken = null;
        username = null;
        role = null;
        userId = null;
        activeSessionId = null;
        activeSessionTitle = null;
        log.info("CLI state cleared");
    }
}
