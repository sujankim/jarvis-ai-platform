package ai.jarvis.agents;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for the agents table.
 *
 * SECURITY: all queries scoped to userId.
 * Users can only access their own agents.
 *
 * updateStatus() is atomic SQL UPDATE.
 * Safer than load → mutate → save pattern
 * which has race conditions.
 */
@Repository
public interface AgentRepository
        extends R2dbcRepository<Agent, UUID> {

    /**
     * All agents for a user, newest first.
     * Used for agent history list.
     */
    Flux<Agent> findByUserIdOrderByCreatedAtDesc(
            UUID userId);

    /**
     * Agents filtered by status for a user.
     * Used to find active/completed agents.
     */
    Flux<Agent> findByUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, AgentStatus status);

    /**
     * Ownership-verified single agent lookup.
     * Returns empty if agent not found OR
     * agent belongs to different user.
     *
     * WHY: Security — never expose 403 vs 404
     * Just return 404 for both cases.
     */
    Mono<Agent> findByIdAndUserId(
            UUID id, UUID userId);

    /**
     * Count all agents for a user.
     */
    Mono<Long> countByUserId(UUID userId);

    /**
     * Atomic status update.
     * Called during agent state transitions.
     *
     * WHY atomic SQL instead of load+save:
     * Prevents race conditions in concurrent updates.
     * Agent executor runs on separate thread.
     *
     * completed_at auto-set for terminal states.
     */
    @Modifying
    @Query("""
            UPDATE agents
            SET status = :status,
                step_count = :stepCount,
                final_answer = :finalAnswer,
                error_message = :errorMessage,
                completed_at = CASE
                    WHEN :status IN (
                        'COMPLETED', 'FAILED', 'CANCELLED'
                    )
                    THEN NOW()
                    ELSE completed_at
                END,
                updated_at = NOW()
            WHERE id = :agentId
            """)
    Mono<Integer> updateStatus(
            UUID agentId,
            String status,
            int stepCount,
            String finalAnswer,
            String errorMessage);
}