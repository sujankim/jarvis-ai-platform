package ai.jarvis.chat.session;

import static org.mockito.Mockito.when;

import java.util.UUID;

import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
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
import reactor.core.publisher.Flux;

@WebFluxTest(controllers = {ChatSessionController.class})
@Import(TestSecurityConfig.class)
@DisplayName("ChatSessionController Tests")
class ChatSessionControllerTest {

    public static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatSessionService sessionService;

    @MockitoBean
    private JwtService jwtService;

    @Nested
    @DisplayName("Tests with valid user UUID context")
    @WithMockJarvisUser(principal = USER_ID_RAW)
    class ValidUserContext {

        private final UUID userId = UUID.fromString(USER_ID_RAW);

        @Test
        @DisplayName("Test GET /api/v1/sessions - Should return empty list when no sessions found")
        void testListSessions_ShouldReturnEmptyListWhenNoSessionsFound() {
            when(sessionService.getUserSessions(userId)).thenReturn(Flux.empty());

            webTestClient
                    .get()
                    .uri("/api/v1/sessions")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.length()").isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Tests with malformed user UUID token subject")
    @WithMockJarvisUser(principal = "malformed-uuid-string-123")
    class InvalidUserContext {

        @Test
        @DisplayName("Test GET /api/v1/sessions - Should return 401 Unauthorized for invalid UUID")
        void testListSessions_ShouldReturnUnauthorizedWhenUuidIsMalformed() {
            webTestClient
                    .get()
                    .uri("/api/v1/sessions")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Test GET /api/v1/sessions/{id} - Should return 401 Unauthorized for invalid UUID")
        void testGetSession_ShouldReturnUnauthorizedWhenUuidIsMalformed() {
            UUID randomSessionId = UUID.randomUUID();

            webTestClient
                    .get()
                    .uri("/api/v1/sessions/" + randomSessionId)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("GET /api/v1/sessions/{id}/messages - Should return 401 for invalid UUID")
        void testGetMessages_ShouldReturnUnauthorizedWhenUuidIsMalformed() {
            webTestClient.get()
                    .uri("/api/v1/sessions/" + UUID.randomUUID() + "/messages")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("DELETE /api/v1/sessions/{id} - Should return 401 for invalid UUID")
        void testArchiveSession_ShouldReturnUnauthorizedWhenUuidIsMalformed() {
            webTestClient.delete()
                    .uri("/api/v1/sessions/" + UUID.randomUUID())
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
