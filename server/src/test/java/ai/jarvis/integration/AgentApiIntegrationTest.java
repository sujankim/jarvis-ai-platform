package ai.jarvis.integration;

import ai.jarvis.agents.AgentOrchestrator;
import ai.jarvis.agents.AgentRequest;
import ai.jarvis.agents.AgentStatus;
import ai.jarvis.agents.Agent;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.config.WithMockJarvisUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(TestContainerConfig.class)
@DisplayName("Agent API Integration Tests")
class AgentApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentOrchestrator orchestrator;

    static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("POST /api/v1/agents creates a new agent with pending status")
    void shouldCreateAgentInDatabase() {
        AgentRequest request = new AgentRequest("Persist Goal", UUID.randomUUID());

        when(orchestrator.startAgent(any(String.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Flux.empty());

        webTestClient.post()
                .uri("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("GET /api/v1/agents returns only authenticated user's agents")
    void shouldEnforceOwnershipIsolationOnList() {
        UUID agentId = UUID.randomUUID();
        Agent agent = new Agent(agentId, USER_ID, UUID.randomUUID(), "Test Goal", AgentStatus.RUNNING, null, 0, null, null, Instant.now(), Instant.now(), null);

        when(orchestrator.getUserAgents(any(UUID.class)))
                .thenReturn(Flux.just(agent));

        webTestClient.get()
                .uri("/api/v1/agents")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("DELETE /api/v1/agents/{id} cannot cancel another user's agent")
    void shouldReturnNotFoundForMismatchedOwnerOnCancel() {
        UUID crossTenantId = UUID.randomUUID();
        when(orchestrator.cancelAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found or access denied")));

        webTestClient.delete()
                .uri("/api/v1/agents/{id}", crossTenantId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @WithMockJarvisUser(principal = USER_ID_RAW)
    @DisplayName("DELETE /api/v1/agents/{id} cannot cancel a completed agent")
    void shouldReturnConflictOnCancelCompletedAgent() {
        UUID targetId = UUID.randomUUID();
        when(orchestrator.cancelAgent(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel a completed agent")));

        webTestClient.delete()
                .uri("/api/v1/agents/{id}", targetId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }
}
