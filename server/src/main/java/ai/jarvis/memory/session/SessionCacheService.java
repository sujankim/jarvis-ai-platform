package ai.jarvis.memory.session;

import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.message.MessageRepository;
import ai.jarvis.chat.message.MessageRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SessionCacheService {

    private final ReactiveRedisTemplate<String, String>
            redisTemplate;
    private final MessageRepository messageRepository;

    private static final Duration SESSION_TTL =
            Duration.ofHours(1);
    private static final String MESSAGES_PREFIX =
            "session:messages:";
    private static final int MAX_MESSAGES = 20;
    private static final String SEPARATOR = "|";
    private static final String LINE_SEP = "\n";

    public SessionCacheService(
            @Qualifier("reactiveStringRedisTemplate")
            ReactiveRedisTemplate<String, String>
                    redisTemplate,
            MessageRepository messageRepository) {
        this.redisTemplate = redisTemplate;
        this.messageRepository = messageRepository;
    }

    /**
     * Get session history from Redis or PostgreSQL.
     * Cache HIT  → Redis   (~1ms)
     * Cache MISS → PostgreSQL (~50ms) then cached
     */
    public Mono<List<Message>> getSessionHistory(
            UUID sessionId) {

        String key = MESSAGES_PREFIX + sessionId;

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(cached -> {
                    log.debug("Cache HIT: {}", sessionId);
                    // Extend TTL on access
                    return redisTemplate
                            .expire(key, SESSION_TTL)
                            .thenReturn(
                                    deserialize(cached,
                                            sessionId));
                })
                .switchIfEmpty(
                        loadAndCache(sessionId, key)
                )
                .onErrorResume(error -> {
                    log.warn(
                            "Redis error, using DB: {}",
                            error.getMessage());
                    return loadFromDb(sessionId);
                });
    }

    /**
     * Refresh cache after new message saved.
     * Reloads fresh data from DB and re-caches.
     * Does NOT delete — just refreshes.
     */
    public Mono<Void> refreshCache(UUID sessionId) {
        String key = MESSAGES_PREFIX + sessionId;
        return loadFromDb(sessionId)
                .flatMap(messages ->
                        cacheMessages(key, messages))
                .doOnSuccess(v ->
                        log.debug("Cache refreshed: {}",
                                sessionId))
                .then();
    }

    /**
     * Hard invalidate — only when session deleted.
     * NOT called after every message anymore.
     */
    public Mono<Void> invalidate(UUID sessionId) {
        return redisTemplate
                .delete(MESSAGES_PREFIX + sessionId)
                .doOnSuccess(d ->
                        log.debug("Cache deleted: {}",
                                sessionId))
                .then();
    }

    // ── Private ───────────────────────────────────

    private Mono<List<Message>> loadAndCache(
            UUID sessionId, String key) {
        return loadFromDb(sessionId)
                .flatMap(messages ->
                        cacheMessages(key, messages)
                                .thenReturn(messages)
                );
    }

    private Mono<List<Message>> loadFromDb(
            UUID sessionId) {
        log.debug("Cache MISS: {} — DB query",
                sessionId);
        return messageRepository
                .findBySessionIdOrderByCreatedAtAsc(
                        sessionId)
                .collectList()
                .map(messages -> {
                    if (messages.size() > MAX_MESSAGES) {
                        return messages.subList(
                                messages.size()
                                        - MAX_MESSAGES,
                                messages.size());
                    }
                    return messages;
                });
    }

    private Mono<Void> cacheMessages(
            String key, List<Message> messages) {
        String serialized = serialize(messages);
        return redisTemplate.opsForValue()
                .set(key, serialized, SESSION_TTL)
                .doOnSuccess(ok ->
                        log.debug(
                                "Cached {} msgs: {}",
                                messages.size(), key))
                .then();
    }

    private String serialize(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            if (msg.role() == null) continue;
            if (msg.content() == null) continue;
            sb.append(msg.role().name())
                    .append(SEPARATOR)
                    .append(msg.id().toString())
                    .append(SEPARATOR)
                    .append(msg.content()
                            .replace("\n", "\\n"))
                    .append(LINE_SEP);
        }
        return sb.toString();
    }

    private List<Message> deserialize(
            String data, UUID sessionId) {
        List<Message> messages = new ArrayList<>();
        if (data == null || data.isBlank()) {
            return messages;
        }
        String[] lines = data.split(LINE_SEP);
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split(
                    "\\" + SEPARATOR, 3);
            if (parts.length < 3) continue;
            try {
                MessageRole role = MessageRole
                        .valueOf(parts[0].trim());
                UUID id = UUID.fromString(
                        parts[1].trim());
                String content = parts[2]
                        .replace("\\n", "\n");
                messages.add(new Message(
                        id, sessionId, role, content,
                        null, null, null, null, null,
                        null, null, false, null,
                        Instant.now()));
            } catch (Exception e) {
                log.warn("Skip line: {}", line);
            }
        }
        return messages;
    }
}