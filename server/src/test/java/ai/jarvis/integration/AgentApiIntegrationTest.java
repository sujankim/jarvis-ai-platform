package ai.jarvis.integration;

import ai.jarvis.agents.Agent;
import ai.jarvis.agents.AgentRepository;
import ai.jarvis.agents.AgentRequest;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.UUID;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(TestContainerConfig.class)
@DisplayName("Agent API Integration Tests")
class AgentApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private JwtService jwtService;

    private UUID currentUserId;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll().block();
        currentUserId = UUID.randomUUID();
        
        UserDetails userDetails = new User(currentUserId.toString(), "password", Collections.emptyList());
        jwtToken = jwtService.generateToken(userDetails);
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
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);

        StepVerifier.create(agentRepository.findByUserIdOrderByCreatedAtDesc(currentUserId))
                .expectNextMatches(agent -> agent.userId().equals(currentUserId) && agent.goal().equals("Integrate Goal"))
                .verifyComplete();
    }

    @Test
    @DisplayName("GET /api/v1/agents enforces database cross-tenant tenant isolation")
    void shouldEnforceOwnershipIsolationOnList() {
        Agent currentUserAgent = Agent.create(currentUserId, UUID.randomUUID(), "Current User Goal");
        r2dbcEntityTemplate.insert(currentUserAgent).block();

        UUID otherUserId = UUID.randomUUID();
        Agent otherUserAgent = Agent.create(otherUserId, UUID.randomUUID(), "Other User Goal");
        r2dbcEntityTemplate.insert(otherUserAgent).block();

        webTestClient.get()
                .uri("/api/v1/agents")
                .header("Authorization", "Bearer " + jwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].goal").isEqualTo("Current User Goal");
    }
}
