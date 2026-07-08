package ai.jarvis.integration;

import ai.jarvis.agents.AgentRequest;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.config.WithMockJarvisUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(TestContainerConfig.class)
@WithMockJarvisUser(principal = AgentApiIntegrationTest.USER_ID_RAW)
@DisplayName("Agent API Integration Tests")
class AgentApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";

    @Test
    @DisplayName("POST /api/v1/agents creates a new agent via full database orchestration")
    void shouldCreateAgentInDatabase() {
        AgentRequest request = new AgentRequest("Integrate Goal", UUID.randomUUID());

        webTestClient.post()
                .uri("/api/v1/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("GET /api/v1/agents enforces database cross-tenant tenant isolation")
    void shouldEnforceOwnershipIsolationOnList() {
        String otherUserId = "00000000-0000-0000-0000-000000000001";

        webTestClient.get()
                .uri("/api/v1/agents")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[?(@.userId == '%s')]".formatted(otherUserId)).doesNotExist();
    }
}
