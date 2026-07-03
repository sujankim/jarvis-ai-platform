package ai.jarvis.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

public record VoiceSettingsRequest(
        @Pattern(regexp = "^[a-zA-Z0-9\\s\\-_]*$", message = "Voice name contains invalid characters")
        String voiceName,

        @DecimalMin(value = "0.5", message = "Voice speed must be at least 0.5")
        @DecimalMax(value = "2.0", message = "Voice speed must be at most 2.0")
        Double voiceSpeed
) {
}
