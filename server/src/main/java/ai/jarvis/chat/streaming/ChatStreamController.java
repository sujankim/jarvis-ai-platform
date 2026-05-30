package ai.jarvis.chat.streaming;

import ai.jarvis.ai.orchestrator.AiOrchestrator;
import ai.jarvis.ai.orchestrator.OrchestratorRequest;
import ai.jarvis.chat.session.ChatSession;
import ai.jarvis.chat.session.ChatSessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@SecurityRequirement(name = "Bearer Auth")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat",
        description = "AI chat streaming endpoints")
public class ChatStreamController {

    private final AiOrchestrator orchestrator;
    private final ChatSessionRepository sessionRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Operation(
            summary = "Stream chat response (SSE)",
            description = "Send a message and receive "
                    + "AI response as Server-Sent Events. "
                    + "Each token arrives as a separate event.",
            security = @SecurityRequirement(name = "Bearer Auth")
    )
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody ChatRequest request) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(String.class) // principal = userId
                .flatMapMany(userId ->
                        resolveSession(
                                request.sessionId(),
                                UUID.fromString(userId),
                                request.message()
                        )
                                .flatMapMany(session -> {

                                    log.info(
                                            "Chat: userId={} sessionId={}",
                                            userId,
                                            session.id()
                                    );

                                    // Extract username from
                                    // security context (stored as claims)
                                    OrchestratorRequest orchRequest =
                                            OrchestratorRequest.of(
                                                    session.id(),
                                                    request.message(),
                                                    userId, // use userId for now
                                                    "USER"
                                            );

                                    // Stream tokens as SSE events
                                    return orchestrator.chat(orchRequest)
                                            .map(token ->
                                                    ServerSentEvent
                                                            .<String>builder()
                                                            .event("token")
                                                            .data(token)
                                                            .build()
                                            )
                                            .concatWith(
                                                    // Send 'done' event
                                                    // when stream completes
                                                    Flux.just(
                                                            ServerSentEvent
                                                                    .<String>builder()
                                                                    .event("done")
                                                                    .data("[DONE]")
                                                                    .build()
                                                    )
                                            );
                                })
                );
    }

    /**
     * Resolves or creates a chat session.
     * If sessionId provided: load and verify ownership.
     * If sessionId null: create new session.
     */
    private Mono<ChatSession> resolveSession(
            UUID sessionId,
            UUID userId,
            String firstMessage) {

        if (sessionId != null) {
            // Load existing session
            return sessionRepository
                    .findByIdAndUserId(sessionId, userId)
                    .switchIfEmpty(Mono.error(
                            new RuntimeException(
                                    "Session not found or "
                                            + "not owned by user"
                            )
                    ));
        }

        // Create new session
        ChatSession newSession = ChatSession.create(
                UUID.randomUUID(), userId);

        // Generate title from first 50 chars of message
        String title = firstMessage.length() > 50
                ? firstMessage.substring(0, 47) + "..."
                : firstMessage;

        return r2dbcEntityTemplate
                .insert(newSession)
                .flatMap(saved ->
                        sessionRepository
                                .setTitleIfNull(saved.id(), title)
                                .thenReturn(saved)
                );
    }
}
