package ai.jarvis.integration;

import static ai.jarvis.memory.MemoryFactory.createMemoryRequestJson;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.memory.Memory;
import ai.jarvis.memory.MemoryRepository;
import ai.jarvis.memory.MemoryType;
import ai.jarvis.security.auth.AuthService;
import ai.jarvis.security.auth.request.LoginRequest;
import ai.jarvis.security.auth.response.TokenResponse;
import ai.jarvis.user.User;
import ai.jarvis.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = "spring.shell.interactive.enabled=false")
@AutoConfigureWebTestClient
@ImportTestcontainers(TestContainerConfig.class)
class MemoryApiIntegrationTest {

    public static final String USER_ID_RAW = "2dc30198-27df-44a4-a09b-5fba02b8569d";
    public static final UUID USER_ID = UUID.fromString(USER_ID_RAW);
    public static final String USERNAME = "john-doe";
    public static final String USER_EMAIL = "john.doe@example.com";
    public static final String USER_PASSWORD = "john_doe_password";
    public static final String USER_DISPLAY_NAME = "John Doe";

    public static final UUID ANOTHER_USER_ID = UUID.fromString("319ea309-3b01-49be-a971-c07ff7ea616c");

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private WebTestClient webTestClient;

    private String authHeaderValue;

    @BeforeEach
    void setUp() {
        String encodedPassword = this.passwordEncoder.encode(USER_PASSWORD);
        String anotherUserEncodedPassword = this.passwordEncoder.encode("123456789");

        User user = User.create(USER_ID, USERNAME, USER_EMAIL, encodedPassword, USER_DISPLAY_NAME, UserRole.USER);
        User anotherUser = User.create(ANOTHER_USER_ID, "adam-smith", "adam.smith@example.com", anotherUserEncodedPassword, "Adam Smith", UserRole.USER);

        this.r2dbcEntityTemplate.insert(user).block();
        this.r2dbcEntityTemplate.insert(anotherUser).block();

        LoginRequest loginRequest = new LoginRequest(USERNAME, USER_PASSWORD);
        TokenResponse tokenResponse = Objects.requireNonNull(this.authService.login(loginRequest).block());
        this.authHeaderValue = "Bearer " + tokenResponse.accessToken();
    }

    @AfterEach
    void tearDown() {
        this.r2dbcEntityTemplate.delete(User.class).all().block();
        this.r2dbcEntityTemplate.delete(Memory.class).all().block();
    }

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return unauthorized for request without token")
    void testListMemories_ShouldReturnUnauthorizedForRequestWithoutToken() {
        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return empty list when no memories found")
    void testListMemories_ShouldReturnEmptyListWhenNoMemoriesFound() {
        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return memories for user")
    void testListMemories_ShouldReturnMemoriesForUser() {
        // Given
        Memory firstMemory = Memory.create(USER_ID, MemoryType.FACT, "First Memory", null);
        Memory secondMemory = Memory.create(USER_ID, MemoryType.CONTEXT, "Second Memory", null);

        firstMemory = Objects.requireNonNull(this.r2dbcEntityTemplate.insert(firstMemory).block());
        secondMemory = Objects.requireNonNull(this.r2dbcEntityTemplate.insert(secondMemory).block());

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].length()").isEqualTo(7)
                .jsonPath("$.data[0].id").isEqualTo(firstMemory.id().toString())
                .jsonPath("$.data[0].type").isEqualTo(firstMemory.type().toString())
                .jsonPath("$.data[0].content").isEqualTo(firstMemory.content())
                .jsonPath("$.data[0].importance").isEqualTo(firstMemory.importance())
                .jsonPath("$.data[0].accessCount").isEqualTo(firstMemory.accessCount())
                .jsonPath("$.data[0].lastAccessed").isEqualTo(null)
                .jsonPath("$.data[0].createdAt").isEqualTo(ISO_INSTANT.format(firstMemory.createdAt()))
                .jsonPath("$.data[1].length()").isEqualTo(7)
                .jsonPath("$.data[1].id").isEqualTo(secondMemory.id().toString())
                .jsonPath("$.data[1].type").isEqualTo(secondMemory.type().toString())
                .jsonPath("$.data[1].content").isEqualTo(secondMemory.content())
                .jsonPath("$.data[1].importance").isEqualTo(secondMemory.importance())
                .jsonPath("$.data[1].accessCount").isEqualTo(secondMemory.accessCount())
                .jsonPath("$.data[1].lastAccessed").isEqualTo(null)
                .jsonPath("$.data[1].createdAt").isEqualTo(ISO_INSTANT.format(secondMemory.createdAt()));
    }

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return memories only for current user")
    void testListMemories_ShouldReturnMemoriesOnlyForCurrentUser() {
        // Given
        Memory anotherUsersFirstMemory = Memory.create(ANOTHER_USER_ID, MemoryType.FACT, "First memory", null);
        Memory anotherUsersSecondMemory = Memory.create(ANOTHER_USER_ID, MemoryType.GOAL, "Second memory", null);

        Memory targetMemory = Memory.create(USER_ID, MemoryType.FACT, "Target Memory", null);

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersFirstMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersSecondMemory).block());

