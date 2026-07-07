package ai.jarvis.rag;

import ai.jarvis.config.SecurityConfig;
import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("DocumentController Tests")
class DocumentControllerTest {

    static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Nested
    @WebFluxTest(controllers = DocumentController.class)
    @Import(TestSecurityConfig.class)
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("When user is authenticated")
    class AuthenticatedTests {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private DocumentService documentService;

        @MockitoBean
        private JwtService jwtService;

        @Test
        @DisplayName("GET /{id}/status returns 200 with document status")
        void shouldReturnDocumentStatus() {
            UUID documentId = UUID.fromString("5eff485b-1ca6-4d4f-b94c-c30c010de82b");
            DocumentStatusResponse statusResponse = new DocumentStatusResponse(
                    documentId,
                    "test-file.pdf",
                    DocumentStatus.READY,
                    10,
                    null,
                    Instant.now());

            when(documentService.getDocumentByIdAndUserId(documentId, USER_ID))
                    .thenReturn(Mono.just(statusResponse));

            webTestClient.get()
                    .uri("/api/v1/documents/{documentId}/status", documentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo(documentId.toString())
                    .jsonPath("$.data.status").isEqualTo("READY");
        }

        @Test
        @DisplayName("GET /{id}/status returns 400 for invalid document id")
        void shouldReturnBadRequestForInvalidId() {
            webTestClient.get()
                    .uri("/api/v1/documents/{documentId}/status", "invalid-uuid")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("GET /{id}/status returns 404 when document not found")
        void shouldReturnNotFoundWhenMissing() {
            UUID documentId = UUID.fromString("5eff485b-1ca6-4d4f-b94c-c30c010de82b");
            when(documentService.getDocumentByIdAndUserId(documentId, USER_ID))
                    .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")));

            webTestClient.get()
                    .uri("/api/v1/documents/{documentId}/status", documentId)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("POST /api/v1/documents returns 201 with created document")
        void shouldUploadDocument() {
            DocumentUploadRequest request = new DocumentUploadRequest("test.txt", "Hello World", "Desc");
            UUID docId = UUID.randomUUID();
            DocumentResponse response = new DocumentResponse(docId, USER_ID, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.PENDING, 0, "Desc", null, Instant.now(), Instant.now());

            when(documentService.uploadDocument(eq(USER_ID), any(DocumentUploadRequest.class)))
                    .thenReturn(Mono.just(response));

            webTestClient.post()
                    .uri("/api/v1/documents")
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo(docId.toString())
                    .jsonPath("$.data.status").isEqualTo("PENDING");
        }

        @Test
        @DisplayName("POST /api/v1/documents returns 400 when fields are blank")
        void shouldReturnBadRequestWhenFieldsBlank() {
            DocumentUploadRequest invalidRequest = new DocumentUploadRequest("", "", "Desc");

            webTestClient.post()
                    .uri("/api/v1/documents")
                    .bodyValue(invalidRequest)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("GET /api/v1/documents returns 200 with user documents list")
        void shouldListDocuments() {
            UUID docId = UUID.randomUUID();
            DocumentResponse response = new DocumentResponse(docId, USER_ID, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.READY, 1, "Desc", null, Instant.now(), Instant.now());

            when(documentService.getUserDocuments(USER_ID))
                    .thenReturn(Flux.just(response));

            webTestClient.get()
                    .uri("/api/v1/documents")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data[0].id").isEqualTo(docId.toString())
                    .jsonPath("$.data[0].filename").isEqualTo("test.txt");
        }

        @Test
        @DisplayName("GET /api/v1/documents/{id} returns 200 with single document details")
        void shouldGetDocument() {
            UUID docId = UUID.randomUUID();
            DocumentResponse response = new DocumentResponse(docId, USER_ID, "test.txt", DocumentFileType.TXT, 11L, DocumentStatus.READY, 1, "Desc", null, Instant.now(), Instant.now());

            when(documentService.getDocument(docId, USER_ID))
                    .thenReturn(Mono.just(response));

            webTestClient.get()
                    .uri("/api/v1/documents/{id}", docId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo(docId.toString());
        }

        @Test
        @DisplayName("GET /api/v1/documents/{id} returns 404 when document belongs to another tenant")
        void shouldReturnNotFoundForMismatchedOwner() {
            UUID targetId = UUID.randomUUID();
            when(documentService.getDocument(targetId, USER_ID))
                    .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found or access denied")));

            webTestClient.get()
                    .uri("/api/v1/documents/{id}", targetId)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("DELETE /api/v1/documents/{id} returns 204 No Content on success")
        void shouldDeleteDocument() {
            UUID docId = UUID.randomUUID();
            when(documentService.deleteDocument(docId, USER_ID))
                    .thenReturn(Mono.empty());

            webTestClient.delete()
                    .uri("/api/v1/documents/{id}", docId)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("DELETE /api/v1/documents/{id} returns 404 when object ownership mismatch occurs")
        void shouldReturnNotFoundOnDeleteForMismatchedOwner() {
            UUID targetId = UUID.randomUUID();
            when(documentService.deleteDocument(targetId, USER_ID))
                    .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found or access denied")));

            webTestClient.delete()
                    .uri("/api/v1/documents/{id}", targetId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @WebFluxTest(controllers = DocumentController.class)
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    @DisplayName("When no JWT token provided")
    class UnauthorizedTests {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private DocumentService documentService;

        @Test
        @DisplayName("GET /{id}/status returns 401 without JWT token")
        void shouldReturnUnauthorizedWithoutToken() {
            UUID documentId = UUID.randomUUID();
            webTestClient.get()
                    .uri("/api/v1/documents/{documentId}/status", documentId)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
