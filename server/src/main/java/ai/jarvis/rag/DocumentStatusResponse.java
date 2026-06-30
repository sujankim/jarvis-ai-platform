package ai.jarvis.rag;

import java.time.Instant;
import java.util.UUID;

public record DocumentStatusResponse(
        UUID id,
        String filename,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant completedAt) {
}
