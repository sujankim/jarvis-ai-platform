package ai.jarvis.integration;

import ai.jarvis.agents.Agent;
import ai.jarvis.agents.AgentRepository;
import ai.jarvis.agents.AgentRequest;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.config.WithMockJarvisUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.UUID;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(TestContainerConfig.class)
@WithMockJarvisUser(principal = AgentApiIntegrationTest.USER_ID_RAW)
@DisplayName("Agent API Integration Tests")
class AgentApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentRepository agentRepository;

    static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll().block();
    }

    @AfterEach
    void tearDown() {
        agentRepository.deleteAll().block();
    }

    @Test
    @DisplayName("POST /api/v1/agents creates and persists a new agent")
    void shouldCreateAndPersistAgentInDatabase() {
        AgentRequest request = new AgentRequest("Integrate Goal", UUID.randomUUID());

        webTestClient.post()
                .uri("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        StepVerifier.create(agentRepository.findAll())
                .expectNextMatches(agent -> agent.userId().equals(USER_ID) && agent.goal().equals("Integrate Goal"))
                .verifyComplete();
    }

    @Test
    @DisplayName("GET /api/v1/agents enforces database cross-tenant tenant isolation")
    void shouldEnforceOwnershipIsolationOnList() {
        Agent currentUserAgent = Agent.create(USER_ID, UUID.randomUUID(), "Current User Goal");
        agentRepository.save(currentUserAgent).block();

        UUID otherUserId = UUID.randomUUID();
        Agent otherUserAgent = Agent.create(otherUserId, UUID.randomUUID(), "Other User Goal");
        agentRepository.save(otherUserAgent).block();

        webTestClient.get()
                .uri("/api/v1/agents")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].goal").isEqualTo("Current User Goal")
                .jsonPath("$.data[0].userId").isEqualTo(USER_ID.toString());
    }
}
