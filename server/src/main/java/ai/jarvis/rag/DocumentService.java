package ai.jarvis.rag;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class DocumentService {
    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Mono<DocumentStatusResponse> getDocumentByIdAndUserId(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")))
                .map(it -> new DocumentStatusResponse(
                        it.id(),
                        it.filename(),
                        it.status(),
                        it.chunkCount(),
                        it.errorMessage(),
                        it.updatedAt()));
    }
}
