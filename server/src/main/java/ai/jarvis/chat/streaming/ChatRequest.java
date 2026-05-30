package ai.jarvis.chat.streaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequest(

        // Optional: if null, create a new session
        UUID sessionId,

        @NotBlank(message = "Message cannot be empty")
        @Size(max = 10000,
                message = "Message too long (max 10000 chars)")
        String message,

        // Optional: override default provider
        UUID providerId
) {
}
