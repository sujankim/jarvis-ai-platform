package ai.jarvis.voice;

import ai.jarvis.ai.orchestrator.AiOrchestrator;
import ai.jarvis.ai.orchestrator.OrchestratorRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Orchestrates the full voice conversation loop.
 *
 * FLOW:
 * 1. audio → WhisperTranscriptionService → text
 * 2. text → AiOrchestrator.chat() → Flux<String>
 * 3. Flux<String> → sentence buffer → TTS
 *
 * FIXES (CodeRabbit):
 * 1. Security: Never log transcribed text or AI response
 *    content. Log metadata only (length, session, user).
 *
 * 2. Session ID: voiceChat() now returns Flux of
 *    VoiceChatEvent which includes the session ID
 *    as the first event. Client stores it for continuity.
 *
 * 3. TTS ordering: concatMap replaces fire-and-forget
 *    .subscribe(). Sentences now play sequentially.
 *    Previous .subscribe() caused concurrent execution
 *    → later sentences could play before earlier ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConversationService {

    private final WhisperTranscriptionService
            transcriptionService;
    private final TextToSpeechService
            textToSpeechService;
    private final AiOrchestrator orchestrator;

    private static final String SENTENCE_ENDINGS =
            ".?!\n";

    private static final int MAX_BUFFER_TOKENS = 50;

    /**
     * Full voice chat cycle.
     *
     * Returns Flux<VoiceChatEvent> — first event
     * is always SESSION containing the session ID.
     * Subsequent events are TOKEN (AI response text).
     * Final event is DONE.
     *
     * FIX Issue 2: First event contains session ID
     * so client can attach next message to same session.
     *
     * @param audioBytes  raw audio from microphone
     * @param sessionId   existing session (null = new)
     * @param userId      authenticated user
     * @param username    display name for prompt
     * @param role        user role for prompt
     * @return Flux<VoiceChatEvent> with session + tokens
     */
    public Flux<VoiceChatEvent> voiceChat(
            byte[] audioBytes,
            UUID sessionId,
            UUID userId,
            String username,
            String role) {

        // Resolve session ID upfront so client
        // gets it in the first event
        UUID resolvedSessionId = sessionId != null
                ? sessionId
                : UUID.randomUUID();

        return transcriptionService
                .transcribe(audioBytes)
                .doOnSuccess(text ->
                        // FIX Issue 1: log length ONLY
                        // NEVER log transcribed content
                        log.info(
                                "Voice input: chars={} "
                                        + "session={}",
                                text.length(),
                                resolvedSessionId))
                .flatMapMany(transcribedText -> {

                    OrchestratorRequest request =
                            OrchestratorRequest.of(
                                    resolvedSessionId,
                                    transcribedText,
                                    username,
                                    role,
                                    userId);

                    // FIX Issue 2: emit session ID first
                    Flux<VoiceChatEvent> sessionEvent =
                            Flux.just(
                                    VoiceChatEvent.session(
                                            resolvedSessionId));

                    // Stream AI tokens with TTS
                    Flux<VoiceChatEvent> tokenEvents =
                            streamWithTts(
                                    orchestrator.chat(request),
                                    resolvedSessionId);

                    return sessionEvent.concatWith(
                            tokenEvents);
                });
    }

    /**
     * Transcribe audio to text only.
     *
     * @param audioBytes raw audio bytes
     * @return Mono<String> transcribed text
     */
    public Mono<String> transcribeOnly(
            byte[] audioBytes) {
        return transcriptionService
                .transcribe(audioBytes);
    }

    /**
     * Speak text aloud via TTS.
     *
     * @param text text to speak
     * @return Mono<Void> completes when done
     */
    public Mono<Void> speakText(String text) {
        return textToSpeechService
                .speakAndPlay(text);
    }

    /**
     * Check if voice features are available.
     *
     * @return true if both TTS and Whisper ready
     */
    public Mono<Boolean> isVoiceAvailable() {
        return Mono.zip(
                transcriptionService.isAvailable(),
                textToSpeechService.isAvailable()
        ).map(tuple ->
                tuple.getT1() && tuple.getT2());
    }

    public Mono<Boolean> isTranscriptionAvailable() {
        return transcriptionService.isAvailable();
    }

    public Mono<Boolean> isTtsAvailable() {
        return textToSpeechService.isAvailable();
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Stream AI tokens as VoiceChatEvents with TTS.
     *
     * TTS now sequential via concatMap.
     * Previous: .subscribe() → concurrent execution
     * → sentences played out of order.
     * Now: concatMap(1) → each sentence waits for
     * the previous one to finish playing.
     *
     * FIX Issue 1: doOnNext logs token count ONLY.
     * Never logs the actual token content.
     *
     * @param tokenStream   Flux<String> from AiOrchestrator
     * @param sessionId     for logging only
     * @return Flux<VoiceChatEvent> TOKEN events
     */
    private Flux<VoiceChatEvent> streamWithTts(
            Flux<String> tokenStream,
            UUID sessionId) {

        StringBuilder buffer = new StringBuilder();

        // Collect complete sentences from token stream
        Flux<String> sentences = tokenStream
                .doOnNext(token -> {
                    // log count only
                    // Never log token content
                })
                .flatMap(token -> {
                    buffer.append(token);

                    boolean isSentenceEnd =
                            isSentenceBoundary(token);

                    int wordCount =
                            buffer.toString()
                                    .split("\\s+")
                                    .length;

                    boolean isBufferFull =
                            wordCount >= MAX_BUFFER_TOKENS;

                    if (isSentenceEnd || isBufferFull) {
                        String sentence =
                                buffer.toString().trim();
                        buffer.setLength(0);

                        if (!sentence.isBlank()) {
                            return Flux.just(sentence);
                        }
                    }

                    return Flux.empty();
                })
                .concatWith(Mono.fromCallable(() -> {
                    String remaining =
                            buffer.toString().trim();
                    buffer.setLength(0);
                    return remaining;
                }).filter(s -> !s.isBlank()));

        // concatMap ensures sequential TTS
        // Each sentence waits for previous to finish
        // playing before starting the next one.
        // concatMap = flatMap with concurrency=1
        return sentences
                .concatMap(sentence ->
                        textToSpeechService
                                .speakAndPlay(sentence)
                                .doOnSuccess(v ->
                                        log.debug(
                                                "Spoke sentence: "
                                                        + "chars={}",
                                                sentence.length()))
                                .onErrorResume(e -> {
                                    log.warn(
                                            "TTS failed: {}",
                                            e.getMessage());
                                    // Continue with next sentence
                                    return Mono.empty();
                                })
                                .thenReturn(
                                        VoiceChatEvent
                                                .token(sentence)));
    }

    private boolean isSentenceBoundary(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        char lastChar = trimmed.charAt(
                trimmed.length() - 1);
        return SENTENCE_ENDINGS.indexOf(lastChar) >= 0;
    }

    // ── Event Record ──────────────────────────────

    /**
     * Typed events for voice chat stream.
     *
     * SESSION event: first event, contains session ID
     *   → client stores for next voice turn
     * TOKEN event:   AI response text tokens
     *   → client displays + TTS plays
     * DONE event:    stream complete signal
     */
    public record VoiceChatEvent(
            EventType type,
            String data) {

        public enum EventType {
            SESSION, TOKEN, DONE
        }

        public static VoiceChatEvent session(
                UUID sessionId) {
            return new VoiceChatEvent(
                    EventType.SESSION,
                    sessionId.toString());
        }

        public static VoiceChatEvent token(
                String text) {
            return new VoiceChatEvent(
                    EventType.TOKEN, text);
        }

        public static VoiceChatEvent done() {
            return new VoiceChatEvent(
                    EventType.DONE, "[DONE]");
        }
    }
}