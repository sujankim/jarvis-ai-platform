package ai.jarvis.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService processingService;

    public DocumentServiceImpl(DocumentRepository documentRepository, DocumentProcessingService processingService) {
        this.documentRepository = documentRepository;
        this.processingService = processingService;
    }

    @Override
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
                            .subscribe(
                                    proDoc -> log.info("Async document processing completed successfully: {}", proDoc.id()),
                                    err -> log.error("Async document processing failed for id: {}", savedDoc.id(), err)
                            );
                })
                .map(DocumentResponse::from);
    }

    @Override
    public Flux<DocumentResponse> getUserDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .map(DocumentResponse::from);
    }

    @Override
    public Mono<DocumentResponse> getDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Document not found or access denied")))
                .map(DocumentResponse::from);
    }

    @Override
    public Mono<Void> deleteDocument(UUID id, UUID userId) {
        return documentRepository.findByIdAndUserId(id, userId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Document not found or access denied")))
                .flatMap(documentRepository::delete);
    }
}
