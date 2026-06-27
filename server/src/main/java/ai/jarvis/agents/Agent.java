package ai.jarvis.agents;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single agentic task execution.
 *
 * IMMUTABLE RECORD:
 * Use Agent.create() to make a new agent.
 * Use withXxx() to create updated copies.
 * Never mutate directly — same pattern as Memory.java.
 *
 * LIFECYCLE TRANSITIONS:
 * create() → PENDING
 * withRunning() → RUNNING
 * withCompleted() → COMPLETED + stores finalAnswer
 * withFailed() → FAILED + stores errorMessage
 * withCancelled() → CANCELLED
 */
@Table("agents")
public record Agent(

        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        @Column("session_id")
        UUID sessionId,

        // The user's original task/goal
        String goal,

        // Current execution status
        AgentStatus status,

        // Populated when agent completes
        @Column("final_answer")
        String finalAnswer,

        // Number of ReACT steps executed so far
        @Column("step_count")
        int stepCount,

        // Populated if agent fails
        @Column("error_message")
        String errorMessage,

        // Total execution time
        @Column("duration_ms")
        Integer durationMs,

        @Column("created_at")
        Instant createdAt,

        @Column("updated_at")
        Instant updatedAt,

        // Set when agent reaches terminal state
        @Column("completed_at")
        Instant completedAt

) {

    // ── Factory Methods ───────────────────────────

    /**
     * Create a new agent in PENDING status.
     *
     * Called when user submits a task.
     * Agent has not started executing yet.
     *
     * @param userId    owner of this agent
     * @param sessionId optional chat session link
     * @param goal      the user's task description
     */
    public static Agent create(
            UUID userId,
            UUID sessionId,
            String goal) {

        return new Agent(
                UUID.randomUUID(),
                userId,
                sessionId,
                goal,
                AgentStatus.PENDING,
                null,           // no answer yet
                0,              // no steps yet
                null,           // no error
                null,           // no duration yet
                Instant.now(),
                Instant.now(),
                null            // not completed
        );
    }

    // ── State Transition Methods ──────────────────

    /**
     * Transition from PENDING → RUNNING.
     * Called when execution actually begins.
     */
    public Agent withRunning() {
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.RUNNING,
                finalAnswer, stepCount,
                null, durationMs,
                createdAt, Instant.now(),
                null);
    }

    /**
     * Transition from RUNNING → COMPLETED.
     * Called when agent finishes successfully.
     *
     * @param answer        synthesized final answer
     * @param totalSteps    how many steps were taken
     * @param totalDurationMs total time in ms
     */
    public Agent withCompleted(
            String answer,
            int totalSteps,
            int totalDurationMs) {

        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.COMPLETED,
                answer,
                totalSteps,
                null,           // no error
                totalDurationMs,
                createdAt,
                Instant.now(),
                Instant.now()); // completedAt = now
    }

    /**
     * Transition from RUNNING → FAILED.
     * Called when the agent encounters an unrecoverable error.
     *
     * @param error description of what went wrong
     */
    public Agent withFailed(String error) {
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.FAILED,
                finalAnswer, stepCount,
                error, durationMs,
                createdAt,
                Instant.now(),
                Instant.now()); // completedAt = now
    }

    /**
     * Transition to CANCELLED.
     * Called when user explicitly cancels.
     */
    public Agent withCancelled() {
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.CANCELLED,
                finalAnswer, stepCount,
                null, durationMs,
                createdAt,
                Instant.now(),
                Instant.now());
    }

    /**
     * Create copy with step count incremented by 1.
     * Called after each ReACT step completes.
     */
    public Agent withIncrementedStepCount() {
        return new Agent(
                id, userId, sessionId, goal,
                status, finalAnswer,
                stepCount + 1,  // ← increment
                errorMessage, durationMs,
                createdAt,
                Instant.now(),
                completedAt);
    }
}