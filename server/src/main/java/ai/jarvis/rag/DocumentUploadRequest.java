package ai.jarvis.rag;

import jakarta.validation.constraints.NotBlank;

public record DocumentUploadRequest(
        @NotBlank String filename,
        @NotBlank String content,
        String description
) {}
