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
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Chat",
        description = "AI chat streaming endpoints")
public class ChatStreamController {

    private final AiOrchestrator orchestrator;
    private final ChatSessionRepository sessionRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Operation(
            summary = "Stream chat response (SSE)",
            description = "Send a message and receive "
                    + "AI response as Server-Sent Events."
    )
    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody ChatRequest request) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMapMany(auth -> {

                    // Extract BOTH userId and username
                    // from the security context
                    String userId = auth.getPrincipal()
                            .toString();
                    String username = extractUsername(auth);

                    return resolveSession(
                            request.sessionId(),
                            UUID.fromString(userId),
                            request.message()
                    )
                            .flatMapMany(session -> {

                                log.info(
                                        "Chat: user={} sessionId={}",
                                        username, session.id()
                                );

                                OrchestratorRequest orchRequest =
                                        OrchestratorRequest.of(
                                                session.id(),
                                                request.message(),
                                                username, // ← REAL username
                                                extractRole(auth)
                                        );

                                return orchestrator
                                        .chat(orchRequest)
                                        .map(token ->
                                                ServerSentEvent
                                                        .<String>builder()
                                                        .event("token")
                                                        // JSON encode to
                                                        // preserve spaces!
                                                        .data(
                                                                jsonToken(token)
                                                        )
                                                        .build()
                                        )
                                        .concatWith(Flux.just(
                                                ServerSentEvent
                                                        .<String>builder()
                                                        .event("done")
                                                        .data("[DONE]")
                                                        .build()
                                        ));
                            });
                });
    }

    /**
     * Wrap token in JSON to preserve spaces.
     *
     * Problem: SSE codec strips leading whitespace.
     * " nice" → becomes "nice" after SSE parsing.
     *
     * Solution: {"t":" nice"} → JSON string preserves
     * the space inside the JSON value.
     */
    private String jsonToken(String token) {
        // Simple JSON encoding - escape special chars
        return "{\"t\":\""
                + token
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"}";
    }

    private String extractUsername(Authentication auth) {
        // Details holds username (set in JWT filter)
        Object details = auth.getDetails();
        if (details instanceof String s) {
            return s;
        }
        // Fallback to principal if details not set
        return auth.getPrincipal().toString();
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .findFirst()
                .map(a -> a.getAuthority()
                        .replace("ROLE_", ""))
                .orElse("USER");
    }

    private Mono<ChatSession> resolveSession(
            UUID sessionId,
            UUID userId,
            String firstMessage) {

        if (sessionId != null) {
            return sessionRepository
                    .findByIdAndUserId(sessionId, userId)
                    .switchIfEmpty(Mono.error(
                            new RuntimeException(
                                    "Session not found"
                            )
                    ));
        }

        ChatSession newSession = ChatSession.create(
                UUID.randomUUID(), userId);

        String title = firstMessage.length() > 50
                ? firstMessage.substring(0, 47) + "..."
                : firstMessage;

        return r2dbcEntityTemplate
                .insert(newSession)
                .flatMap(saved ->
                        sessionRepository
                                .setTitleIfNull(
                                        saved.id(), title)
                                .thenReturn(saved)
                );
    }
}