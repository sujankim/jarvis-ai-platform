package ai.jarvis.chat.message;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface MessageRepository
        extends R2dbcRepository<Message, UUID> {

    // Load all messages for a session (chronological order)
    // This is the MOST IMPORTANT query — runs on every chat load
    Flux<Message> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    // Load last N messages (for context window management)
    Flux<Message> findTop20BySessionIdOrderByCreatedAtDesc(
            UUID sessionId);

    // Count messages in a session
    Mono<Long> countBySessionId(UUID sessionId);

    // Get only user + assistant messages (not system)
    Flux<Message> findBySessionIdAndRoleInOrderByCreatedAtAsc(
            UUID sessionId, java.util.List<MessageRole> roles);
}
