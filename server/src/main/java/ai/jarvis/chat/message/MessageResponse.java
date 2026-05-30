package ai.jarvis.chat.message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID sessionId,
        MessageRole role,
        String content,
        String modelName,
        Integer totalTokens,
        Integer durationMs,
        boolean error,
        Instant createdAt
) {}
