package ai.jarvis.ai.orchestrator;

import ai.jarvis.ai.prompt.PromptAssembler;
import ai.jarvis.ai.prompt.WorkingMemoryBuilder;
import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.ProviderRouter;
import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.session.ChatSessionRepository;
import ai.jarvis.memory.MemoryExtractionService;
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
    private final MemoryExtractionService memoryExtractionService;

    public Flux<String> chat(OrchestratorRequest request) {

        long startTime = System.currentTimeMillis();

        // Generate ID first so we can exclude it
        // from history when building the prompt
        UUID userMsgId = UUID.randomUUID();

        Message userMsg = Message.userMessage(
                userMsgId,
                request.sessionId(),
                request.message()
        );

        return r2dbcEntityTemplate
                .insert(userMsg)
                // Load history AFTER insert
                // SessionMemoryService: Redis first, DB fallback
                .then(sessionMemoryService.loadHistory(
                        request.sessionId()))
                .flatMap(history ->
                        providerRouter.route()
                                .map(provider ->
                                        new ProviderAndHistory(
                                                provider, history))
                )
                .flatMapMany(pah -> {

                    AiProvider provider = pah.provider();
                    List<Message> history = pah.history();

                    String workingMemory =
                            workingMemoryBuilder.build(
                                    request.username(),
                                    request.role(),
                                    request.sessionId()
                                            .toString(),
                                    provider.getModelName()
                            );

                    // filter by ID instead of tail trim.
                    // Tail trim breaks on Redis cache HIT because
                    // the cached list may not include the new user
                    // message yet. Filtering by ID works correctly
                    // for both cache HIT and cache MISS.
                    List<Message> historyWithoutCurrent =
                            history.stream()
                                    .filter(msg -> !msg.id()
                                            .equals(userMsgId))
                                    .toList();

                    Prompt prompt = promptAssembler.assemble(
                            request.message(),
                            workingMemory,
                            historyWithoutCurrent,
                            request.username()
                    );

                    StringBuilder responseBuilder =
                            new StringBuilder();
                    AtomicInteger tokenCount =
                            new AtomicInteger(0);

                    log.info(
                            "AI request: user={} session={} "
                                    + "provider={} model={} history={}",
                            request.username(),
                            request.sessionId(),
                            provider.getName(),
                            provider.getModelName(),
                            historyWithoutCurrent.size()
                    );

                    return provider.streamChat(prompt)
                            .doOnNext(token -> {
                                responseBuilder.append(token);
                                tokenCount.incrementAndGet();
                            })
                            .doOnComplete(() -> {
                                long duration =
                                        System.currentTimeMillis()
                                                - startTime;

                                log.info(
                                        "AI response: user={} "
                                                + "tokens≈{} duration={}ms "
                                                + "provider={}",
                                        request.username(),
                                        tokenCount.get(),
                                        duration,
                                        provider.getName()
                                );

                                // Save async — does not block stream
                                saveAssistantMessage(
                                        request.sessionId(),
                                        responseBuilder.toString(),
                                        tokenCount.get(),
                                        (int) duration,
                                        provider.getModelName()
                                )
                                        .doOnError(e ->
                                                log.error(
                                                        "Save assistant failed: {}",
                                                        e.getMessage()))
                                        .subscribe();
                                // ========== Extract memories ASYNC ==========
                                // Fire‑and‑forget; never blocks chat streaming.
                                // If extraction fails, only a debug log is emitted.
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
                            })
                            .doOnError(error -> {
                                log.error(
                                        "AI error: user={} "
                                                + "provider={} error={}",
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
                .doFinally(signal ->
                        // Refresh cache after exchange completes
                        // so next request gets updated history
                        sessionMemoryService
                                .onMessageSaved(request.sessionId())
                                .doOnError(e ->
                                        log.warn(
                                                "Cache refresh failed: {}",
                                                e.getMessage()))
                                .subscribe()
                );
    }

    // ── Private helpers ───────────────────────────

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
                                .doOnSuccess(rows ->
                                        log.debug(
                                                "Session updated: rows={}",
                                                rows))
                                .doOnError(error ->
                                        log.error(
                                                "incrementMessageCount "
                                                        + "failed [{}]: {}",
                                                sessionId,
                                                error.getMessage()))
                )
                .doOnError(error ->
                        log.error(
                                "saveAssistantMessage "
                                        + "failed [{}]: {}",
                                sessionId,
                                error.getMessage(), error))
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
                .doOnError(e ->
                        log.error(
                                "saveErrorMessage failed: {}",
                                e.getMessage()))
                .then();
    }

    // ── Private record ────────────────────────────

    private record ProviderAndHistory(
            AiProvider provider,
            List<Message> history) {}
}