package ai.jarvis.rag;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface DocumentService {
    Mono<DocumentResponse> uploadDocument(UUID userId, DocumentUploadRequest request);
    Flux<DocumentResponse> getUserDocuments(UUID userId);
    Mono<DocumentResponse> getDocument(UUID id, UUID userId);
    Mono<Void> deleteDocument(UUID id, UUID userId);
}
