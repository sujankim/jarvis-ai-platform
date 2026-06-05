package ai.jarvis.chat.message;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface MessageRepository
        extends R2dbcRepository<Message, UUID> {

    // Full history (ascending) — for display
    Flux<Message> findBySessionIdOrderByCreatedAtAsc(
            UUID sessionId);

    // Last N messages at DB level — for prompt context
    // Fetches newest N rows, reverses for ascending order
    @Query("""
            SELECT * FROM (
                SELECT * FROM messages
                WHERE session_id = :sessionId
                ORDER BY created_at DESC
                LIMIT :limit
            ) sub
            ORDER BY created_at ASC
            """)
    Flux<Message> findLastNBySessionId(
            UUID sessionId, int limit);

    Mono<Long> countBySessionId(UUID sessionId);

    Flux<Message> findBySessionIdAndRoleInOrderByCreatedAtAsc(
            UUID sessionId,
            java.util.List<MessageRole> roles);
}