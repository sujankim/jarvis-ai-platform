package ai.jarvis.ai.orchestrator;

import ai.jarvis.ai.prompt.PromptAssembler;
import ai.jarvis.ai.prompt.WorkingMemoryBuilder;
import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.message.MessageRepository;
import ai.jarvis.chat.session.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    private final ChatClient.Builder chatClientBuilder;
    private final MessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final PromptAssembler promptAssembler;
    private final WorkingMemoryBuilder workingMemoryBuilder;

    /**
     * The main chat method.
     * Orchestrates: save → load context → assemble prompt
     * → stream AI → save response
     *
     * Returns Flux<String> of tokens for SSE streaming.
     */
    public Flux<String> chat(OrchestratorRequest request) {

        long startTime = System.currentTimeMillis();

        // Build ChatClient for this request
        ChatClient chatClient = chatClientBuilder.build();

        // Step 1: Save user message to DB immediately
        // (even if AI fails, user message is preserved)
        Message userMsg = Message.userMessage(
                UUID.randomUUID(),
                request.sessionId(),
                request.message()
        );

        // Step 2: Load conversation history
        // Step 3: Assemble prompt
        // Step 4: Stream AI response
        // Step 5: Save assistant message after stream completes

        return r2dbcEntityTemplate
                .insert(userMsg)
                .then(messageRepository
                        .findBySessionIdOrderByCreatedAtAsc(
                                request.sessionId())
                        .collectList()
                )
                .flatMapMany(history -> {

                    // Build working memory string
                    String workingMemory =
                            workingMemoryBuilder.build(
                                    request.username(),
                                    request.role(),
                                    request.sessionId().toString(),
                                    "llama3.1:8b"
                            );

                    // Assemble the full prompt
                    // Exclude the last message from history
                    // (it's the one we just saved = current msg)
                    List<Message> historyWithoutCurrent =
                            history.subList(
                                    0,
                                    Math.max(0, history.size() - 1)
                            );

                    Prompt prompt = promptAssembler.assemble(
                            request.message(),
                            workingMemory,
                            historyWithoutCurrent,
                            request.username()
                    );

                    // Accumulate response for saving
                    StringBuilder responseBuilder =
                            new StringBuilder();
                    AtomicInteger tokenCount =
                            new AtomicInteger(0);

                    log.info(
                            "AI request: user={} session={} "
                                    + "historyMessages={}",
                            request.username(),
                            request.sessionId(),
                            historyWithoutCurrent.size()
                    );

                    // Stream from Ollama
                    return chatClient
                            .prompt(prompt)
                            .stream()
                            .content()
                            .filter(token -> token != null
                                    && !token.isEmpty())
                            .doOnNext(token -> {
                                responseBuilder.append(token);
                                tokenCount.incrementAndGet();
                            })
                            .doOnComplete(() -> {
                                long duration =
                                        System.currentTimeMillis()
                                                - startTime;

                                String fullResponse =
                                        responseBuilder.toString();

                                log.info(
                                        "AI response: user={} "
                                                + "tokens≈{} duration={}ms",
                                        request.username(),
                                        tokenCount.get(),
                                        duration
                                );

                                // Save assistant message
                                // and update session stats
                                // (async — doesn't block stream)
                                saveAssistantMessage(
                                        request.sessionId(),
                                        fullResponse,
                                        tokenCount.get(),
                                        (int) duration
                                ).subscribe();
                            })
                            .doOnError(error -> {
                                log.error(
                                        "AI error: user={} error={}",
                                        request.username(),
                                        error.getMessage()
                                );
                                // Save error message to DB
                                saveErrorMessage(
                                        request.sessionId(),
                                        error.getMessage()
                                ).subscribe();
                            });
                });
    }

    // ── Private Helpers ───────────────────────────

    private Mono<Void> saveAssistantMessage(
            UUID sessionId,
            String content,
            int tokens,
            int durationMs) {

        Message assistantMsg = Message.assistantMessage(
                UUID.randomUUID(),
                sessionId,
                content,
                "llama3.1:8b",
                null,
                tokens,
                durationMs
        );

        return r2dbcEntityTemplate
                .insert(assistantMsg)
                .then(sessionRepository
                        .incrementMessageCount(
                                sessionId, tokens))
                .then();
    }

    private Mono<Void> saveErrorMessage(
            UUID sessionId,
            String errorText) {

        Message errorMsg = Message.errorMessage(
                UUID.randomUUID(),
                sessionId,
                errorText
        );

        return r2dbcEntityTemplate
                .insert(errorMsg)
                .then();
    }
}