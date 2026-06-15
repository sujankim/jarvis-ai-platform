package ai.jarvis.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryService Tests")
class MemoryServiceTest {

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MemoryEmbeddingRepository embeddingRepository;

    @InjectMocks
    private MemoryService memoryService;

    private UUID userId;
    private UUID sessionId;
    private Memory testMemory;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        testMemory = new Memory(
                UUID.randomUUID(),
                userId,
                MemoryType.FACT,
                "User is building Jarvis",
                sessionId,
                0.50, 0, null,
                Instant.now(), Instant.now()
        );
    }

    // ── save() tests ──────────────────────────────

    @Test
    @DisplayName("save() returns empty for blank content")
    void saveShouldReturnEmptyForBlankContent() {
        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        "   ", sessionId))
                .verifyComplete();

        verify(memoryRepository, never())
                .existsByUserIdAndContentIgnoreCase(
                        any(), any());
    }

    @Test
    @DisplayName("save() returns empty for null content")
    void saveShouldReturnEmptyForNullContent() {
        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        null, sessionId))
                .verifyComplete();
    }

    @Test
    @DisplayName("save() skips duplicate memories")
    void saveShouldSkipDuplicates() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        userId, "User is building Jarvis"))
                .thenReturn(Mono.just(true));

        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .verifyComplete();

        verify(r2dbcEntityTemplate, never())
                .insert(any(Memory.class));
    }

    @Test
    @DisplayName("save() saves unique memories")
    void saveShouldSaveUniqueMemory() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        any(), any()))
                .thenReturn(Mono.just(false));

        when(r2dbcEntityTemplate
                .insert(any(Memory.class)))
                .thenReturn(Mono.just(testMemory));

        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .expectNextMatches(m ->
                        m.type() == MemoryType.FACT
                                && m.content().equals(
                                "User is building Jarvis"))
                .verifyComplete();
    }

    @Test
    @DisplayName("save() handles DB unique constraint violation")
    void saveShouldHandleDbUniqueViolation() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        any(), any()))
                .thenReturn(Mono.just(false));

        // Simulate DB unique constraint violation
        when(r2dbcEntityTemplate
                .insert(any(Memory.class)))
                .thenReturn(Mono.error(
                        new RuntimeException(
                                "ERROR: duplicate key value "
                                        + "violates unique constraint "
                                        + "(23505)")));

        // Should complete empty, not throw error
        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .verifyComplete();
    }

    // ── saveWithEmbedding() tests ─────────────────

    @Test
    @DisplayName("saveWithEmbedding() saves and stores embedding")
    void saveWithEmbeddingShouldStoreEmbedding() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        any(), any()))
                .thenReturn(Mono.just(false));

        when(r2dbcEntityTemplate
                .insert(any(Memory.class)))
                .thenReturn(Mono.just(testMemory));

        float[] embedding = new float[768];
        when(embeddingService.embed(any()))
                .thenReturn(Mono.just(embedding));

        when(embeddingRepository
                .storeEmbedding(any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(memoryService.saveWithEmbedding(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .expectNextMatches(m ->
                        m.type() == MemoryType.FACT)
                .verifyComplete();

        verify(embeddingService).embed(any());
        verify(embeddingRepository)
                .storeEmbedding(any(), any());
    }

    @Test
    @DisplayName("saveWithEmbedding() succeeds even if embedding fails")
    void saveWithEmbeddingShouldSucceedOnEmbeddingFailure() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        any(), any()))
                .thenReturn(Mono.just(false));

        when(r2dbcEntityTemplate
                .insert(any(Memory.class)))
                .thenReturn(Mono.just(testMemory));

        // Embedding service fails
        when(embeddingService.embed(any()))
                .thenReturn(Mono.error(
                        new RuntimeException("Ollama error")));

        // Memory still returned despite embedding failure
        StepVerifier
                .create(memoryService.saveWithEmbedding(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .expectNextMatches(m ->
                        m.type() == MemoryType.FACT)
                .verifyComplete();
    }

    // ── getTop() tests ────────────────────────────

    @Test
    @DisplayName("getTop() returns empty for limit zero")
    void getTopShouldReturnEmptyForZeroLimit() {
        StepVerifier
                .create(memoryService.getTop(userId, 0))
                .verifyComplete();

        verify(memoryRepository, never())
                .findTopMemoriesByUserId(any(), anyInt());
    }

    @Test
    @DisplayName("getTop() returns empty for negative limit")
    void getTopShouldReturnEmptyForNegativeLimit() {
        StepVerifier
                .create(memoryService.getTop(userId, -5))
                .verifyComplete();
    }

    // ── count() tests ─────────────────────────────

    @Test
    @DisplayName("count() returns correct memory count")
    void countShouldReturnCorrectCount() {
        when(memoryRepository.countByUserId(userId))
                .thenReturn(Mono.just(5L));

        StepVerifier
                .create(memoryService.count(userId))
                .expectNext(5L)
                .verifyComplete();
    }

    // ── delete() tests ────────────────────────────

    @Test
    @DisplayName("delete() returns 404 when not owned")
    void deleteShouldReturn404WhenNotOwned() {
        UUID memoryId = UUID.randomUUID();

        when(memoryRepository
                .findByIdAndUserId(memoryId, userId))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(memoryService
                        .delete(memoryId, userId))
                .expectErrorMatches(ex ->
                        ex instanceof ResponseStatusException rse
                                && rse.getStatusCode().value() == 404)
                .verify();

        // delete must NEVER be called when not authorized
        verify(memoryRepository, never())
                .delete(any(Memory.class));
    }

    @Test
    @DisplayName("delete() succeeds when owned by user")
    void deleteShouldSucceedWhenOwned() {
        UUID memoryId = testMemory.id();

        when(memoryRepository
                .findByIdAndUserId(memoryId, userId))
                .thenReturn(Mono.just(testMemory));

        when(memoryRepository.delete(testMemory))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(memoryService
                        .delete(memoryId, userId))
                .verifyComplete();

        // Verify delete WAS called with correct memory
        verify(memoryRepository).delete(testMemory);
    }

    // ── formatForPrompt() tests ───────────────────

    @Test
    @DisplayName("formatForPrompt() returns empty for no memories")
    void formatForPromptShouldReturnEmptyForNoMemories() {
        when(memoryRepository
                .findTopMemoriesByUserId(
                        eq(userId), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier
                .create(memoryService
                        .formatForPrompt(userId))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    @DisplayName("formatForPrompt() includes type and content")
    void formatForPromptShouldIncludeMemories() {
        when(memoryRepository
                .findTopMemoriesByUserId(
                        eq(userId), anyInt()))
                .thenReturn(Flux.just(testMemory));

        when(memoryRepository
                .incrementAccessCount(any()))
                .thenReturn(Mono.just(1));

        StepVerifier
                .create(memoryService
                        .formatForPrompt(userId))
                .expectNextMatches(result ->
                        result.contains("[FACT]")
                                && result.contains(
                                "User is building Jarvis")
                                && result.contains("WHAT I KNOW"))
                .verifyComplete();
    }

    @Test
    @DisplayName("formatForPrompt(userId, query) uses semantic search")
    void formatForPromptWithQueryShouldUseSemanticSearch() {
        float[] embedding = new float[768];
        String userQuery = "what is my coding project?";

        when(embeddingService.embed(userQuery))
                .thenReturn(Mono.just(embedding));

        MemoryEmbeddingRepository.SemanticSearchResult result =
                new MemoryEmbeddingRepository
                        .SemanticSearchResult(
                        testMemory.id(),
                        MemoryType.FACT,
                        "User is building Jarvis",
                        0.80, 5, 0.87
                );

        when(embeddingRepository.searchSimilar(
                eq(userId), any(), anyInt(), anyDouble()))
                .thenReturn(Flux.just(result));

        StepVerifier
                .create(memoryService.formatForPrompt(
                        userId, userQuery))
                .expectNextMatches(formatted ->
                        formatted.contains("[FACT]")
                                && formatted.contains(
                                "User is building Jarvis")
                                && formatted.contains("WHAT I KNOW"))
                .verifyComplete();

        // Verify embedding was called for the query
        verify(embeddingService).embed(userQuery);
        verify(embeddingRepository).searchSimilar(
                eq(userId), any(), anyInt(), anyDouble());
    }

    @Test
    @DisplayName("formatForPrompt() falls back when semantic empty")
    void formatForPromptShouldFallbackWhenSemanticEmpty() {
        float[] embedding = new float[768];
        String userQuery = "what is my coding project?";

        when(embeddingService.embed(userQuery))
                .thenReturn(Mono.just(embedding));

        // Semantic search returns nothing
        when(embeddingRepository.searchSimilar(
                eq(userId), any(), anyInt(), anyDouble()))
                .thenReturn(Flux.empty());

        // Fallback: importance-based
        when(memoryRepository
                .findTopMemoriesByUserId(
                        eq(userId), anyInt()))
                .thenReturn(Flux.just(testMemory));

        when(memoryRepository
                .incrementAccessCount(any()))
                .thenReturn(Mono.just(1));

        StepVerifier
                .create(memoryService.formatForPrompt(
                        userId, userQuery))
                .expectNextMatches(formatted ->
                        formatted.contains("[FACT]")
                                && formatted.contains(
                                "User is building Jarvis"))
                .verifyComplete();
    }

    @Test
    @DisplayName("formatForPrompt() falls back when embedding fails")
    void formatForPromptShouldFallbackOnEmbeddingError() {
        String userQuery = "what is my coding project?";

        // Embedding service fails
        when(embeddingService.embed(userQuery))
                .thenReturn(Mono.error(
                        new RuntimeException("Ollama down")));

        // Fallback: importance-based
        when(memoryRepository
                .findTopMemoriesByUserId(
                        eq(userId), anyInt()))
                .thenReturn(Flux.just(testMemory));

        when(memoryRepository
                .incrementAccessCount(any()))
                .thenReturn(Mono.just(1));

        // Should NOT fail — graceful fallback
        StepVerifier
                .create(memoryService.formatForPrompt(
                        userId, userQuery))
                .expectNextMatches(formatted ->
                        formatted.contains("[FACT]"))
                .verifyComplete();
    }

    @Test
    @DisplayName("formatForPrompt() limit zero returns empty")
    void formatForPromptWithZeroLimitReturnsEmpty() {
        // getTop with 0 → Flux.empty()
        // fallbackFormat collects → empty list → ""
        when(memoryRepository
                .findTopMemoriesByUserId(
                        eq(userId), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier
                .create(memoryService
                        .formatForPrompt(userId))
                .expectNext("")
                .verifyComplete();
    }
}