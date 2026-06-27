package ai.jarvis.agents;

/**
 * Lifecycle status of an agent execution.
 *
 * LIFECYCLE:
 * PENDING   → Created, waiting to start
 * RUNNING   → Currently executing ReACT steps
 * COMPLETED → All steps done, final_answer ready
 * FAILED    → Error occurred, see error_message
 * CANCELLED → User cancelled before completion
 *
 * DB constraint enforces these exact values.
 * R2dbcConfig converters handle String ↔ enum.
 */
public enum AgentStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}