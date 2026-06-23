package ai.jarvis.memory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemoryRequest(
        @NotNull
        MemoryType memoryType,
        @NotBlank
        String content
) {}
