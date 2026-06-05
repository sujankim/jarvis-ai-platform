package ai.jarvis.ai.orchestrator;

import ai.jarvis.ai.prompt.PromptAssembler;
import ai.jarvis.ai.prompt.WorkingMemoryBuilder;
import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.ProviderRouter;
import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.session.ChatSessionRepository;
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

    public Flux<String> chat(OrchestratorRequest request) {

        long startTime = System.currentTimeMillis();

        Message userMsg = Message.userMessage(
                UUID.randomUUID(),
                request.sessionId(),
                request.message()
        );

        return r2dbcEntityTemplate
                .insert(userMsg)
                // .then() closes immediately after loadHistory()
                // NOT wrapping the entire downstream chain
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

                    List<Message> historyWithoutCurrent =
                            history.subList(
                                    0,
                                    Math.max(0,
                                            history.size() - 1)
                            );

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

                                saveAssistantMessage(
                                        request.sessionId(),
                                        responseBuilder.toString(),
                                        tokenCount.get(),
                                        (int) duration,
                                        provider.getModelName()
                                ).subscribe();
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
                        sessionMemoryService
                                .onMessageSaved(request.sessionId())
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
                .flatMap(saved -> {
                    log.debug("Assistant message saved: {}",
                            saved.id());
                    return sessionRepository
                            .incrementMessageCount(
                                    sessionId, tokens)
                            .then();
                })
                .doOnError(error ->
                        log.error(
                                "Failed to save assistant message: {}",
                                error.getMessage(), error)
                )
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

    private record ProviderAndHistory(
            AiProvider provider,
            List<Message> history) {}
}