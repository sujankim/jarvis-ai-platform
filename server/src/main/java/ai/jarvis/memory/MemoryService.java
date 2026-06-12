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
 * WHAT THIS SERVICE DOES:
 * Coordinates between the outside world (controllers, CLI)
 * and the database (MemoryRepository).
 *
 * BUSINESS RULES ENFORCED HERE:
 * 1. No empty content saved
 * 2. No duplicate memories for same user
 * 3. Only owner can delete their memory
 * 4. Access count tracked when memories retrieved
 * 5. Memories formatted correctly for prompt injection
 *
 * WHY SEPARATE FROM REPOSITORY:
 * Repository only knows SQL operations.
 * Service knows the RULES of the application.
 * Keeping them separate makes both easier to test.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // Maximum memories to inject into one prompt
    // Too many = fills context window
    // Too few = misses important context
    private static final int DEFAULT_PROMPT_LIMIT = 5;

    // ── Save Operations ───────────────────────────

    /**
     * Save a memory extracted from a conversation.
     *
     * WHY: AiOrchestrator calls this after each chat
     * to store what Jarvis learned about the user.
     *
     * BUSINESS RULES:
     * 1. Content must not be blank
     * 2. Skip if identical memory already exists
     *    (prevents duplicate extraction)
     *
     * @param userId        who this memory belongs to
     * @param type          FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content       the actual memory text
     * @param sourceSession which session this came from
     */
    public Mono<Memory> save(
            UUID userId,
            MemoryType type,
            String content,
            UUID sourceSession) {

        // Rule 1: Reject empty content
        // Empty strings waste DB space and
        // confuse the AI when injected into prompts
        if (content == null || content.isBlank()) {
            log.debug(
                    "Skipping empty memory for user={}",
                    userId);
            return Mono.empty();
        }

        String trimmedContent = content.trim();

        // Rule 2: Check for duplicate before saving
        // If user said "I use Java" twice in two sessions,
        // we should not store "User uses Java" twice.
        // .flatMap() here because existsByUserId...
        // is an async database call (returns Mono<Boolean>)
        // Application-level check (fast path optimization)
        // DB unique constraint is the real safety net
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

                    // No duplicate — create and save
                    Memory memory = Memory.create(
                            userId,
                            type,
                            trimmedContent,
                            sourceSession
                    );

                    // Use insert() not save()!
                    // save() with non-null UUID = UPDATE
                    // insert() always = INSERT
                    // We learned this lesson the hard way
                    // during authentication implementation
                    return r2dbcEntityTemplate
                            .insert(memory)
                            .doOnSuccess(saved ->
                                    log.info(
                                            "Memory saved: "
                                                    + "type={} user={}",
                                            saved.type(),
                                            saved.userId())
                            )
                            // Handle DB unique constraint violation
                            // (concurrent insert race condition)
                            .onErrorResume(error -> {
                                String msg = error.getMessage();
                                if (msg != null && (
                                        msg.contains("unique")
                                                || msg.contains("duplicate")
                                                || msg.contains("23505"))) {
                                    // PostgreSQL error code 23505
                                    // = unique_violation
                                    log.debug(
                                            "Concurrent duplicate "
                                                    + "prevented by DB "
                                                    + "constraint: user={}",
                                            userId);
                                    return Mono.empty();
                                }
                                // Other errors: re-throw
                                return Mono.error(error);
                            });
                });
    }

    /**
     * Save a memory added manually by the user.
     *
     * WHY SEPARATE FROM save():
     * Manual memories have no sourceSession.
     * They come from CLI "memory add" command.
     * The user explicitly chose to remember this.
     * We still check for duplicates.
     *
     * @param userId  who this memory belongs to
     * @param type    FACT/GOAL/PREFERENCE/CONTEXT/EVENT
     * @param content the actual memory text
     */
    public Mono<Memory> saveManual(
            UUID userId,
            MemoryType type,
            String content) {

        // Delegate to save() with null sourceSession
        // save() handles all validation rules
        return save(userId, type, content, null);
    }

    // ── Retrieve Operations ───────────────────────

    /**
     * Get ALL memories for a user.
     *
     * WHY: Used by CLI "memory list" command
     * and REST API GET /api/v1/memories.
     *
     * Returns Flux not List because:
     * → Flux = reactive stream (non-blocking)
     * → Items delivered ONE BY ONE as DB reads them
     * → Works perfectly with WebFlux SSE streaming
     * → More memory efficient for large lists
     *
     * @param userId who to fetch memories for
     */
    public Flux<Memory> getAll(UUID userId) {
        return memoryRepository
                .findByUserIdOrderByImportanceDesc(userId);
    }

    /**
     * Get top N memories for prompt injection.
     *
     * WHY: Called by PromptAssembler before every
     * AI request to inject relevant context.
     *
     * ALSO TRACKS ACCESS:
     * Each retrieved memory gets access_count++
     * This makes the system smarter over time:
     * frequently accessed memories rank higher.
     *
     * WHY ASYNC ACCESS TRACKING:
     * We fire-and-forget the incrementAccessCount.
     * We do NOT wait for it to complete.
     * Why? The user is waiting for AI response.
     * We must not slow down the chat for bookkeeping.
     *
     * @param userId how to fetch memories for
     * @param limit  max memories to return
     */
    public Flux<Memory> getTop(UUID userId, int limit) {
        // Guard: negative or zero limit returns nothing
        if (limit <= 0) {
            return Flux.empty();
        }

        return memoryRepository
                .findTopMemoriesByUserId(userId, limit)
                .doOnNext(memory ->
                        // Track access ASYNC
                        // .subscribe() = fire and forget
                        // Does NOT block the stream
                        memoryRepository
                                .incrementAccessCount(memory.id())
                                .subscribe(
                                        null,
                                        error -> log.warn(
                                                "Access count update "
                                                        + "failed for memory={}: {}",
                                                memory.id(),
                                                error.getMessage())
                                )
                );
    }

    /**
     * Get top memories using default limit (5).
     * Convenience method for prompt injection.
     *
     * @param userId who to fetch memories for
     */
    public Flux<Memory> getTopForPrompt(UUID userId) {
        return getTop(userId, DEFAULT_PROMPT_LIMIT);
    }

    /**
     * Get memories filtered by type.
     *
     * WHY: When user says "show my goals"
     * we only want GOAL type memories.
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
     *
     * WHY: CLI "memory list" shows count in header.
     * "Your memories (47 total)"
     *
     * @param userId who to count memories for
     */
    public Mono<Long> count(UUID userId) {
        return memoryRepository.countByUserId(userId);
    }

    // ── Delete Operations ─────────────────────────

    /**
     * Delete a specific memory.
     *
     * SECURITY RULE:
     * Verify the memory belongs to this user
     * BEFORE deleting it.
     *
     * WHY THIS MATTERS:
     * Without this check:
     * User A could delete User B's memories
     * by guessing UUIDs.
     *
     * With findByIdAndUserId():
     * → If memory exists AND belongs to userId → delete
     * → If memory not found OR wrong user → 404 error
     *
     * @param memoryId which memory to delete
     * @param userId   must be the owner
     */
    public Mono<Void> delete(
            UUID memoryId, UUID userId) {

        return memoryRepository
                .findByIdAndUserId(memoryId, userId)
                .switchIfEmpty(Mono.error(
                        // 404 if not found OR wrong owner
                        // We say "not found" not "forbidden"
                        // This prevents attackers from knowing
                        // if a memory ID exists at all
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Memory not found"
                        )
                ))
                .flatMap(memory ->
                        memoryRepository.delete(memory)
                )
                .doOnSuccess(v ->
                        log.info(
                                "Memory deleted: id={} user={}",
                                memoryId, userId)
                );
    }

    /**
     * Delete ALL memories for a user.
     *
     * WHY: CLI "memory clear" command.
     * User explicitly wants to wipe their memory.
     *
     * NOTE: No ownership check needed here because
     * we use userId directly — user can only delete
     * their OWN memories by definition.
     *
     * @param userId who to clear memories for
     */
    public Mono<Void> deleteAll(UUID userId) {
        return memoryRepository
                .deleteByUserId(userId)
                .doOnSuccess(v ->
                        log.info(
                                "All memories cleared: user={}",
                                userId)
                );
    }

    // ── Prompt Formatting ─────────────────────────

    /**
     * Format top memories as a string for AI prompt.
     *
     * WHY: PromptAssembler calls this to get a
     * formatted string to inject into the AI prompt.
     *
     * OUTPUT EXAMPLE:
     * "=== WHAT I KNOW ABOUT YOU ===
     *  - [FACT] Your name is Dravin
     *  - [GOAL] Building Jarvis AI Platform
     *  - [PREFERENCE] Prefers detailed explanations
     *  ==========================="
     *
     * The AI reads this and uses it to personalize
     * responses WITHOUT us explicitly saying anything.
     *
     * WHY Mono<String> not String:
     * We need to call the database (async).
     * Database calls return Mono/Flux.
     * We chain: DB call → collect list → format string
     * All non-blocking via Reactor operators.
     *
     * @param userId     whose memories to format
     * @param maxMemories how many to include
     */
    public Mono<String> formatForPrompt(
            UUID userId, int maxMemories) {
        // Guard: invalid limit returns empty string
        if (maxMemories <= 0) {
            return Mono.just("");
        }

        return getTop(userId, maxMemories)
                .collectList()
                // .map() here because formatting a List
                // is synchronous (no more DB calls)
                .map(memories -> {
                    // If no memories: return empty string
                    // PromptAssembler skips empty strings
                    if (memories.isEmpty()) {
                        return "";
                    }

                    // Build the formatted string
                    StringBuilder sb = new StringBuilder();
                    sb.append("=== WHAT I KNOW ABOUT YOU ===\n");
                    for (Memory memory : memories) {
                        sb.append("- [")
                                .append(memory.type().name())
                                .append("] ")
                                .append(memory.content())
                                .append("\n");
                    }
                    sb.append("============================");
                    return sb.toString();
                });
    }

    /**
     * Format using default limit.
     * Convenience method for PromptAssembler.
     *
     * @param userId whose memories to format
     */
    public Mono<String> formatForPrompt(UUID userId) {
        return formatForPrompt(userId, DEFAULT_PROMPT_LIMIT);
    }
}