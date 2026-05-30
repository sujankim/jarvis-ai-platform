package ai.jarvis.ai.orchestrator;

import java.util.UUID;

public record OrchestratorRequest(
        UUID sessionId,
        String message,
        String username,
        String role,
        UUID providerId
) {
    public static OrchestratorRequest of(
            UUID sessionId,
            String message,
            String username,
            String role) {
        return new OrchestratorRequest(
                sessionId, message, username, role, null
        );
    }
}