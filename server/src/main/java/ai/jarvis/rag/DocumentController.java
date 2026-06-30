package ai.jarvis.rag;

import java.util.UUID;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.common.model.ErrorResponse;
import ai.jarvis.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Document", description = "Document Management")
@RequestMapping("/api/v1/documents")
class DocumentController {
    private final DocumentService documentService;

    @Operation(summary = "Get document status")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved document status"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid document id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Document was not found by given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping(value = "/{documentId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<DocumentStatusResponse>> getDocumentStatus(
            @PathVariable UUID documentId,
            @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return SecurityUtil.getUserId(authenticationMono)
                .flatMap(userId -> documentService.getDocumentByIdAndUserId(documentId, userId))
                .map(ApiResponse::ok);
    }
}
