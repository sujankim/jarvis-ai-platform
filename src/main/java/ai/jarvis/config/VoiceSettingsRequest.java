package ai.jarvis.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record VoiceSettingsRequest(
        String voiceName,

        @Min(0.5)
        @Max(2.0)
        Double voiceSpeed
) {}
