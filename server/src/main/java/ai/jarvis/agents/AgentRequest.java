package ai.jarvis.agents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for starting an agent task.
 *
 * POST /api/v1/agents
 * POST /api/v1/agents/stream
 *
 * goal is the user's natural language task description.
 * sessionId optionally links the agent to a chat session
 * so the AI has access to conversation context.
 */
public record AgentRequest(

        @NotBlank(message = "Goal cannot be empty")
        @Size(max = 2000,
                message = "Goal too long (max 2000 chars)")
        String goal,

        // Optional: link to existing chat session
        // null = standalone agent task
        UUID sessionId

) {}