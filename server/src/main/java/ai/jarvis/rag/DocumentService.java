package ai.jarvis.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

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
        long sizeBytes = request.content().getBytes(StandardCharsets.UTF_8).length;
        Document doc = Document.create(userId, request.filename(), DocumentFileType.TXT, sizeBytes, request.description());

        return r2dbcEntityTemplate.insert(doc)
                .doOnNext(savedDoc -> {
                    processingService.processDocument(savedDoc.id(), userId, request.content(), DocumentFileType.TXT)
                            .subscribe(
                                    null,
                                    error -> log.error("Async document processing failed for docId: {}", savedDoc.id(), error)
                            );
                })
                .map(DocumentResponse::from);
    }

    public Flux<DocumentResponse> getUserDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .map(DocumentResponse::from);
    }

    public Mono<DocumentResponse> getDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found or access denied")))
                .map(DocumentResponse::from);
    }

    public Mono<Void> deleteDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found or access denied")))
                .flatMap(documentRepository::delete);
    }
}
