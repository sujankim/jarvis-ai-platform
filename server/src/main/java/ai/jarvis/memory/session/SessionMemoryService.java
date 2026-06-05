package ai.jarvis.memory.session;

import ai.jarvis.chat.message.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    private final SessionCacheService sessionCacheService;

    /**
     * Load history: Redis first, PostgreSQL fallback.
     */
    public Mono<List<Message>> loadHistory(
            UUID sessionId) {
        return sessionCacheService
                .getSessionHistory(sessionId);
    }

    /**
     * Refresh cache after message saved.
     * Reloads from DB and re-caches.
     * Does NOT delete — persists for 1 hour TTL.
     */
    public Mono<Void> onMessageSaved(UUID sessionId) {
        return sessionCacheService
                .refreshCache(sessionId);
    }
}