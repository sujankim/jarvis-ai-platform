package ai.jarvis.agents;

import java.util.UUID;

/**
 * Event emitted during agent execution for SSE streaming.
 *
 * Each step in the ReACT loop produces one or more events.
 * Clients receive these as Server-Sent Events to show
 * real-time agent progress instead of waiting for completion.
 *
 * EVENT TYPES:
 * THINK:   AI is reasoning about what to do next
 * ACT:     AI has decided to call a specific tool
 * OBSERVE: Tool has returned a result
 * FINAL:   Agent has the complete answer
 * ERROR:   Something went wrong during execution
 *
 * The stepIndex helps clients track which step
 * produced each event for display ordering.
 */
public record AgentEvent(
        EventType type,
        String data,
        String toolName,
        int stepIndex
) {

    public enum EventType {
        THINK, ACT, OBSERVE, FINAL, ERROR
    }

    // ── Factory Methods ───────────────────────────

    /**
     * AI is reasoning about what to do next.
     *
     * @param step      current step index
     * @param reasoning AI's reasoning text
     */
    public static AgentEvent think(
            int step, String reasoning) {
        return new AgentEvent(
                EventType.THINK,
                reasoning,
                null,
                step);
    }

    /**
     * AI has decided to call a tool.
     *
     * @param step     current step index
     * @param toolName which tool to call
     * @param input    what to pass to the tool
     */
    public static AgentEvent act(
            int step,
            String toolName,
            String input) {
        return new AgentEvent(
                EventType.ACT,
                input,
                toolName,
                step);
    }

    /**
     * Tool has returned a result.
     *
     * @param step     current step index
     * @param toolName which tool produced this
     * @param result   the tool's output
     */
    public static AgentEvent observe(
            int step,
            String toolName,
            String result) {
        return new AgentEvent(
                EventType.OBSERVE,
                result,
                toolName,
                step);
    }

    /**
     * Agent has synthesized the final answer.
     *
     * @param step   current step index
     * @param answer the complete answer for the user
     */
    public static AgentEvent finalAnswer(
            int step, String answer) {
        return new AgentEvent(
                EventType.FINAL,
                answer,
                null,
                step);
    }

    /**
     * An error occurred during execution.
     * stepIndex = -1 indicates non-step error.
     *
     * @param message description of what went wrong
     */
    public static AgentEvent error(String message) {
        return new AgentEvent(
                EventType.ERROR,
                message,
                null,
                -1);
    }
}