package ai.jarvis.agents;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {AgentController.class})
@Import(TestSecurityConfig.class)
@DisplayName("AgentController Tests")
class AgentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentOrchestrator orchestrator;

    @MockitoBean
    private AgentMapper agentMapper;

    @MockitoBean
    private JwtService jwtService;

    public static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    public static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Nested
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("When user is authenticated")
    class AuthenticatedTests {

        @Test
        @DisplayName("Test POST /api/v1/agents - Should return 202 Accepted")
        void testCreateAgent_ShouldReturnAccepted() {
            AgentRequest request = new AgentRequest("Test Goal", UUID.randomUUID());
            AgentResponse mockResponse = new AgentResponse(UUID.randomUUID(), "Test Goal", AgentStatus.PENDING, null, 0, null, null, Instant.now(), Instant.now(), null, List.of());

            when(orchestrator.startAgent(any(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(Flux.empty());
            when(agentMapper.toResponse(any(Agent.class)))
                    .thenReturn(mockResponse);

            webTestClient.post()
                    .uri("/api/v1/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        @DisplayName("Test POST /api/v1/agents/stream - Should return 200 OK")
        void testStream_ShouldReturnSseStream() {
            AgentRequest request = new AgentRequest("Stream Goal", UUID.randomUUID());

            when(orchestrator.startAgent(any(String.class), any(UUID.class), any(UUID.class)))
                    .thenReturn(Flux.empty());

            webTestClient.post()
                    .uri("/api/v1/agents/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Test GET /api/v1/agents - Should return empty list when no agents found")
        void testListAgents_ShouldReturnEmptyList() {
            when(orchestrator.getUserAgents(USER_ID)).thenReturn(Flux.empty());

            webTestClient.get()
                    .uri("/api/v1/agents")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Test GET /api/v1/agents/{id} - Should return agent details")
        void testGetAgent_ShouldReturnAgentDetails() {
            UUID agentId = UUID.randomUUID();
            Agent agent = Agent.create(USER_ID, UUID.randomUUID(), "Test Goal");
            AgentOrchestrator.AgentWithSteps agentWithSteps = new AgentOrchestrator.AgentWithSteps(agent, List.of());

            when(orchestrator.getAgent(agentId, USER_ID)).thenReturn(Mono.just(agentWithSteps));

            webTestClient.get()
                    .uri("/api/v1/agents/{agentId}", agentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.goal").isEqualTo("Test Goal");
        }

        @Test
        @DisplayName("Test GET /api/v1/agents/{id}/steps - Should return step list")
        void testGetSteps_ShouldReturnStepsArray() {
            UUID agentId = UUID.randomUUID();
            Agent agent = Agent.create(USER_ID, UUID.randomUUID(), "Test Goal");
            AgentOrchestrator.AgentWithSteps agentWithSteps = new AgentOrchestrator.AgentWithSteps(agent, List.of());

            when(orchestrator.getAgent(agentId, USER_ID)).thenReturn(Mono.just(agentWithSteps));

            webTestClient.get()
                    .uri("/api/v1/agents/{agentId}/steps", agentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data").isArray();
        }

        @Test
        @DisplayName("Test DELETE /api/v1/agents/{id} - Should return 204 No Content")
        void testCancelAgent_ShouldReturnNoContent() {
            UUID agentId = UUID.randomUUID();
            when(orchestrator.cancelAgent(agentId, USER_ID)).thenReturn(Mono.empty());

            webTestClient.delete()
                    .uri("/api/v1/agents/{agentId}", agentId)
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Nested
    @WithMockJarvisUser(principal = "invalid-malformed-token-subject")
    @DisplayName("When no JWT token provided")
    class UnauthorizedTests {

        @Test
        @DisplayName("POST /api/v1/agents returns 401 Unauthorized")
        void shouldReturnUnauthorizedForCreate() {
            webTestClient.post()
                    .uri("/api/v1/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AgentRequest("Test Goal", UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("POST /api/v1/agents/stream returns 401 Unauthorized")
        void shouldReturnUnauthorizedForStream() {
            webTestClient.post()
                    .uri("/api/v1/agents/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AgentRequest("Stream Goal", UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("GET /api/v1/agents returns 401 Unauthorized")
        void shouldReturnUnauthorizedForList() {
            webTestClient.get()
                    .uri("/api/v1/agents")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("GET /api/v1/agents/{id} returns 401 Unauthorized")
        void shouldReturnUnauthorizedForGet() {
            webTestClient.get()
                    .uri("/api/v1/agents/{id}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("GET /api/v1/agents/{id}/steps returns 401 Unauthorized")
        void shouldReturnUnauthorizedForSteps() {
            webTestClient.get()
                    .uri("/api/v1/agents/{id}/steps", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("DELETE /api/v1/agents/{id} returns 401 Unauthorized")
        void shouldReturnUnauthorizedForDelete() {
            webTestClient.delete()
                    .uri("/api/v1/agents/{id}", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
