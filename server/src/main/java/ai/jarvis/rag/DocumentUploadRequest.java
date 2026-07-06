package ai.jarvis.rag;

public record DocumentUploadRequest(
        String filename,
        String content,
        String description
) {}
