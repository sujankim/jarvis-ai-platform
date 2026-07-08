package ai.jarvis.integration;

import ai.jarvis.agents.Agent;
import ai.jarvis.agents.AgentOrchestrator;
import ai.jarvis.agents.AgentRepository;
import ai.jarvis.agents.AgentRequest;
import ai.jarvis.agents.AgentStatus;
import ai.jarvis.auth.AuthService;
import ai.jarvis.auth.LoginRequest;
import ai.jarvis.auth.LoginResponse;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.users.User;
import ai.jarvis.users.UserRepository;
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
@DisplayName("Agent API Integration Tests")
class AgentApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private AgentOrchestrator orchestrator;

    private String jwtToken;
    private UUID currentUserId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        agentRepository.deleteAll().block();
        userRepository.deleteAll().block();

        currentUserId = UUID.randomUUID();
        User currentUser = User.builder()
                .id(currentUserId)
                .email("user@jarvis.ai")
                .password("encoded_password")
                .build();
        userRepository.save(currentUser).block();

        otherUserId = UUID.randomUUID();
        User otherUser = User.builder()
                .id(otherUserId)
                .email("other@jarvis.ai")
                .password("encoded_password")
                .build();
        userRepository.save(otherUser).block();

        LoginResponse loginResponse = authService.login(new LoginRequest("user@jarvis.ai", "password")).block();
        if (loginResponse != null) {
            jwtToken = loginResponse.token();
        }
    }

    @AfterEach
    void tearDown() {
        agentRepository.deleteAll().block();
        userRepository.deleteAll().block();
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

        StepVerifier.create(agentRepository.findAll())
                .expectNextMatches(agent -> agent.getUserId().equals(currentUserId) && agent.getGoal().equals("Integrate Goal"))
                .verifyComplete();
    }

    @Test
    @DisplayName("GET /api/v1/agents enforces database cross-tenant tenant isolation")
    void shouldEnforceOwnershipIsolationOnList() {
        Agent currentUserAgent = Agent.builder()
                .id(UUID.randomUUID())
                .userId(currentUserId)
                .sessionId(UUID.randomUUID())
                .goal("Current User Goal")
                .status(AgentStatus.RUNNING)
                .build();
        agentRepository.save(currentUserAgent).block();

        Agent otherUserAgent = Agent.builder()
                .id(UUID.randomUUID())
                .userId(otherUserId)
                .sessionId(UUID.randomUUID())
                .goal("Other User Goal")
                .status(AgentStatus.RUNNING)
                .build();
        agentRepository.save(otherUserAgent).block();

        webTestClient.get()
                .uri("/api/v1/agents")
                .header("Authorization", "Bearer " + jwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].goal").isEqualTo("Current User Goal")
                .jsonPath("$.data[0].userId").isEqualTo(currentUserId.toString());
    }
}
