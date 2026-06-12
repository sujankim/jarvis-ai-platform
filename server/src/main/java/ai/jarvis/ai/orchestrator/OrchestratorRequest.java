package ai.jarvis.ai.orchestrator;

import java.util.UUID;

public record OrchestratorRequest(
        UUID sessionId,
        String message,
        String username,
        String role,
        UUID userId,
        UUID providerId
) {
    // Factory with userId
    public static OrchestratorRequest of(
            UUID sessionId,
            String message,
            String username,
            String role,
            UUID userId) {
        return new OrchestratorRequest(
                sessionId, message,
                username, role,
                userId, null
        );
    }

    // Keep backward compatible factory
    // (without userId — uses null)
    public static OrchestratorRequest of(
            UUID sessionId,
            String message,
            String username,
            String role) {
        return new OrchestratorRequest(
                sessionId, message,
                username, role,
                null, null
        );
    }
}