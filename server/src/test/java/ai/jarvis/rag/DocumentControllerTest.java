package ai.jarvis.rag;

import ai.jarvis.common.model.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentController & Service Unit Tests")
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    private WebTestClient webTestClient;
    private final UUID userId = UUID.randomUUID();
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        org.mockito.Mockito.when(authentication.getPrincipal()).thenReturn(userId.toString());
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
        org.mockito.Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);

        DocumentController controller = new DocumentController(documentService);
        webTestClient = WebTestClient.bindToController(controller)
                .webFilter((exchange, chain) -> chain.filter(exchange)
                        .contextWrite(context -> ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext))))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/documents - Upload")
    class UploadEndpoints {

        @Test
        @DisplayName("Should successfully upload valid document text")
        void uploadDocument_Success() {
            DocumentUploadRequest request = new DocumentUploadRequest("test.txt", "Hello World", "Desc");
            DocumentResponse response = new DocumentResponse(docId, userId, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.PENDING, 0, "Desc", null, Instant.now(), Instant.now());

            org.mockito.Mockito.when(documentService.uploadDocument(eq(userId), any(DocumentUploadRequest.class))).thenReturn(Mono.just(response));

            webTestClient.post()
                    .uri("/api/v1/documents")
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo(docId.toString())
                    .jsonPath("$.data.status").isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/documents - List")
    class ListEndpoints {

        @Test
        @DisplayName("Should return list of documents for current user")
        void listDocuments_Success() {
            DocumentResponse response = new DocumentResponse(docId, userId, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.READY, 1, "Desc", null, Instant.now(), Instant.now());

            org.mockito.Mockito.when(documentService.getUserDocuments(userId)).thenReturn(Flux.just(response));

            webTestClient.get()
                    .uri("/api/v1/documents")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data[0].id").isEqualTo(docId.toString())
                    .jsonPath("$.data[0].filename").isEqualTo("test.txt");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/documents/{id} - Single Document")
    class GetSingleEndpoints {

        @Test
        @DisplayName("Should return document details when owner requests")
        void getDocument_Success() {
            DocumentResponse response = new DocumentResponse(docId, userId, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.READY, 1, "Desc", null, Instant.now(), Instant.now());

            org.mockito.Mockito.when(documentService.getDocument(docId, userId)).thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri("/api/v1/documents/" + docId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo(docId.toString());
        }

        @Test
        @DisplayName("Should return 404 when document not found or access denied")
        void getDocument_NotFoundOrAccessDenied() {
            org.mockito.Mockito.when(documentService.getDocument(docId, userId)).thenReturn(Mono.error(new ResourceNotFoundException("Not found")));

            webTestClient.get()
                    .uri("/api/v1/documents/" + docId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/documents/{id} - Delete")
    class DeleteEndpoints {

        @Test
        @DisplayName("Should delete document successfully and return 244 No Content")
        void deleteDocument_Success() {
            org.mockito.Mockito.when(documentService.deleteDocument(docId, userId)).thenReturn(Mono.empty());

            webTestClient.delete()
                    .uri("/api/v1/documents/" + docId)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("Should return 404 on delete when document not found or access denied")
        void deleteDocument_NotFound() {
            org.mockito.Mockito.when(documentService.deleteDocument(docId, userId)).thenReturn(Mono.error(new ResourceNotFoundException("Not found")));

            webTestClient.delete()
                    .uri("/api/v1/documents/" + docId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }
}
