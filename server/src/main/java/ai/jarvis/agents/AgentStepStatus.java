package ai.jarvis.agents;

/**
 * Execution status of a single agent step.
 *
 * PENDING → Step created, not yet started
 * RUNNING → Step currently executing
 * DONE    → Step completed successfully
 * FAILED  → Step encountered an error
 *
 * Allows clients to see which step is
 * currently running in real time.
 */
public enum AgentStepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}