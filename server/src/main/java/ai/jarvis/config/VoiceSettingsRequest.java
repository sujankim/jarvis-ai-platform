package ai.jarvis.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record VoiceSettingsRequest(
        String voiceName,

        @DecimalMin("0.5")
        @DecimalMax("2.0")
        Double voiceSpeed
) {}

