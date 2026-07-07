package ai.jarvis.agents;

import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
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

@WebFluxTest(controllers = AgentController.class)
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

    static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("POST /api/v1/agents returns 202 Accepted")
    void shouldCreateAgent() {
        AgentRequest request = new AgentRequest("Test Goal", UUID.randomUUID());

        when(orchestrator.startAgent(any(String.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Flux.empty());

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
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("GET /api/v1/agents returns empty list when none exist")
    void shouldReturnEmptyListWhenNoAgents() {
        when(orchestrator.getUserAgents(any(UUID.class)))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/v1/agents")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isEmpty();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("GET /api/v1/agents/{id} returns agent details")
    void shouldGetAgentDetails() {
        UUID agentId = UUID.randomUUID();
        Agent agent = new Agent(agentId, USER_ID, UUID.randomUUID(), "Test Goal", AgentStatus.COMPLETED, null, 0, null, null, Instant.now(), Instant.now(), null);
        AgentOrchestrator.AgentWithSteps agentWithSteps = new AgentOrchestrator.AgentWithSteps(agent, List.of());

        when(orchestrator.getAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.just(agentWithSteps));

        webTestClient.get()
                .uri("/api/v1/agents/{id}", agentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").exists();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("GET /api/v1/agents/{id} returns 404 when missing")
    void shouldReturnNotFoundWhenAgentMissing() {
        UUID agentId = UUID.randomUUID();
        when(orchestrator.getAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found")));

        webTestClient.get()
                .uri("/api/v1/agents/{id}", agentId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("DELETE /api/v1/agents/{id} returns 204 when cancelled")
    void shouldCancelAgent() {
        UUID agentId = UUID.randomUUID();
        when(orchestrator.cancelAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/api/v1/agents/{id}", agentId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("DELETE /api/v1/agents/{id} returns 404 when agent not found")
    void shouldReturnNotFoundOnCancelMissing() {
        UUID agentId = UUID.randomUUID();
        when(orchestrator.cancelAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found")));

        webTestClient.delete()
                .uri("/api/v1/agents/{id}", agentId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("DELETE /api/v1/agents/{id} returns 409 when agent in terminal state")
    void shouldReturnConflictOnCancelTerminalAgent() {
        UUID agentId = UUID.randomUUID();
        when(orchestrator.cancelAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Agent already in terminal state")));

        webTestClient.delete()
                .uri("/api/v1/agents/{id}", agentId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
}
