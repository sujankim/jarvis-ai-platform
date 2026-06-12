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
    }

    @Test
    @DisplayName("save() skips duplicate memories")
    void saveShouldSkipDuplicates() {
        when(memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        userId,
                        "User is building Jarvis"))
                .thenReturn(Mono.just(true));

        StepVerifier
                .create(memoryService.save(
                        userId, MemoryType.FACT,
                        "User is building Jarvis",
                        sessionId))
                .verifyComplete();
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

    // ── getTop() tests ────────────────────────────

    @Test
    @DisplayName("getTop() returns empty for limit <= 0")
    void getTopShouldReturnEmptyForNonPositiveLimit() {
        StepVerifier
                .create(memoryService.getTop(userId, 0))
                .verifyComplete();

        StepVerifier
                .create(memoryService.getTop(userId, -1))
                .verifyComplete();
    }

    // ── count() tests ─────────────────────────────

    @Test
    @DisplayName("count() returns correct count")
    void countShouldReturnCorrectCount() {
        when(memoryRepository.countByUserId(userId))
                .thenReturn(Mono.just(5L));

        StepVerifier
                .create(memoryService.count(userId))
                .expectNext(5L)
                .verifyComplete();
    }

    // ── delete() tests ─────────────────────────────

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

        // Verify delete was NEVER called
        // (ownership check failed correctly)
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
    @DisplayName("formatForPrompt() empty for limit <= 0")
    void formatForPromptShouldReturnEmptyForZeroLimit() {
        StepVerifier
                .create(memoryService
                        .formatForPrompt(userId, 0))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    @DisplayName("formatForPrompt() empty when no memories")
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
}