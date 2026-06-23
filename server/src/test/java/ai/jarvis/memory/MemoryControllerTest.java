package ai.jarvis.memory;

import static ai.jarvis.memory.MemoryFactory.MEMORY_REQUEST_WITH_BLANK_CONTENT;
import static ai.jarvis.memory.MemoryFactory.MEMORY_REQUEST_WITH_MISSING_CONTENT;
import static ai.jarvis.memory.MemoryFactory.MEMORY_REQUEST_WITH_MISSING_MEMORY_TYPE;
import static ai.jarvis.memory.MemoryFactory.MEMORY_REQUEST_WITH_NULL_CONTENT;
import static ai.jarvis.memory.MemoryFactory.MEMORY_REQUEST_WITH_NULL_MEMORY_TYPE;
import static ai.jarvis.memory.MemoryFactory.createMemoryRequestJson;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
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

@WebFluxTest(controllers = {MemoryController.class})
@ExtendWith(MockitoExtension.class)
@Import(TestSecurityConfig.class)
@WithMockJarvisUser(principal = MemoryControllerTest.USER_ID_RAW)
class MemoryControllerTest {

    public static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";
    public static final UUID USER_ID = UUID.fromString(USER_ID_RAW);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MemoryService memoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private MemoryMapper memoryMapper;

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return empty list when no memories found")
    void testListMemories_ShouldReturnEmptyListWhenNoMemoriesFound() {
        // Given
        when(this.memoryService.getAll(USER_ID)).thenReturn(Flux.empty());

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("Test GET /api/v1/memories - Should return user's memories")
    void testListMemories_ShouldReturnMemoriesForUser() {
        // Given
        Memory firstMemory = mock(Memory.class);
        Memory secondMemory = mock(Memory.class);

        MemoryResponse firstMemoryResponse = new MemoryResponse(UUID.randomUUID(), MemoryType.FACT, "First Memory", 1.0, 1, Instant.now(), Instant.now().minus(Duration.ofDays(2)));
        MemoryResponse secondMemoryResponse = new MemoryResponse(UUID.randomUUID(), MemoryType.GOAL, "Second Memory", 2.0, 2, Instant.now(), Instant.now().minus(Duration.ofDays(3)));

        when(this.memoryService.getAll(USER_ID)).thenReturn(Flux.fromIterable(List.of(firstMemory, secondMemory)));

        when(this.memoryMapper.toResponse(firstMemory)).thenReturn(firstMemoryResponse);
        when(this.memoryMapper.toResponse(secondMemory)).thenReturn(secondMemoryResponse);

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].length()").isEqualTo(7)
                .jsonPath("$.data[0].id").isEqualTo(firstMemoryResponse.id().toString())
                .jsonPath("$.data[0].type").isEqualTo(firstMemoryResponse.type().toString())
                .jsonPath("$.data[0].content").isEqualTo(firstMemoryResponse.content())
                .jsonPath("$.data[0].importance").isEqualTo(firstMemoryResponse.importance())
                .jsonPath("$.data[0].accessCount").isEqualTo(firstMemoryResponse.accessCount())
                .jsonPath("$.data[0].lastAccessed").isEqualTo(ISO_INSTANT.format(firstMemoryResponse.lastAccessed()))
                .jsonPath("$.data[0].createdAt").isEqualTo(ISO_INSTANT.format(firstMemoryResponse.createdAt()))
                .jsonPath("$.data[1].length()").isEqualTo(7)
                .jsonPath("$.data[1].id").isEqualTo(secondMemoryResponse.id().toString())
                .jsonPath("$.data[1].type").isEqualTo(secondMemoryResponse.type().toString())
                .jsonPath("$.data[1].content").isEqualTo(secondMemoryResponse.content())
                .jsonPath("$.data[1].importance").isEqualTo(secondMemoryResponse.importance())
                .jsonPath("$.data[1].accessCount").isEqualTo(secondMemoryResponse.accessCount())
                .jsonPath("$.data[1].lastAccessed").isEqualTo(ISO_INSTANT.format(secondMemoryResponse.lastAccessed()))
                .jsonPath("$.data[1].createdAt").isEqualTo(ISO_INSTANT.format(secondMemoryResponse.createdAt()));
    }

    @Test
    @DisplayName("Test GET /api/v1/memories/count - Should return memory count")
    void testCount_ShouldReturnCount() {
        // Given
        long count = 10;
        when(this.memoryService.count(USER_ID)).thenReturn(Mono.just(count));

        // When + Then
        this.webTestClient
                .get()
                .uri("/api/v1/memories/count")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.count").isEqualTo(count);
    }

