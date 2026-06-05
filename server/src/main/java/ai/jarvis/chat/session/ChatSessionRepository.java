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

    Flux<ChatSession> findByUserIdAndStatusOrderByLastMessageAtDesc(
            UUID userId, String status);

    Mono<ChatSession> findByIdAndUserId(
            UUID id, UUID userId);

    Mono<Long> countByUserId(UUID userId);

    @Query("""
            UPDATE chat_sessions
            SET message_count = message_count + 1,
                total_tokens = total_tokens + :tokens,
                last_message_at = NOW(),
                updated_at = NOW()
            WHERE id = :sessionId
            """)
    Mono<Integer> incrementMessageCount(
            UUID sessionId, int tokens);

    @Query("""
            UPDATE chat_sessions
            SET title = :title,
                updated_at = NOW()
            WHERE id = :sessionId AND title IS NULL
            """)
    Mono<Void> setTitleIfNull(UUID sessionId, String title);
}