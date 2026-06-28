package ai.jarvis.agents;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for exposing agent steps via REST API.
 *
 * Does NOT expose input field to avoid
 * leaking potentially sensitive prompt content.
 * Only exposes what users need to see:
 * step type, tool used, output, timing.
 */
public record AgentStepResponse(

        UUID id,

        int stepIndex,

        AgentStepType stepType,

        // Which tool was called (ACT steps only)
        // null for THINK, OBSERVE, FINAL
        String toolName,

        // What this step produced:
        // THINK: AI reasoning
        // OBSERVE: tool result
        // FINAL: final answer
        // ACT: null (result in OBSERVE)
        String output,

        AgentStepStatus status,

        Integer durationMs,

        Instant createdAt,
        Instant completedAt

) {}