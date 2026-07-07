package ai.jarvis.rag;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Document", description = "Document Management")
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload document text")
    public Mono<ApiResponse<DocumentResponse>> uploadDocument(
            @Valid @RequestBody DocumentUploadRequest request,
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.uploadDocument(userId, request))
                .map(ApiResponse::ok);
    }

    @GetMapping
    @Operation(summary = "List all documents")
    public Mono<ApiResponse<List<DocumentResponse>>> listDocuments(
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.getUserDocuments(userId).collectList())
                .map(ApiResponse::ok);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details")
    public Mono<ApiResponse<DocumentResponse>> getDocument(
            @PathVariable UUID id,
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.getDocument(id, userId))
                .map(ApiResponse::ok);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document")
    public Mono<Void> deleteDocument(
            @PathVariable UUID id,
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.deleteDocument(id, userId));
    }

    @GetMapping(value = "/{documentId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get document status")
    public Mono<ApiResponse<DocumentStatusResponse>> getDocumentStatus(
            @PathVariable UUID documentId,
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.getDocumentByIdAndUserId(documentId, userId))
                .map(ApiResponse::ok);
    }
}
