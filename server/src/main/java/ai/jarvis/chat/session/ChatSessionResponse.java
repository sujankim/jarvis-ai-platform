package ai.jarvis.chat.session;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(
        UUID id,
        String title,
        String status,
        int messageCount,
        int totalTokens,
        Instant createdAt,
        Instant lastMessageAt
) {
}
