package ai.jarvis.rag;

import ai.jarvis.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<DocumentResponse>> uploadDocument(@RequestBody DocumentUploadRequest request) {
        return getUserId()
                .flatMap(userId -> documentService.uploadDocument(userId, request))
                .map(ApiResponse::ok);
    }

    @GetMapping
    public Mono<ApiResponse<List<DocumentResponse>>> listDocuments() {
        return getUserId()
                .flatMap(userId -> documentService.getUserDocuments(userId).collectList())
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    public Mono<ApiResponse<DocumentResponse>> getDocument(@PathVariable UUID id) {
        return getUserId()
                .flatMap(userId -> documentService.getDocument(id, userId))
                .map(ApiResponse::ok);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteDocument(@PathVariable UUID id) {
        return getUserId()
                .flatMap(userId -> documentService.deleteDocument(id, userId));
    }

    private Mono<UUID> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString)
                .onErrorMap(
                        IllegalArgumentException.class,
                        ex -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid token subject",
                                ex));
    }
}
