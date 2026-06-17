package ai.jarvis.ai.orchestrator;

import ai.jarvis.ai.prompt.PromptAssembler;
import ai.jarvis.ai.prompt.WorkingMemoryBuilder;
import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.ProviderRouter;
import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.session.ChatSessionRepository;
import ai.jarvis.memory.MemoryExtractionService;
import ai.jarvis.memory.MemoryService;
import ai.jarvis.memory.session.SessionMemoryService;
import ai.jarvis.rag.RagSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core AI orchestration service.
 *
 * PHASE 3 ADDITION:
 * Now loads RAG document context IN PARALLEL with
 * session history and long-term memories.
 * Zero extra latency — all 3 load simultaneously.
 *
 * CONTEXT LOADING (all parallel via Mono.zip):
 * 1. Session history  (Redis cache ~1ms)
 * 2. Memory context   (pgvector ~20ms)
 * 3. RAG context      (pgvector ~20ms) ← NEW Phase 3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrchestrator {

    private final ProviderRouter providerRouter;
    private final ChatSessionRepository sessionRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final PromptAssembler promptAssembler;
    private final WorkingMemoryBuilder workingMemoryBuilder;
    private final SessionMemoryService sessionMemoryService;
    private final MemoryService memoryService;
    private final MemoryExtractionService
            memoryExtractionService;
    private final RagSearchService ragSearchService; // ← NEW

    public Flux<String> chat(OrchestratorRequest request) {

        long startTime = System.currentTimeMillis();

        UUID userMsgId = UUID.randomUUID();
        Message userMsg = Message.userMessage(
                userMsgId,
                request.sessionId(),
                request.message()
        );

        return r2dbcEntityTemplate
                .insert(userMsg)
                // Load ALL context IN PARALLEL
                // Mono.zip runs all 3 simultaneously
                // Total time = slowest of 3, not sum!
                .then(
                        Mono.zip(
                                // 1. Session history (Redis)
                                sessionMemoryService
                                        .loadHistory(
                                                request.sessionId()),
                                // 2. Memory context (pgvector)
                                loadMemoryContext(
                                        request.userId(),
                                        request.message()),
                                // 3. RAG context (pgvector) ← NEW
                                loadRagContext(
                                        request.userId(),
                                        request.message())
                        )
                )
                .flatMap(tuple -> {
                    List<Message> history =
                            tuple.getT1();
                    String memoryContext =
                            tuple.getT2();
                    String ragContext =
                            tuple.getT3(); // ← NEW

                    return providerRouter.route()
                            .map(provider ->
                                    new ProviderAndContext(
                                            provider,
                                            history,
                                            memoryContext,
                                            ragContext)); // ← NEW
                })
                .flatMapMany(pac -> {

                    AiProvider provider =
                            pac.provider();
                    List<Message> history =
                            pac.history();
                    String memoryContext =
                            pac.memoryContext();
                    String ragContext =
                            pac.ragContext(); // ← NEW

                    String workingMemory =
                            workingMemoryBuilder.build(
                                    request.username(),
                                    request.role(),
                                    request.sessionId()
                                            .toString(),
                                    provider.getModelName()
                            );

                    List<Message> historyWithoutCurrent =
                            history.stream()
                                    .filter(msg -> !msg.id()
                                            .equals(userMsgId))
                                    .toList();

                    // Build prompt with ALL context
                    Prompt prompt = promptAssembler.assemble(
                            request.message(),
                            workingMemory,
                            historyWithoutCurrent,
                            request.username(),
                            memoryContext,
                            ragContext  // ← NEW Phase 3
                    );

                    StringBuilder responseBuilder =
                            new StringBuilder();
                    AtomicInteger tokenCount =
                            new AtomicInteger(0);

                    log.info(
                            "AI request: user={} "
                                    + "session={} "
                                    + "provider={} "
                                    + "model={} "
                                    + "history={} "
                                    + "memories={} "
                                    + "rag={}",
                            request.username(),
                            request.sessionId(),
                            provider.getName(),
                            provider.getModelName(),
                            historyWithoutCurrent.size(),
                            memoryContext.isBlank()
                                    ? 0 : 1,
                            ragContext.isBlank()
                                    ? 0 : 1  // ← NEW
                    );

                    return provider.streamChat(prompt)
                            .doOnNext(token -> {
                                responseBuilder
                                        .append(token);
                                tokenCount
                                        .incrementAndGet();
                            })
                            .doOnComplete(() -> {
                                long duration =
                                        System.currentTimeMillis()
                                                - startTime;

                                log.info(
                                        "AI response: "
                                                + "user={} "
                                                + "tokens≈{} "
                                                + "duration={}ms "
                                                + "provider={}",
                                        request.username(),
                                        tokenCount.get(),
                                        duration,
                                        provider.getName()
                                );

                                saveAssistantMessage(
                                        request.sessionId(),
                                        responseBuilder
                                                .toString(),
                                        tokenCount.get(),
                                        (int) duration,
                                        provider.getModelName()
                                )
                                        .doOnError(e ->
                                                log.error(
                                                        "Save failed: {}",
                                                        e.getMessage()))
                                        .subscribe();
                            })
                            .doOnError(error -> {
                                log.error(
                                        "AI error: user={} "
                                                + "provider={} "
                                                + "error={}",
                                        request.username(),
                                        provider.getName(),
                                        error.getMessage()
                                );
                                saveErrorMessage(
                                        request.sessionId(),
                                        error.getMessage()
                                ).subscribe();
                            });
                })
                .doFinally(signal -> {
                    sessionMemoryService
                            .onMessageSaved(
                                    request.sessionId())
                            .subscribe();

                    if (request.userId() != null) {
                        memoryExtractionService
                                .extractAndSave(
                                        request.userId(),
                                        request.sessionId(),
                                        request.message()
                                )
                                .subscribe(
                                        null,
                                        error -> log.debug(
                                                "Extraction skipped: {}",
                                                error.getMessage())
                                );
                    }
                });
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Load RAG context for prompt injection.
     *
     * WHY Mono<String> not Flux<RagSearchResult>:
     * PromptAssembler needs a ready-to-inject String.
     * RagSearchService.formatForPrompt() handles this.
     *
     * WHY handle null userId:
     * Same as memory — some paths may not have userId.
     * Return empty string gracefully.
     *
     * @param userId    owner of the documents
     * @param userQuery current user message
     * @return formatted RAG string or empty string
     */
    private Mono<String> loadRagContext(
            UUID userId,
            String userQuery) {

        if (userId == null) {
            return Mono.just("");
        }

        return ragSearchService
                .formatForPrompt(userId, userQuery)
                .onErrorReturn("")
                .defaultIfEmpty("");
    }

    private Mono<String> loadMemoryContext(
            UUID userId,
            String userQuery) {

        if (userId == null) {
            return Mono.just("");
        }

        return memoryService
                .formatForPrompt(userId, userQuery)
                .onErrorReturn("")
                .defaultIfEmpty("");
    }

    private Mono<Void> saveAssistantMessage(
            UUID sessionId,
            String content,
            int tokens,
            int durationMs,
            String modelName) {

        Message assistantMsg = Message.assistantMessage(
                UUID.randomUUID(),
                sessionId,
                content,
                modelName,
                null,
                tokens,
                durationMs
        );

        return r2dbcEntityTemplate
                .insert(assistantMsg)
                .doOnSuccess(saved ->
                        log.debug("Assistant saved: {}",
                                saved.id()))
                .flatMap(saved ->
                        sessionRepository
                                .incrementMessageCount(
                                        sessionId, tokens)
                                .doOnError(error ->
                                        log.error(
                                                "incrementMessageCount "
                                                        + "failed: {}",
                                                error.getMessage()))
                )
                .doOnError(error ->
                        log.error(
                                "saveAssistantMessage "
                                        + "failed: {}",
                                error.getMessage()))
                .then();
    }

    private Mono<Void> saveErrorMessage(
            UUID sessionId, String errorText) {
        Message errorMsg = Message.errorMessage(
                UUID.randomUUID(),
                sessionId,
                errorText
        );
        return r2dbcEntityTemplate
                .insert(errorMsg)
                .then();
    }

    // ── Private Records ───────────────────────────

    private record ProviderAndContext(
            AiProvider provider,
            List<Message> history,
            String memoryContext,
            String ragContext) {} // ← added ragContext
}