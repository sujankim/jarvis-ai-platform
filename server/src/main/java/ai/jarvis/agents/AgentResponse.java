package ai.jarvis.agents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for exposing agent data via REST API.
 *
 * Two variants:
 * - Without steps: used for list endpoint
 * - With steps: used for single agent detail
 *
 * steps is null when not requested (list view)
 * to avoid loading all steps for every agent in a list.
 */
public record AgentResponse(

        UUID id,

        String goal,

        AgentStatus status,

        // The synthesized answer
        // null while PENDING or RUNNING
        String finalAnswer,

        int stepCount,

        // Error message if FAILED
        String errorMessage,

        Integer durationMs,

        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,

        // Only populated for single agent detail
        // null for list endpoint (performance)
        List<AgentStepResponse> steps

) {}