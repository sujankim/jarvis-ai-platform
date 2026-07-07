package ai.jarvis.rag;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID userId,
        String filename,
        DocumentFileType fileType,
        long fileSizeBytes,
        DocumentStatus status,
        int chunkCount,
        String description,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.id(),
                doc.userId(),
                doc.filename(),
                doc.fileType(),
                doc.fileSizeBytes(),
                doc.status(),
                doc.chunkCount(),
                doc.description(),
                doc.errorMessage(),
                doc.createdAt(),
                doc.updatedAt()
        );
    }
}
