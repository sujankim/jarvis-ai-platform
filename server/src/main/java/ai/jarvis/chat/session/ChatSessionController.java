package ai.jarvis.chat.session;

import ai.jarvis.chat.message.MessageResponse;
import ai.jarvis.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Sessions",
        description = "Chat session management")
public class ChatSessionController {

    public final ChatSessionService sessionService;

    // ── GET /api/v1/sessions ──────────────────────

    @Operation(summary = "List all my sessions")
    @GetMapping
    public Mono<ApiResponse<List<ChatSessionResponse>>>
    listSessions() {

        return getUserId()
                .flatMap(userId ->
                        sessionService
                                .getUserSessions(userId)
                                .collectList()
                )
                .map(ApiResponse::ok);
    }

    // ── GET /api/v1/sessions/{id} ─────────────────

    @Operation(summary = "Get session details")
    @GetMapping("/{sessionId}")
    public Mono<ApiResponse<ChatSessionResponse>> getSession(
            @PathVariable UUID sessionId) {

        return getUserId()
                .flatMap(userId ->
                        sessionService
                                .getSession(
                                sessionId, userId))
                .map(ApiResponse::ok);
    }

    // ── GET /api/v1/sessions/{id}/messages ────────

    @Operation(summary = "Get session message history")
    @GetMapping("/{sessionId}/messages")
    public Mono<ApiResponse<List<MessageResponse>>>
    getMessages(@PathVariable UUID sessionId) {

        return getUserId()
                .flatMap(userId ->
                        sessionService
                                .getSessionMessages(
                                        sessionId, userId)
                                .collectList()
                )
                .map(ApiResponse::ok);
    }

    // ── DELETE /api/v1/sessions/{id} ─────────────

    @Operation(summary = "Archive (soft delete) a session")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> archiveSession(
            @PathVariable UUID sessionId) {

        return getUserId()
                .flatMap(userId ->
                        sessionService.archiveSession(
                                sessionId, userId));
    }

    // ── Helper ────────────────────────────────────

    private Mono<UUID> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString);
    }
}
