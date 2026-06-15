package ai.jarvis.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Core memory management service.
 *
 * RESPONSIBILITIES:
 * - Save memories (with and without embeddings)
 * - Retrieve memories (by importance or semantically)
 * - Delete memories (with ownership verification)
 * - Format memories for AI prompt injection
 *
 * PHASE 2 FEATURES:
 * - saveWithEmbedding(): generates and stores pgvector embedding
 * - formatForPrompt(userId, userQuery): semantic search first,
 *   falls back to importance-based if no embeddings available
 *
 * SECURITY:
 * - Ownership verified before delete (404 not 403)
 * - DB unique constraint prevents concurrent duplicates
 * - Application-level duplicate check as fast-path optimization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final EmbeddingService embeddingService;
    private final MemoryEmbeddingRepository
            embeddingRepository;

    /**
     * Default number of memories to inject into one prompt.
     * Too many = fills context window.
     * Too few = misses important context.
     */
    private static final int DEFAULT_PROMPT_LIMIT = 5;

    /**
     * Minimum cosine similarity for semantic search results.
     * Below this = probably not relevant to the query.
     * Range: 0.0 (unrelated) to 1.0 (identical meaning)
     */
    private static final double MIN_SIMILARITY = 0.5;

    // ── Save Operations ───────────────────────────

    /**
     * Save a memory with duplicate prevention.
     *
     * BUSINESS RULES:
     * 1. Blank content is rejected immediately
     * 2. Application-level duplicate check (fast path)
     * 3. DB unique constraint catches concurrent duplicates
     * 4. PostgreSQL error 23505 handled gracefully
     *
     * NOTE: Does NOT generate embedding.
     * Use saveWithEmbedding() for semantic search support.
     *
     * @param userId        owner of this memory
     * @param type          FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content       the actual memory text
     * @param sourceSession which session this came from (null = manual)
     * @return saved Memory or empty if duplicate/blank
     */
    public Mono<Memory> save(
            UUID userId,
            MemoryType type,
            String content,
            UUID sourceSession) {

        if (content == null || content.isBlank()) {
            log.debug(
                    "Skipping empty memory for user={}",
                    userId);
            return Mono.empty();
        }

        String trimmedContent = content.trim();

        return memoryRepository
                .existsByUserIdAndContentIgnoreCase(
                        userId, trimmedContent)
                .flatMap(exists -> {
                    if (exists) {
                        log.debug(
                                "Skipping duplicate memory "
                                        + "for user={}",
                                userId);
                        return Mono.empty();
                    }

                    Memory memory = Memory.create(
                            userId,
                            type,
                            trimmedContent,
                            sourceSession);

                    // Use insert() not save()
                    // save() with non-null UUID = UPDATE
                    // insert() always = INSERT
                    return r2dbcEntityTemplate
                            .insert(memory)
                            .doOnSuccess(saved ->
                                    log.info(
                                            "Memory saved: "
                                                    + "type={} user={}",
                                            saved.type(),
                                            saved.userId()))
                            .onErrorResume(error -> {
                                String msg =
                                        error.getMessage();
                                // Handle concurrent insert race
                                // PostgreSQL 23505 = unique_violation
                                if (msg != null && (
                                        msg.contains("unique")
                                                || msg.contains("duplicate")
                                                || msg.contains("23505"))) {
                                    log.debug(
                                            "DB constraint prevented "
                                                    + "duplicate: user={}",
                                            userId);
                                    return Mono.empty();
                                }
                                return Mono.error(error);
                            });
                });
    }

    /**
     * Save memory AND generate + store its embedding.
     *
     * PREFERRED save method for Phase 2.
     * Called by MemoryExtractionService after each chat.
     *
     * FLOW:
     * 1. Save memory record to DB via R2DBC (fast, ~10ms)
     * 2. Generate embedding via Ollama (~200ms, on boundedElastic)
     * 3. Store embedding via JDBC/pgvector (~10ms)
     *
     * RESILIENCE:
     * If embedding generation fails: memory is still saved.
     * Semantic search skips memories without embeddings.
     * Importance-based search acts as fallback.
     *
     * @param userId        owner of this memory
     * @param type          FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content       the actual memory text
     * @param sourceSession which session this came from
     * @return saved Memory (with or without embedding)
     */
    public Mono<Memory> saveWithEmbedding(
            UUID userId,
            MemoryType type,
            String content,
            UUID sourceSession) {

        return save(userId, type, content, sourceSession)
                .flatMap(savedMemory ->
                        embeddingService
                                // Fix 2: use savedMemory.content()
                                // not raw content parameter.
                                // save() trims whitespace before storing.
                                // Embedding must match stored text exactly.
                                .embed(savedMemory.content())
                                .flatMap(embedding ->
                                        embeddingRepository
                                                .storeEmbedding(
                                                        savedMemory.id(),
                                                        embedding)
                                                .thenReturn(savedMemory)
                                )
                                .onErrorReturn(savedMemory)
                                .defaultIfEmpty(savedMemory)
                );
    }

    /**
     * Save a memory added manually by the user.
     * No session reference (CLI or API origin).
     * Delegates to save() for duplicate checking.
     *
     * @param userId  owner of this memory
     * @param type    FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content the actual memory text
     */
    public Mono<Memory> saveManual(
            UUID userId,
            MemoryType type,
            String content) {
        return save(userId, type, content, null);
    }

    // ── Retrieve Operations ───────────────────────

    /**
     * Get ALL memories for a user ordered by importance.
     * Used by CLI "memory list" and REST API.
     *
     * @param userId who to fetch memories for
     */
    public Flux<Memory> getAll(UUID userId) {
        return memoryRepository
                .findByUserIdOrderByImportanceDesc(userId);
    }

    /**
     * Get top N memories ordered by importance + access count.
     * Used as fallback when semantic search unavailable.
     * Also tracks access count for relevance scoring.
     *
     * @param userId who to fetch memories for
     * @param limit  max memories to return (must be positive)
     */
    public Flux<Memory> getTop(UUID userId, int limit) {
        if (limit <= 0) {
            return Flux.empty();
        }
        return memoryRepository
                .findTopMemoriesByUserId(userId, limit)
                .doOnNext(memory ->
                        // Track access async — never blocks the stream
                        memoryRepository
                                .incrementAccessCount(memory.id())
                                .subscribe(
                                        null,
                                        error -> log.warn(
                                                "Access count update "
                                                        + "failed for memory={}: {}",
                                                memory.id(),
                                                error.getMessage()))
                );
    }

    /**
     * Get top memories using default limit.
     * Convenience method for prompt injection.
     *
     * @param userId who to fetch memories for
     */
    public Flux<Memory> getTopForPrompt(UUID userId) {
        return getTop(userId, DEFAULT_PROMPT_LIMIT);
    }

    /**
     * Get memories filtered by specific type.
     * Used for targeted retrieval (e.g. only GOALs).
     *
     * @param userId who to fetch memories for
     * @param type   which type to filter by
     */
    public Flux<Memory> getByType(
            UUID userId, MemoryType type) {
        return memoryRepository
                .findByUserIdAndTypeOrderByImportanceDesc(
                        userId, type);
    }

    /**
     * Count total memories for a user.
     * Used by CLI "memory list" header display.
     *
     * @param userId who to count memories for
     */
    public Mono<Long> count(UUID userId) {
        return memoryRepository.countByUserId(userId);
    }

    // ── Delete Operations ─────────────────────────

    /**
     * Delete a specific memory with ownership verification.
     *
     * SECURITY:
     * Returns 404 (not 403) whether memory missing OR wrong owner.
     * This prevents attackers learning whether a memory ID exists.
     * Verified by checking findByIdAndUserId before deleting.
     *
     * @param memoryId which memory to delete
     * @param userId   must be the owner
     */
    public Mono<Void> delete(
            UUID memoryId, UUID userId) {

        return memoryRepository
                .findByIdAndUserId(memoryId, userId)
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Memory not found"
                        )
                ))
                .flatMap(memoryRepository::delete)
                .doOnSuccess(v ->
                        log.info(
                                "Memory deleted: id={} user={}",
                                memoryId, userId));
    }

    /**
     * Delete ALL memories for a user.
     * Used by CLI "memory clear" command.
     * No ownership check needed — userId is the scope.
     *
     * @param userId who to clear memories for
     */
    public Mono<Void> deleteAll(UUID userId) {
        return memoryRepository
                .deleteByUserId(userId)
                .doOnSuccess(v ->
                        log.info(
                                "All memories cleared: user={}",
                                userId));
    }

    // ── Prompt Formatting ─────────────────────────

    /**
     * Format memories for AI prompt injection with semantic search.
     *
     * STRATEGY:
     * 1. If userQuery provided: embed it and search by similarity
     * 2. If semantic search returns results: use them
     * 3. Otherwise fall back to importance-based lookup
     * 4. If no memories at all: return empty string
     *
     * CALLED BY: AiOrchestrator before every AI request
     *
     * @param userId    whose memories to search
     * @param userQuery current user message for semantic matching
     * @return formatted memory string for PromptAssembler injection
     */
    public Mono<String> formatForPrompt(
            UUID userId,
            String userQuery) {

        if (userQuery != null && !userQuery.isBlank()) {
            return embeddingService
                    .embed(userQuery)
                    .flatMap(queryEmbedding ->
                            embeddingRepository
                                    .searchSimilar(
                                            userId,
                                            queryEmbedding,
                                            DEFAULT_PROMPT_LIMIT,
                                            MIN_SIMILARITY)
                                    .collectList()
                    )
                    .flatMap(results -> {
                        if (!results.isEmpty()) {
                            log.debug(
                                    "Semantic search found {} "
                                            + "memories for user={}",
                                    results.size(), userId);

                            StringBuilder sb =
                                    new StringBuilder();
                            sb.append(
                                    "=== WHAT I KNOW "
                                            + "ABOUT YOU ===\n");
                            for (var r : results) {
                                log.debug(
                                        "Memory: similarity={} "
                                                + "type={}",
                                        String.format(
                                                "%.2f", r.similarity()),
                                        r.type());
                                sb.append("- [")
                                        .append(r.type().name())
                                        .append("] ")
                                        .append(r.content())
                                        .append("\n");
                            }
                            sb.append(
                                    "============================");
                            return Mono.just(sb.toString());
                        }

                        log.debug(
                                "No semantic results for user={}, "
                                        + "using importance fallback",
                                userId);
                        return fallbackFormat(userId);
                    })
                    .onErrorResume(error -> {
                        log.debug(
                                "Semantic search failed for "
                                        + "user={}, using fallback: {}",
                                userId,
                                error.getClass().getSimpleName());
                        return fallbackFormat(userId);
                    })
                    // Fix: Mono.defer makes fallback LAZY
                    // Only evaluated if source Mono is empty
                    // Without defer: fallbackFormat() called
                    // immediately even when source has value
                    .switchIfEmpty(
                            Mono.defer(() ->
                                    fallbackFormat(userId)));
        }

        return fallbackFormat(userId);
    }

    /**
     * Format memories by importance without semantic search.
     * Backward-compatible method — used when no user query available.
     *
     * @param userId whose memories to format
     * @return formatted memory string or empty string
     */
    public Mono<String> formatForPrompt(UUID userId) {
        return fallbackFormat(userId);
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Format top memories by importance as prompt string.
     * Used as fallback when semantic search is unavailable.
     *
     * @param userId whose memories to load
     * @return formatted string or empty string if no memories
     */
    private Mono<String> fallbackFormat(UUID userId) {
        return getTop(userId, DEFAULT_PROMPT_LIMIT)
                .collectList()
                .map(memories -> {
                    if (memories.isEmpty()) {
                        return "";
                    }

                    StringBuilder sb =
                            new StringBuilder();
                    sb.append(
                            "=== WHAT I KNOW "
                                    + "ABOUT YOU ===\n");
                    for (Memory memory : memories) {
                        sb.append("- [")
                                .append(memory.type().name())
                                .append("] ")
                                .append(memory.content())
                                .append("\n");
                    }
                    sb.append(
                            "============================");
                    return sb.toString();
                });
    }
}