        targetMemory = Objects.requireNonNull(this.r2dbcEntityTemplate.insert(targetMemory).block());

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].length()").isEqualTo(7)
                .jsonPath("$.data[0].id").isEqualTo(targetMemory.id().toString())
                .jsonPath("$.data[0].type").isEqualTo(targetMemory.type().toString())
                .jsonPath("$.data[0].content").isEqualTo(targetMemory.content())
                .jsonPath("$.data[0].importance").isEqualTo(targetMemory.importance())
                .jsonPath("$.data[0].accessCount").isEqualTo(targetMemory.accessCount())
                .jsonPath("$.data[0].lastAccessed").isEqualTo(null)
                .jsonPath("$.data[0].createdAt").isEqualTo(ISO_INSTANT.format(targetMemory.createdAt()));
    }

    @Test
    @DisplayName("Test GET /api/v1/memories/count - Should return unauthorized for request without token")
    void testCount_ShouldReturnUnauthorizedForRequestWithoutToken() {
        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories/count")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Test GET /api/v1/memories/count - Should return memory count")
    void testCount_ShouldReturnCount() {
        // Given
        Memory firstMemory = Memory.create(USER_ID, MemoryType.FACT, "First Memory", null);
        Memory secondMemory = Memory.create(USER_ID, MemoryType.CONTEXT, "Second Memory", null);

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(firstMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(secondMemory).block());

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories/count")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.count").isEqualTo(2);
    }

    @Test
    @DisplayName("Test GET /api/v1/memories/count - Should return memory count only for current user's memories")
    void testCount_ShouldReturnCountOnlyForCurrentUser() {
        // Given
        Memory anotherUsersFirstMemory = Memory.create(ANOTHER_USER_ID, MemoryType.FACT, "First memory", null);
        Memory anotherUsersSecondMemory = Memory.create(ANOTHER_USER_ID, MemoryType.GOAL, "Second memory", null);

        Memory targetMemory = Memory.create(USER_ID, MemoryType.FACT, "Target Memory", null);

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersFirstMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersSecondMemory).block());

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(targetMemory).block());

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories/count")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.count").isEqualTo(1);
    }

    @Test
    @DisplayName("Test POST /api/v1/memories - Should return unauthorized for request without token")
    void testCreate_ShouldReturnUnauthorizedForRequestWithoutToken() {
        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Test POST /api/v1/memories - Should save memory")
    void testCreate_ShouldSaveMemory() {
        // Given
        String memoryContent = "Memory content";
        String memoryRequestJson = createMemoryRequestJson(MemoryType.FACT, memoryContent);
        AtomicReference<UUID> atomicMemoryId = new AtomicReference<>();

        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .bodyValue(memoryRequestJson)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(7)
                .jsonPath("$.data.id").exists()
                .jsonPath("$.data.type").isEqualTo(MemoryType.FACT.toString())
                .jsonPath("$.data.content").isEqualTo(memoryContent)
                .jsonPath("$.data.importance").isEqualTo(0.5)
                .jsonPath("$.data.accessCount").isEqualTo(0)
                .jsonPath("$.data.lastAccessed").isEqualTo(null)
                .jsonPath("$.data.createdAt").exists()
                .jsonPath("$.data.id").value((String id) -> atomicMemoryId.set(UUID.fromString(id)));

        Memory memoryFromDb = Objects.requireNonNull(this.memoryRepository.findByIdAndUserId(atomicMemoryId.get(), USER_ID).block());
        assertNotNull(memoryFromDb.id());
        assertEquals(USER_ID, memoryFromDb.userId());
        assertEquals(MemoryType.FACT, memoryFromDb.type());
        assertNull(memoryFromDb.sourceSession());
        assertEquals(0.5, memoryFromDb.importance());
        assertEquals(0, memoryFromDb.accessCount());
        assertNull(memoryFromDb.lastAccessed());
        assertNotNull(memoryFromDb.createdAt());
        assertNotNull(memoryFromDb.updatedAt());
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories/{memoryId} - Should return unauthorized for request without token")
    void testDelete_ShouldReturnUnauthorizedForRequestWithoutToken() {
        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", USER_ID)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories/{memoryId} - Should delete memory by id")
    void testDelete_ShouldDeleteMemoryById() {
        // Given
        Memory memoryToDelete = Memory.create(USER_ID, MemoryType.FACT, "Memory Content", null);
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(memoryToDelete).block());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", memoryToDelete.id())
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isNoContent()
                .expectBody()
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories/{memoryId} - Should not delete memory related to another user")
    void testDelete_ShouldNotDeleteMemoryRelatedToAnotherUser() {
        // Given
        Memory anotherUsersMemory = Memory.create(ANOTHER_USER_ID, MemoryType.FACT, "Memory Content", null);
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersMemory).block());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", anotherUsersMemory.id())
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isNotFound();


        Memory memoryFromDb = Objects.requireNonNull(this.memoryRepository.findByIdAndUserId(anotherUsersMemory.id(), ANOTHER_USER_ID).block());
        assertEquals(anotherUsersMemory.id(), memoryFromDb.id());
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories - Should return unauthorized for request without token")
    void testDeleteAll_ShouldReturnUnauthorizedForRequestWithoutToken() {
        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories - Should delete all memory records")
    void testDeleteAll_ShouldDeleteAllMemoryRecords() {
        // Given
        Memory firstMemory = Memory.create(USER_ID, MemoryType.FACT, "First Memory", null);
        Memory secondMemory = Memory.create(USER_ID, MemoryType.CONTEXT, "Second Memory", null);

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(firstMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(secondMemory).block());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isNoContent();

        List<Memory> memoriesFromDb = this.memoryRepository.findAll().collectList().block();
        assertNotNull(memoriesFromDb);
        assertTrue(memoriesFromDb.isEmpty());
    }

    @Test
    @DisplayName("Test DELETE /api/v1/memories - Should delete all memory records only related to current user")
    void testDeleteAll_ShouldDeleteOnlyMemoriesRelatedToCurrentUser() {
        // Given
        Memory firstMemory = Memory.create(USER_ID, MemoryType.FACT, "First Memory", null);
        Memory secondMemory = Memory.create(USER_ID, MemoryType.CONTEXT, "Second Memory", null);
        Memory anotherUsersMemory = Memory.create(ANOTHER_USER_ID, MemoryType.GOAL, "Third Memory", null);

        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(firstMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(secondMemory).block());
        Objects.requireNonNull(this.r2dbcEntityTemplate.insert(anotherUsersMemory).block());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories")
                .header(HttpHeaders.AUTHORIZATION, this.authHeaderValue)
                .exchange()
                .expectStatus().isNoContent();

        List<Memory> memoriesFromDb = this.memoryRepository.findAll().collectList().block();
        assertNotNull(memoriesFromDb);
        assertEquals(1, memoriesFromDb.size());
        assertEquals(anotherUsersMemory.id(), memoriesFromDb.getFirst().id());
    }

}
