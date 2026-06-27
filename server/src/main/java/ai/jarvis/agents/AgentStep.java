package ai.jarvis.agents;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single step in a ReACT agent execution.
 *
 * Steps form the THINK → ACT → OBSERVE loop.
 * Each step is persisted immediately for:
 * - Real-time progress streaming to client
 * - Debugging failed agents
 * - User transparency (see what agent did)
 *
 * IMMUTABLE RECORD:
 * Use createXxx() factory methods.
 * Use withXxx() for state updates.
 *
 * WHY NOT store in-memory only:
 * Agents can take 30-120 seconds.
 * If server restarts, steps would be lost.
 * DB persistence allows inspection + resume.
 */
@Table("agent_steps")
public record AgentStep(

        @Id
        UUID id,

        @Column("agent_id")
        UUID agentId,

        @Column("user_id")
        UUID userId,

        @Column("step_index")
        int stepIndex,

        @Column("step_type")
        AgentStepType stepType,

        @Column("tool_name")
        String toolName,

        String input,

        String output,

        AgentStepStatus status,

        @Column("duration_ms")
        Integer durationMs,

        @Column("created_at")
        Instant createdAt,

        @Column("completed_at")
        Instant completedAt

) {

    // ── Factory Methods ───────────────────────────

    /**
     * Create a THINK step.
     * AI is reasoning about what to do next.
     *
     * @param agentId parent agent
     * @param userId  owner
     * @param index   step position (0-based)
     * @param input   the reasoning prompt
     */
    public static AgentStep createThink(
            UUID agentId, UUID userId,
            int index, String input) {
        return new AgentStep(
                UUID.randomUUID(),
                agentId, userId, index,
                AgentStepType.THINK,
                null, input, null,
                AgentStepStatus.PENDING,
                null,
                Instant.now(), null);
    }

    /**
     * Create an ACT step.
     * AI is calling a specific tool.
     *
     * @param agentId  parent agent
     * @param userId   owner
     * @param index    step position
     * @param toolName which @Tool method to call
     * @param input    tool input parameters
     */
    public static AgentStep createAct(
            UUID agentId, UUID userId,
            int index, String toolName,
            String input) {
        return new AgentStep(
                UUID.randomUUID(),
                agentId, userId, index,
                AgentStepType.ACT,
                toolName, input, null,
                AgentStepStatus.PENDING,
                null,
                Instant.now(), null);
    }

    /**
     * Create an OBSERVE step.
     * Recording the result from a tool call.
     * OBSERVE is immediately DONE when created.
     *
     * @param agentId  parent agent
     * @param userId   owner
     * @param index    step position
     * @param toolName which tool produced this
     * @param output   the tool's result
     */
    public static AgentStep createObserve(
            UUID agentId, UUID userId,
            int index, String toolName,
            String output) {
        // OBSERVE is immediately DONE — no RUNNING phase
        return new AgentStep(
                UUID.randomUUID(),
                agentId, userId, index,
                AgentStepType.OBSERVE,
                toolName, null, output,
                AgentStepStatus.DONE,
                null,
                Instant.now(), Instant.now());
    }

    /**
     * Create a FINAL step.
     * Agent has synthesized the final answer.
     * FINAL is immediately DONE when created.
     *
     * @param agentId parent agent
     * @param userId  owner
     * @param index   step position
     * @param answer  the final answer text
     */
    public static AgentStep createFinal(
            UUID agentId, UUID userId,
            int index, String answer) {
        // FINAL is immediately DONE — no RUNNING phase
        return new AgentStep(
                UUID.randomUUID(),
                agentId, userId, index,
                AgentStepType.FINAL,
                null, null, answer,
                AgentStepStatus.DONE,
                null,
                Instant.now(), Instant.now());
    }

    // ── State Guards ──────────────────────────────

    /**
     * Check if step is in terminal state.
     * Terminal steps cannot transition further.
     */
    private boolean isTerminal() {
        return status == AgentStepStatus.DONE
                || status == AgentStepStatus.FAILED;
    }

    // ── State Transition Methods ──────────────────

    /**
     * Mark step as DONE with result and timing.
     * Used for THINK and ACT steps after completion.
     *
     * @param result      output from this step
     * @param executionMs how long it took
     */
    public AgentStep withOutput(
            String result, int executionMs) {
        if (status != AgentStepStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING steps can produce "
                            + "output, current: "
                            + status);
        }
        return new AgentStep(
                id, agentId, userId,
                stepIndex, stepType,
                toolName, input,
                result,
                AgentStepStatus.DONE,
                executionMs,
                createdAt, Instant.now());
    }

    /**
     * Mark step as RUNNING.
     * Used when step begins execution.
     */
    public AgentStep withRunning() {
        if (isTerminal()
                || stepType == AgentStepType.OBSERVE
                || stepType == AgentStepType.FINAL
                || status != AgentStepStatus.PENDING) {
            throw new IllegalStateException(
                    "Invalid transition to RUNNING "
                            + "for type="
                            + stepType
                            + " status="
                            + status);
        }
        return new AgentStep(
                id, agentId, userId,
                stepIndex, stepType,
                toolName, input, output,
                AgentStepStatus.RUNNING,
                durationMs,
                createdAt, null);
    }

    /**
     * Mark step as FAILED with error message.
     *
     * @param error what went wrong
     */
    public AgentStep withFailed(String error) {
        if (status != AgentStepStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING steps can fail, "
                            + "current: "
                            + status);
        }
        return new AgentStep(
                id, agentId, userId,
                stepIndex, stepType,
                toolName, input,
                error,
                AgentStepStatus.FAILED,
                durationMs,
                createdAt, Instant.now());
    }
}
