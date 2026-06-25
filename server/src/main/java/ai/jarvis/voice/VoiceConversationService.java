package ai.jarvis.voice;

import ai.jarvis.ai.orchestrator.AiOrchestrator;
import ai.jarvis.ai.orchestrator.OrchestratorRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * Orchestrates the full voice conversation loop.
 *
 * FIXES (CodeRabbit Round 2):
 *
 * Fix 1: generateAudioBytes() added.
 * /speak/bytes needs bytes back, not server playback.
 * speakText() calls speakAndPlay() → void → wrong.
 * generateAudioBytes() calls speak() → Mono<byte[]>.
 *
 * Fix 2: SSE tokens independent from TTS.
 * BEFORE: emit token AFTER TTS completes → blocks SSE
 * AFTER:  token stream → SSE immediately (fast path)
 *         sentence buffer → TTS in parallel (slow path)
 * Two independent pipelines — UI gets incremental output.
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
     * Returns Flux<VoiceChatEvent>:
     * - First: SESSION event with session ID
     * - Then:  TOKEN events (AI response, streamed fast)
     * - TTS:   plays sentences in background (parallel)
     *
     * FIX Issue 2: SSE tokens are NOT blocked by TTS.
     * Tokens stream to client immediately as AI generates.
     * TTS runs as a side effect on parallel scheduler.
     *
     * @param audioBytes  raw audio from microphone
     * @param sessionId   existing session (null = new)
     * @param userId      authenticated user
     * @param username    display name for prompt
     * @param role        user role for prompt
     * @return Flux<VoiceChatEvent>
     */
    public Flux<VoiceChatEvent> voiceChat(
            byte[] audioBytes,
            UUID sessionId,
            UUID userId,
            String username,
            String role) {

        UUID resolvedSessionId = sessionId != null
                ? sessionId
                : UUID.randomUUID();

        return transcriptionService
                .transcribe(audioBytes)
                .doOnSuccess(text ->
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

                    // SESSION event first
                    Flux<VoiceChatEvent> sessionEvent =
                            Flux.just(
                                    VoiceChatEvent.session(
                                            resolvedSessionId));

                    // FIX Issue 2: separate pipelines
                    Flux<String> tokenStream =
                            orchestrator.chat(request);

                    // Run TTS in background
                    // Does NOT block SSE token emission
                    startTtsPipeline(tokenStream);

                    // Emit tokens immediately to SSE
                    Flux<VoiceChatEvent> tokenEvents =
                            tokenStream.map(
                                    VoiceChatEvent::token);

                    return sessionEvent
                            .concatWith(tokenEvents);
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
     * Speak text aloud via TTS (server speakers).
     * Returns Mono<Void> — no audio bytes returned.
     *
     * @param text text to speak
     * @return Mono<Void> completes when done
     */
    public Mono<Void> speakText(String text) {
        return textToSpeechService
                .speakAndPlay(text);
    }

    /**
     * FIX Issue 1: Generate audio bytes from text.
     * /speak/bytes needs byte[] not server playback.
     *
     * Calls speak() → Mono<byte[]> (returns audio data)
     * NOT speakAndPlay() → Mono<Void> (plays on server)
     *
     * @param text text to convert to audio
     * @return Mono<byte[]> audio data (wav format)
     */
    public Mono<byte[]> generateAudioBytes(String text) {
        return textToSpeechService.speak(text);
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
     * FIX Issue 2: TTS pipeline runs independently.
     *
     * Starts a SEPARATE subscription to the token stream
     * that buffers into sentences and speaks them.
     * This pipeline runs on boundedElastic (background).
     *
     * The ORIGINAL tokenStream subscription (SSE)
     * is completely unaffected — tokens stream fast.
     *
     * WHY separate subscription works:
     * AiOrchestrator.chat() returns a HOT-ish Flux.
     * Both subscribers receive all tokens independently.
     * SSE subscriber: emit immediately
     * TTS subscriber: buffer → sentence → speak
     *
     * @param tokenStream Flux<String> from AiOrchestrator
     */
    private void startTtsPipeline(
            Flux<String> tokenStream) {

        StringBuilder buffer = new StringBuilder();

        tokenStream
                // Buffer tokens into sentences
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
                    return Flux.<String>empty();
                })
                // Flush remaining buffer at end
                .concatWith(Mono.fromCallable(() -> {
                    String remaining =
                            buffer.toString().trim();
                    buffer.setLength(0);
                    return remaining;
                }).filter(s -> !s.isBlank()))
                // Speak sentences sequentially
                // concatMap = one at a time
                .concatMap(sentence ->
                        textToSpeechService
                                .speakAndPlay(sentence)
                                .doOnSuccess(v ->
                                        log.debug(
                                                "Spoke: chars={}",
                                                sentence.length()))
                                .onErrorResume(e -> {
                                    log.warn(
                                            "TTS failed: {}",
                                            e.getMessage());
                                    return Mono.empty();
                                }))
                // Subscribe on background thread
                // Never blocks the SSE token stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.warn(
                                "TTS pipeline error: {}",
                                error.getMessage()));
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