package ai.jarvis.agents;

import ai.jarvis.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for the Agent System.
 *
 * ENDPOINTS:
 *
 * POST /api/v1/agents/stream
 *   Start agent + stream events as SSE.
 *   Client sees real-time THINK/ACT/OBSERVE/FINAL.
 *
 * POST /api/v1/agents
 *   Start agent without streaming.
 *   Returns agent ID immediately.
 *   Client polls GET /api/v1/agents/{id} for status.
 *
 * GET /api/v1/agents
 *   List all agents for authenticated user.
 *
 * GET /api/v1/agents/{id}
 *   Get agent status + all steps.
 *
 * GET /api/v1/agents/{id}/steps
 *   Get just the steps for an agent.
 *
 * DELETE /api/v1/agents/{id}
 *   Cancel a running agent.
 *
 * ALL endpoints require JWT authentication.
 * Ownership verified before any data access.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Agents",
        description = "AI Agent System endpoints")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentMapper agentMapper;

    // ── POST /api/v1/agents/stream ────────────────

    @Operation(
            summary = "Start agent with SSE streaming",
            description =
                    "Start an agent task and receive "
                            + "real-time progress as "
                            + "Server-Sent Events. "
                            + "Each ReACT step emitted "
                            + "as: think/act/observe/final."
    )
    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> stream(
            @Valid @RequestBody
            AgentRequest request) {

        return getUserId()
                .flatMapMany(userId -> {

                    log.info(
                            "Agent stream started: "
                                    + "user={} goal_chars={}",
                            userId,
                            request.goal().length());

                    return orchestrator
                            .startAgent(
                                    request.goal(),
                                    userId,
                                    request.sessionId())
                            .map(event ->
                                    ServerSentEvent
                                            .<String>builder()
                                            // Event type = think/act/observe/final/error
                                            .event(event.type()
                                                    .name()
                                                    .toLowerCase())
                                            // Data = JSON with step info
                                            .data(buildEventData(event))
                                            .build())
                            .concatWith(Flux.just(
                                    ServerSentEvent
                                            .<String>builder()
                                            .event("done")
                                            .data("[DONE]")
                                            .build()));
                });
    }

    // ── POST /api/v1/agents ───────────────────────

    @Operation(
            summary = "Start agent (no streaming)",
            description =
                    "Start an agent task and receive "
                            + "agent ID immediately. "
                            + "Poll GET /agents/{id} "
                            + "for status updates."
    )
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<ApiResponse<AgentResponse>>
    startAgent(
            @Valid @RequestBody
            AgentRequest request) {

        return getUserId()
                .flatMap(userId -> {

                    log.info(
                            "Agent started (async): "
                                    + "user={} "
                                    + "goal_chars={}",
                            userId,
                            request.goal().length());

                    // Start agent — subscribe but
                    // don't wait for completion
                    orchestrator
                            .startAgent(
                                    request.goal(),
                                    userId,
                                    request.sessionId())
                            .subscribe(
                                    null,
                                    error -> log.error(
                                            "Async agent error: {}",
                                            error.getMessage()));

                    // Return accepted immediately
                    // with placeholder response
                    Agent pending = Agent.create(
                            userId,
                            request.sessionId(),
                            request.goal());

                    return Mono.just(ApiResponse.ok(
                            agentMapper.toResponse(
                                    pending),
                            "Agent started. "
                                    + "Poll GET /agents/{id} "
                                    + "for status."));
                });
    }

    // ── GET /api/v1/agents ────────────────────────

    @Operation(
            summary = "List all my agents",
            description =
                    "Returns all agents for the "
                            + "authenticated user, "
                            + "newest first. "
                            + "Steps not included — "
                            + "use GET /agents/{id}."
    )
    @GetMapping(
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<List<AgentResponse>>>
    listAgents() {

        return getUserId()
                .flatMap(userId ->
                        orchestrator
                                .getUserAgents(userId)
                                .map(agentMapper::toResponse)
                                .collectList())
                .map(ApiResponse::ok);
    }

    // ── GET /api/v1/agents/{id} ───────────────────

    @Operation(
            summary = "Get agent status and steps",
            description =
                    "Returns agent details including "
                            + "all executed steps "
                            + "in order."
    )
    @GetMapping(
            value = "/{agentId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<AgentResponse>>
    getAgent(@PathVariable UUID agentId) {

        return getUserId()
                .flatMap(userId ->
                        orchestrator
                                .getAgent(agentId, userId))
                .map(agentWithSteps -> {

                    Agent agent =
                            agentWithSteps.agent();

                    List<AgentStepResponse> stepResponses =
                            agentWithSteps.steps()
                                    .stream()
                                    .map(agentMapper
                                            ::toStepResponse)
                                    .toList();

                    // Build response with steps
                    AgentResponse response =
                            new AgentResponse(
                                    agent.id(),
                                    agent.goal(),
                                    agent.status(),
                                    agent.finalAnswer(),
                                    agent.stepCount(),
                                    agent.errorMessage(),
                                    agent.durationMs(),
                                    agent.createdAt(),
                                    agent.updatedAt(),
                                    agent.completedAt(),
                                    stepResponses);

                    return ApiResponse.ok(response);
                });
    }

    // ── GET /api/v1/agents/{id}/steps ─────────────

    @Operation(
            summary = "Get agent steps only",
            description =
                    "Returns just the execution "
                            + "steps for an agent "
                            + "in order."
    )
    @GetMapping(
            value = "/{agentId}/steps",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<List<AgentStepResponse>>>
    getSteps(@PathVariable UUID agentId) {

        return getUserId()
                .flatMap(userId ->
                        orchestrator
                                .getAgent(agentId, userId))
                .map(agentWithSteps ->
                        agentWithSteps.steps()
                                .stream()
                                .map(agentMapper
                                        ::toStepResponse)
                                .toList())
                .map(ApiResponse::ok);
    }

    // ── DELETE /api/v1/agents/{id} ────────────────

    @Operation(
            summary = "Cancel a running agent",
            description =
                    "Cancels a PENDING or RUNNING agent. "
                            + "Cannot cancel COMPLETED, "
                            + "FAILED, or CANCELLED agents."
    )
    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> cancelAgent(
            @PathVariable UUID agentId) {

        return getUserId()
                .flatMap(userId ->
                        orchestrator.cancelAgent(
                                agentId, userId));
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Extract userId UUID from JWT principal.
     * Same pattern as MemoryController + VoiceController.
     * onErrorMap converts malformed UUID → 401.
     */
    private Mono<UUID> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString)
                .onErrorMap(
                        IllegalArgumentException.class,
                        ex -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid token subject"));
    }

    /**
     * Build SSE event data string from AgentEvent.
     *
     * Format: JSON with step, data, and tool fields.
     * Simple manual JSON — no Jackson needed for this.
     *
     * @param event the agent event to serialize
     * @return JSON string for SSE data field
     */
    private String buildEventData(AgentEvent event) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"step\":").append(event.stepIndex());
        json.append(",\"data\":\"")
                .append(escapeJson(event.data()))
                .append("\"");

        if (event.toolName() != null) {
            json.append(",\"tool\":\"")
                    .append(event.toolName())
                    .append("\"");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Escape special characters for JSON string values.
     * Prevents malformed JSON when data contains quotes.
     *
     * @param text raw text to escape
     * @return JSON-safe escaped string
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}