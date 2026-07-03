package ai.jarvis.rag;

import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.TOKEN;

import java.time.Instant;
import java.util.UUID;

import ai.jarvis.config.SecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = {DocumentController.class})
@ExtendWith(MockitoExtension.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("DocumentController Tests")
class DocumentControllerTest {

    public static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    public static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn("access");
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID_RAW);
        when(jwtService.extractUsername(TOKEN)).thenReturn("test-user");
        when(jwtService.extractRole(TOKEN)).thenReturn("USER");
    }

    @Test
    @WithMockJarvisUser(principal = DocumentControllerTest.USER_ID_RAW)
    @DisplayName("Test GET /api/v1/documents/{documentId}/status - Should return document status")
    void testGetDocumentStatus_ShouldReturnStatus() {
        UUID documentId = UUID.fromString("5eff485b-1ca6-4d4f-b94c-c30c010de82b");
        DocumentStatusResponse statusResponse = new DocumentStatusResponse(
                documentId,
                "test-file",
                DocumentStatus.READY,
                300,
                null,
                Instant.now()
        );

        when(documentService.getDocumentByIdAndUserId(documentId, USER_ID)).thenReturn(Mono.just(statusResponse));

        webTestClient
                .get()
                .uri("/api/v1/documents/{documentId}/status", documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(statusResponse.id().toString())
                .jsonPath("$.data.status").isEqualTo(statusResponse.status().toString());
    }

    @Test
    @WithMockJarvisUser(principal = DocumentControllerTest.USER_ID_RAW)
    @DisplayName("Test GET /api/v1/documents/{documentId}/status - Should return bad request when invalid document id provided")
    void testGetDocumentStatus_ShouldReturnBadRequestWhenInvalidIdProvided() {
        String invalidDocumentId = "invalid id";

        webTestClient
                .get()
                .uri("/api/v1/documents/{documentId}/status", invalidDocumentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockJarvisUser(principal = DocumentControllerTest.USER_ID_RAW)
    @DisplayName("Test GET /api/v1/documents/{documentId}/status - Should return not found when document was not found by id")
    void testGetDocumentStatus_ShouldReturnNotFoundWhenDocumentNotFound() {
        UUID documentId = UUID.fromString("5eff485b-1ca6-4d4f-b94c-c30c010de82b");
        ResponseStatusException documentNotFoundException = new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");

        when(documentService.getDocumentByIdAndUserId(documentId, USER_ID)).thenReturn(Mono.error(documentNotFoundException));

        webTestClient
                .get()
                .uri("/api/v1/documents/{documentId}/status", documentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /{id}/status - returns 401 without JWT token")
    void testGetDocumentStatus_ShouldReturnUnauthorizedWithoutToken() {
        webTestClient
                .get()
                .uri("/api/v1/documents/{documentId}/status",
                        UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
