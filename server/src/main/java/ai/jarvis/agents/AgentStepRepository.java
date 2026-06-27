package ai.jarvis.agents;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for the agent_steps table.
 *
 * Steps retrieved in stepIndex order so the
 * ReACT loop is displayed correctly to users.
 */
@Repository
public interface AgentStepRepository
        extends R2dbcRepository<AgentStep, UUID> {

    /**
     * All steps for an agent in execution order.
     * Used to display full agent execution trace.
     */
    Flux<AgentStep> findByAgentIdOrderByStepIndexAsc(
            UUID agentId);

    /**
     * Count total steps for an agent.
     * Used to verify execution progress.
     */
    Mono<Long> countByAgentId(UUID agentId);

    /**
     * Steps of a specific type for an agent.
     * Example: get all ACT steps to see tool calls.
     */
    Flux<AgentStep> findByAgentIdAndStepType(
            UUID agentId, AgentStepType stepType);

    /**
     * All steps for a user across all agents.
     * Used for user-level analytics.
     */
    Flux<AgentStep> findByUserId(UUID userId);
}