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
 * Never mutate directly.
 *
 * LIFECYCLE TRANSITIONS:
 * create()        → PENDING
 * withRunning()   → RUNNING   (requires PENDING)
 * withCompleted() → COMPLETED (requires RUNNING)
 * withFailed()    → FAILED    (requires RUNNING)
 * withCancelled() → CANCELLED (any non-terminal)
 *
 * DB CONSTRAINTS (V15 migration):
 * final_answer IS NULL OR status = 'COMPLETED'
 * error_message IS NULL OR status = 'FAILED'
 *
 * withFailed() and withCancelled() must explicitly
 * set finalAnswer = null to satisfy the DB constraint.
 * Carrying through a non-null finalAnswer from a prior
 * state would cause the UPDATE to be rejected.
 */
@Table("agents")
public record Agent(

        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        @Column("session_id")
        UUID sessionId,

        String goal,

        AgentStatus status,

        @Column("final_answer")
        String finalAnswer,

        @Column("step_count")
        int stepCount,

        @Column("error_message")
        String errorMessage,

        @Column("duration_ms")
        Integer durationMs,

        @Column("created_at")
        Instant createdAt,

        @Column("updated_at")
        Instant updatedAt,

        @Column("completed_at")
        Instant completedAt

) {

    // ── Factory Method ────────────────────────────

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
                null, 0, null, null,
                Instant.now(),
                Instant.now(),
                null);
    }

    // ── State Guards ──────────────────────────────

    /**
     * Check if the agent is in a terminal state.
     * Terminal agents cannot transition to any other state.
     */
    private boolean isTerminal() {
        return status == AgentStatus.COMPLETED
                || status == AgentStatus.FAILED
                || status == AgentStatus.CANCELLED;
    }

    /**
     * Enforce expected current status.
     * Prevents impossible transitions like
     * PENDING → COMPLETED or RUNNING → RUNNING.
     *
     * @param expected required current status
     * @throws IllegalStateException if current status wrong
     */
    private void requireStatus(AgentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Invalid transition from "
                            + status
                            + "; expected "
                            + expected);
        }
    }

    // ── State Transition Methods ──────────────────

    /**
     * PENDING → RUNNING.
     * requireStatus(PENDING) prevents invalid
     * transitions from non-PENDING states.
     */
    public Agent withRunning() {
        requireStatus(AgentStatus.PENDING);
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.RUNNING,
                finalAnswer, stepCount,
                null, durationMs,
                createdAt, Instant.now(),
                null);
    }

    /**
     * RUNNING → COMPLETED.
     * requireStatus(RUNNING) prevents jumping
     * to COMPLETED from PENDING.
     */
    public Agent withCompleted(
            String answer,
            int totalSteps,
            int totalDurationMs) {
        requireStatus(AgentStatus.RUNNING);
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.COMPLETED,
                answer, totalSteps,
                null,
                totalDurationMs,
                createdAt, Instant.now(),
                Instant.now());
    }

    /**
     * RUNNING → FAILED.
     * requireStatus(RUNNING) prevents failing
     * a PENDING or already terminal agent.
     *
     * finalAnswer explicitly set to null.
     * DB constraint: final_answer IS NULL OR status = 'COMPLETED'.
     * Carrying through a non-null finalAnswer from a prior
     * state would cause the DB UPDATE to be rejected.
     */
    public Agent withFailed(String error) {
        requireStatus(AgentStatus.RUNNING);
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.FAILED,
                null,
                stepCount,
                error, durationMs,
                createdAt, Instant.now(),
                Instant.now());
    }

    /**
     * Any non-terminal → CANCELLED.
     * isTerminal() check prevents cancelling
     * an already completed agent.
     *
     * finalAnswer explicitly set to null.
     * DB constraint: final_answer IS NULL OR status = 'COMPLETED'.
     * A CANCELLED agent never has a final answer.
     *
     * errorMessage explicitly set to null.
     * DB constraint: error_message IS NULL OR status = 'FAILED'.
     * A CANCELLED agent has no error message.
     */
    public Agent withCancelled() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "Agent is already terminal: "
                            + status);
        }
        return new Agent(
                id, userId, sessionId, goal,
                AgentStatus.CANCELLED,
                null,
                stepCount,
                null,
                durationMs,
                createdAt, Instant.now(),
                Instant.now());
    }

    /**
     * Increment step count.
     * isTerminal() prevents incrementing
     * a completed/failed/cancelled agent.
     */
    public Agent withIncrementedStepCount() {
        if (isTerminal()) {
            throw new IllegalStateException(
                    "Cannot increment a terminal agent");
        }
        return new Agent(
                id, userId, sessionId, goal,
                status, finalAnswer,
                stepCount + 1,
                errorMessage, durationMs,
                createdAt, Instant.now(),
                completedAt);
    }
}