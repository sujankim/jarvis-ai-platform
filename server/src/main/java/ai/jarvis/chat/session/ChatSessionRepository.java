package ai.jarvis.chat.session;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ChatSessionRepository
        extends R2dbcRepository<ChatSession, UUID> {

    // Find all active sessions for a user
    // ordered by most recent message first
    Flux<ChatSession> findByUserIdAndStatusOrderByLastMessageAtDesc(
            UUID userId, String status);

    // Find a specific session that belongs to a user
    Mono<ChatSession> findByIdAndUserId(UUID userId, UUID sessionId);

    // Count sessions for a user
    Mono<Long> countByUserId(UUID userId);

    // Update message count and tokens after a chat
    @Query("""
            UPDATE chat_sessions
            SET message_count = message_count +1,
                total_tokens = total_tokens + :tokens,
                last_message_at = NOW(),
                updated_at = NOW()
            where id = :sessionId
            """)
    Mono<Void> incrementMessageCount(UUID sessionId, int tokens);

    // Auto-generate title from first message
    @Query("""
            UPDATE chat_sessions
            SET title = :title,
                updated_at = NOW()
            WHERE id = :sessionId AND title IS NULL
            """)
    Mono<Void> setTitleIfNull(UUID sessionId, String title);
}