    @Test
    @DisplayName("Test POST /api/v1/memories - Should save memory")
    void testCreate_ShouldSaveMemory() {
        // Given
        String memoryContent = "Memory content";
        MemoryRequest memoryRequest = new MemoryRequest(MemoryType.FACT, memoryContent);
        String memoryRequestJson = createMemoryRequestJson(MemoryType.FACT, memoryContent);

        Memory memory = mock(Memory.class);
        MemoryResponse memoryResponse = new MemoryResponse(UUID.randomUUID(), MemoryType.FACT, "First Memory", 1.0, 1, Instant.now(), Instant.now().minus(Duration.ofDays(2)));

        when(this.memoryService.saveManual(USER_ID, memoryRequest)).thenReturn(Mono.just(memory));
        when(this.memoryMapper.toResponse(memory)).thenReturn(memoryResponse);

        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(memoryRequestJson)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(7)
                .jsonPath("$.data.id").isEqualTo(memoryResponse.id().toString())
                .jsonPath("$.data.type").isEqualTo(memoryResponse.type().toString())
                .jsonPath("$.data.content").isEqualTo(memoryResponse.content())
                .jsonPath("$.data.importance").isEqualTo(memoryResponse.importance())
                .jsonPath("$.data.accessCount").isEqualTo(memoryResponse.accessCount())
                .jsonPath("$.data.lastAccessed").isEqualTo(ISO_INSTANT.format(memoryResponse.lastAccessed()))
                .jsonPath("$.data.createdAt").isEqualTo(ISO_INSTANT.format(memoryResponse.createdAt()));
    }

    @Test
    @DisplayName("Test POST /api/v1/memories - Should return conflict when saving memory with already existing content")
    void testCreate_ShouldSendConflictWhenSavingAlreadyExistingMemoryContent() {
        // Given
        String memoryContent = "Memory content";
        MemoryRequest memoryRequest = new MemoryRequest(MemoryType.FACT, memoryContent);
        String memoryRequestJson = createMemoryRequestJson(MemoryType.FACT, memoryContent);

        when(this.memoryService.saveManual(USER_ID, memoryRequest)).thenReturn(Mono.empty());

        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(memoryRequestJson)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    private static Stream<Arguments> provideInvalidMemoryType() {
        return Stream.of(
                Arguments.of("Should return bad request for null memory type", MEMORY_REQUEST_WITH_NULL_MEMORY_TYPE),
                Arguments.of("Should return bad request for missing memory type", MEMORY_REQUEST_WITH_MISSING_MEMORY_TYPE)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvalidMemoryType")
    @DisplayName("Test POST /api/v1/memories - Should return bad request for null memory type")
    void testCreate_ShouldReturnBadResponseWhenMemoryTypeIsNull(String testName, String memoryRequestJson) {
        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(memoryRequestJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static Stream<Arguments> provideInvalidContent() {
        return Stream.of(
                Arguments.of("Should return bad request for null content", MEMORY_REQUEST_WITH_NULL_CONTENT),
                Arguments.of("Should return bad request for missing content", MEMORY_REQUEST_WITH_MISSING_CONTENT),
                Arguments.of("Should return bad request for blank content", MEMORY_REQUEST_WITH_BLANK_CONTENT)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInvalidContent")
    @DisplayName("Test POST /api/v1/memories - Should return bad request for blank memory content")
    void testCreate_ShouldReturnBadResponseWhenContentIsBlank(String testName, String memoryRequestWithInvalidContent) {
        // When + Then
        this.webTestClient
                .post()
                .uri("/api/v1/memories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(memoryRequestWithInvalidContent)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Test DELTE /api/v1/memories/{memoryId} - Should delete memory by id")
    void testDeleteById_ShouldDeleteMemoryById() {
        // Given
        String memoryIdRaw = "5eff485b-1ca6-4d4f-b94c-c30c010de82b";
        UUID memoryId = UUID.fromString(memoryIdRaw);

        when(this.memoryService.delete(memoryId, USER_ID)).thenReturn(Mono.empty());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", memoryId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("Test DELTE /api/v1/memories/{memoryId} - Should return bad request when invalid memory id provided")
    void testDeleteById_ShouldReturnBadRequestWhenInvalidIdProvided() {
        // Given
        String invalidMemoryId = "invalid id";

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", invalidMemoryId)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Test DELTE /api/v1/memories/{memoryId} - Should return not found when memory was not found by id")
    void testDeleteById_ShouldReturnNotFoundWhenMemoryIdNotFound() {
        // Given
        String memoryIdRaw = "5eff485b-1ca6-4d4f-b94c-c30c010de82b";
        UUID memoryId = UUID.fromString(memoryIdRaw);
        ResponseStatusException memoryNotFoundException = new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found");

        when(this.memoryService.delete(memoryId, USER_ID)).thenReturn(Mono.error(memoryNotFoundException));

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories/{memoryId}", memoryId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Test DELTE /api/v1/memories - Should delete all memories")
    void testDeleteAll_ShouldDeleteAllMemories() {
        // Given
        when(this.memoryService.deleteAll(USER_ID)).thenReturn(Mono.empty());

        // When + Then
        this.webTestClient
                .delete()
                .uri("/api/v1/memories")
                .exchange()
                .expectStatus().isNoContent();
    }

}
