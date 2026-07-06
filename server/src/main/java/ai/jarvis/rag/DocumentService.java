package ai.jarvis.rag;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;

    public DocumentService(DocumentRepository documentRepository, DocumentProcessingService processingService) {
        this.documentRepository = documentRepository;
        this.processingService = processingService;
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

    public Mono<DocumentResponse> uploadDocument(UUID userId, DocumentUploadRequest request) {
        if (request.filename() == null || request.filename().isBlank()) {
            return Mono.error(new IllegalArgumentException("Filename cannot be empty"));
        }
        if (request.content() == null || request.content().isBlank()) {
            return Mono.error(new IllegalArgumentException("Content cannot be empty"));
        }

        long sizeBytes = request.content().getBytes().length;
        Document doc = Document.create(userId, request.filename(), DocumentFileType.TXT, sizeBytes, request.description());

        return documentRepository.save(doc)
                .doOnNext(savedDoc -> {
                    processingService.processDocument(savedDoc.id(), userId, request.content(), DocumentFileType.TXT)
                            .subscribe();
                })
                .map(DocumentResponse::from);
    }

    public Flux<DocumentResponse> getUserDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .map(DocumentResponse::from);
    }

    public Mono<DocumentResponse> getDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Document not found or access denied")))
                .map(DocumentResponse::from);
    }

    public Mono<Void> deleteDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Document not found or access denied")))
                .flatMap(documentRepository::delete);
    }
}
