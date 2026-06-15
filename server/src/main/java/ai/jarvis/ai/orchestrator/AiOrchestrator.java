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
 * PHASE 2 ADDITION:
 * Now loads long-term memories before building prompt.
 * Memory loading runs IN PARALLEL with session history
 * loading for performance (no extra latency).
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
    private final MemoryService memoryService;           // NEW
    private final MemoryExtractionService
            memoryExtractionService;                      // EXISTING

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
                // Load session history AND memories IN PARALLEL
                // Mono.zip runs both simultaneously
                // Result: no extra latency for memory loading!
                .then(
                        Mono.zip(
                                // Left: session history
                                sessionMemoryService.loadHistory(
                                        request.sessionId()),
                                // Right: formatted memory context
                                // Empty string if no userId or no memories
                                loadMemoryContext(request.userId(), request.message())
                        )
                )
                .flatMap(tuple -> {
                    List<Message> history = tuple.getT1();
                    String memoryContext = tuple.getT2();

                    return providerRouter.route()
                            .map(provider ->
                                    new ProviderAndContext(
                                            provider,
                                            history,
                                            memoryContext));
                })
                .flatMapMany(pac -> {

                    AiProvider provider = pac.provider();
                    List<Message> history = pac.history();
                    String memoryContext =
                            pac.memoryContext();

                    String workingMemory =
                            workingMemoryBuilder.build(
                                    request.username(),
                                    request.role(),
                                    request.sessionId()
                                            .toString(),
                                    provider.getModelName()
                            );

                    // Filter out current user message
                    // from history (just inserted above)
                    List<Message> historyWithoutCurrent =
                            history.stream()
                                    .filter(msg -> !msg.id()
                                            .equals(userMsgId))
                                    .toList();

                    // Build prompt WITH memory context
                    Prompt prompt = promptAssembler.assemble(
                            request.message(),
                            workingMemory,
                            historyWithoutCurrent,
                            request.username(),
                            memoryContext  // ← NEW
                    );

                    StringBuilder responseBuilder =
                            new StringBuilder();
                    AtomicInteger tokenCount =
                            new AtomicInteger(0);

                    log.info(
                            "AI request: user={} session={} "
                                    + "provider={} model={} "
                                    + "history={} memories={}",
                            request.username(),
                            request.sessionId(),
                            provider.getName(),
                            provider.getModelName(),
                            historyWithoutCurrent.size(),
                            memoryContext.isBlank() ? 0 : 1
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
                                        "AI response: user={} "
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
                    // Cache refresh after exchange
                    sessionMemoryService
                            .onMessageSaved(
                                    request.sessionId())
                            .subscribe();

                    // Memory extraction (async)
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
     * Load formatted memory context for prompt.
     *
     * WHY Mono<String> not Flux<Memory>:
     * PromptAssembler needs a ready-to-inject String.
     * MemoryService.formatForPrompt() does this.
     *
     * WHY handle null userId:
     * Some code paths may not have userId yet.
     * Gracefully return empty string in that case.
     * Chat still works — just without memories.
     *
     * @param userId owner of the memories
     * @return formatted memory string or empty string
     */
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
            String memoryContext) {}
}