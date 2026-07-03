package ai.jarvis.rag;

import ai.jarvis.config.SecurityConfig;
import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * DocumentController Tests.
 *
 * STRUCTURE follows SettingsControllerTest pattern:
 * No @WebFluxTest on outer class.
 * Each @Nested class owns its own independent
 * Spring context — zero shared bean handlers,
 * zero Duplicate BeanOverrideHandler risk.
 *
 * AuthenticatedTests  → TestSecurityConfig (permits all)
 *                       + @WithMockJarvisUser
 *                       → tests actual controller logic
 *
 * UnauthorizedTests   → Real SecurityConfig + JwtFilter
 *                       + @MockitoBean JwtService (own context)
 *                       → tests 401 boundary
 */
@DisplayName("DocumentController Tests")
class DocumentControllerTest {

    // ── Shared constants ──────────────────────────

    static final String USER_ID_RAW =
            "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    static final UUID USER_ID =
            UUID.fromString(USER_ID_RAW);
    private static final String TOKEN =
            "test-bearer-token";

    // ── Authenticated scenarios ───────────────────

    /**
     * Tests that verify controller behaviour for
     * an authenticated user.
     *
     * Uses TestSecurityConfig (permits all requests)
     * + @WithMockJarvisUser so security is bypassed
     * and only controller logic is exercised.
     */
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
        @DisplayName(
                "GET /{id}/status returns 200 "
                        + "with document status")
        void shouldReturnDocumentStatus() {
            UUID documentId = UUID.fromString(
                    "5eff485b-1ca6-4d4f-"
                            + "b94c-c30c010de82b");

            DocumentStatusResponse statusResponse =
                    new DocumentStatusResponse(
                            documentId,
                            "test-file.pdf",
                            DocumentStatus.READY,
                            10,
                            null,
                            Instant.now());

            when(documentService
                    .getDocumentByIdAndUserId(
                            documentId, USER_ID))
                    .thenReturn(
                            Mono.just(statusResponse));

            webTestClient
                    .get()
                    .uri("/api/v1/documents"
                                    + "/{documentId}/status",
                            documentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.id")
                    .isEqualTo(documentId.toString())
                    .jsonPath("$.data.status")
                    .isEqualTo("READY");
        }

        @Test
        @DisplayName(
                "GET /{id}/status returns 400 "
                        + "for invalid document id")
        void shouldReturnBadRequestForInvalidId() {
            webTestClient
                    .get()
                    .uri("/api/v1/documents"
                                    + "/{documentId}/status",
                            "invalid-uuid")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName(
                "GET /{id}/status returns 404 "
                        + "when document not found")
        void shouldReturnNotFoundWhenMissing() {
            UUID documentId = UUID.fromString(
                    "5eff485b-1ca6-4d4f-"
                            + "b94c-c30c010de82b");

            when(documentService
                    .getDocumentByIdAndUserId(
                            documentId, USER_ID))
                    .thenReturn(Mono.error(
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Document not found")));

            webTestClient
                    .get()
                    .uri("/api/v1/documents"
                                    + "/{documentId}/status",
                            documentId)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    // ── Unauthorized scenarios ────────────────────

    /**
     * Tests that verify unauthenticated requests
     * are rejected with 401.
     *
     * Uses REAL SecurityConfig + JwtAuthenticationFilter
     * so the full security chain is active.
     *
     * Owns its own @MockitoBean JwtService because
     * this is an INDEPENDENT Spring context from
     * AuthenticatedTests — no shared handlers,
     * no Duplicate BeanOverrideHandler.
     *
     * When no Authorization header is present:
     * → JwtAuthenticationFilter skips token validation
     *   entirely (see JwtAuthenticationFilter line 38)
     * → Request continues unauthenticated
     * → SecurityConfig's authenticated() rule rejects it
     * → 401 Unauthorized returned ✅
     *
     * Note: JwtService.validateToken() is NOT called
     * in the no-token path.
     */
    @Nested
    @WebFluxTest(controllers = DocumentController.class)
    @Import({SecurityConfig.class,
            JwtAuthenticationFilter.class})
    @DisplayName("When no JWT token provided")
    class UnauthorizedTests {

        @Autowired
        private WebTestClient webTestClient;

        // Own context — NOT a duplicate of
        // AuthenticatedTests.jwtService.
        // JwtAuthenticationFilter needs this bean
        // to start. In the no-token scenario,
        // JwtAuthenticationFilter skips calling
        // JwtService entirely (no validateToken call).
        // The 401 comes from SecurityConfig's
        // authenticated() rule, not from JwtService.
        @MockitoBean
        private JwtService jwtService;

        // Own context — needed by DocumentController
        // even though it's never called (Spring Security
        // rejects unauthenticated request before
        // reaching controller).
        @MockitoBean
        private DocumentService documentService;

        @Test
        @DisplayName(
                "GET /{id}/status returns 401 "
                        + "without JWT token")
        void shouldReturnUnauthorizedWithoutToken() {
            webTestClient
                    .get()
                    .uri("/api/v1/documents"
                                    + "/{documentId}/status",
                            UUID.randomUUID())
                    // No Authorization header
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}