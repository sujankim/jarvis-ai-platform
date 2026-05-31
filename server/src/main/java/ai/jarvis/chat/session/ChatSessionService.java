package ai.jarvis.chat.session;

import ai.jarvis.chat.message.MessageMapper;
import ai.jarvis.chat.message.MessageRepository;
import ai.jarvis.chat.message.MessageResponse;
import ai.jarvis.common.exception.SessionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    public final ChatSessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ChatSessionMapper sessionMapper;
    private final MessageMapper messageMapper;

    // ── List sessions for user ────────────────────

    public Flux<ChatSessionResponse> getUserSessions(
            UUID userId) {
        return sessionRepository
                .findByUserIdAndStatusOrderByLastMessageAtDesc(
                        userId, ChatSession.STATUS_ACTIVE)
                .map(sessionMapper::toResponse);
    }

    // ── Get single session ────────────────────────

    public Mono<ChatSessionResponse> getSession(
            UUID sessionId, UUID userId) {
        return sessionRepository
                .findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(
                        new SessionNotFoundException(sessionId)))
                .map(sessionMapper::toResponse);
    }

    // ── Get session messages ──────────────────────

    public Flux<MessageResponse> getSessionMessages(
            UUID sessionId, UUID userId) {

        // First verify session belongs to user
        return sessionRepository
                .findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(
                        new SessionNotFoundException(sessionId)))
                .flatMapMany(session ->
                        messageRepository
                                .findBySessionIdOrderByCreatedAtAsc(
                                        sessionId)
                )
                .map(messageMapper::toResponse);
    }

    // ── Archive session (soft delete) ────────────

    public Mono<Void> archiveSession(
            UUID sessionId, UUID userId) {

        return sessionRepository
                .findByIdAndUserId(sessionId, userId)
                .switchIfEmpty(Mono.error(
                        new SessionNotFoundException(sessionId)))
                .flatMap(session -> {
                    log.info("Archiving session: id={} userId={}",
                            sessionId, userId);
                    // Create archived copy
                    ChatSession archived = new ChatSession(
                            session.id(),
                            session.userId(),
                            session.title(),
                            ChatSession.STATUS_ARCHIVED,
                            session.providerId(),
                            session.systemPrompt(),
                            session.messageCount(),
                            session.totalToken(),
                            session.createdAt(),
                            session.updatedAt(),
                            session.lastMessageAt()
                    );
                    return sessionRepository.save(archived);
                })
                .then();
    }
}

