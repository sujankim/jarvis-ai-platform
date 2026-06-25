package ai.jarvis.voice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for text-to-speech endpoint.
 * POST /api/v1/voice/speak
 */
public record VoiceRequest(

        @NotBlank(message = "Text cannot be empty")
        @Size(max = 5000,
                message = "Text too long (max 5000 chars)")
        String text,

        // Optional: specify language for TTS
        // null = use system default
        String language,

        // Optional: link to existing chat session
        UUID sessionId

) {}