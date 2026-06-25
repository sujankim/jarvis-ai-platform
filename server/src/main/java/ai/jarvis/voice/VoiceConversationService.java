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
 * DESIGN:
 * This service is a WRAPPER around existing pipeline.
 * AiOrchestrator is UNCHANGED — voice just feeds
 * transcribed text as the user message.
 * All Phase 2 memory + Phase 3 RAG still work.
 *
 * SENTENCE BUFFERING:
 * Tokens buffered into complete sentences.
 * Each sentence spoken as soon as complete.
 * AI keeps generating while TTS plays.
 * Result: natural, low-latency voice conversation.
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

    /**
     * Sentence boundary characters.
     * Buffer flushed when token ends with these.
     */
    private static final String SENTENCE_ENDINGS =
            ".?!\n";

    /**
     * Max tokens before forced flush.
     * Prevents indefinitely growing buffer
     * when AI generates no punctuation.
     */
    private static final int MAX_BUFFER_TOKENS = 50;

    /**
     * Full voice chat cycle:
     * audio → transcribe → AI → TTS.
     *
     * Returns Flux<String> of AI text tokens
     * so SSE streaming works for web clients.
     * TTS happens as side effect (speakAndPlay).
     *
     * @param audioBytes  raw audio from microphone
     * @param sessionId   chat session (null = new)
     * @param userId      authenticated user
     * @param username    display name for prompt
     * @param role        user role for prompt
     * @return Flux<String> AI response tokens
     */
    public Flux<String> voiceChat(
            byte[] audioBytes,
            UUID sessionId,
            UUID userId,
            String username,
            String role) {

        return transcriptionService
                .transcribe(audioBytes)
                .doOnSuccess(text ->
                        log.info(
                                "Voice input: '{}'",
                                text.length() > 50
                                        ? text.substring(
                                        0, 47) + "..."
                                        : text))
                .flatMapMany(transcribedText -> {

                    OrchestratorRequest request =
                            OrchestratorRequest.of(
                                    sessionId != null
                                            ? sessionId
                                            : UUID.randomUUID(),
                                    transcribedText,
                                    username,
                                    role,
                                    userId);

                    // Stream AI tokens with TTS
                    return streamWithTts(
                            orchestrator.chat(request));
                });
    }

    /**
     * Transcribe audio to text only.
     * No AI call — just speech-to-text.
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
     * Converts text to audio and plays it.
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

    /**
     * Check transcription service availability.
     *
     * @return Mono<Boolean>
     */
    public Mono<Boolean> isTranscriptionAvailable() {
        return transcriptionService.isAvailable();
    }

    /**
     * Check TTS service availability.
     *
     * @return Mono<Boolean>
     */
    public Mono<Boolean> isTtsAvailable() {
        return textToSpeechService.isAvailable();
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Stream AI tokens with sentence-buffered TTS.
     *
     * ALGORITHM:
     * 1. Collect tokens into buffer
     * 2. When sentence boundary detected:
     *    → Speak the buffered sentence (async)
     *    → Clear buffer
     *    → Continue collecting
     * 3. If buffer hits MAX_BUFFER_TOKENS:
     *    → Force flush (speak partial sentence)
     * 4. At stream end:
     *    → Speak any remaining buffered text
     *
     * @param tokenStream Flux<String> from AiOrchestrator
     * @return Flux<String> same tokens (pass-through)
     */
    private Flux<String> streamWithTts(
            Flux<String> tokenStream) {

        StringBuilder buffer = new StringBuilder();

        return tokenStream
                .doOnNext(token -> {
                    buffer.append(token);

                    // Check for sentence boundary
                    boolean isSentenceEnd =
                            isSentenceBoundary(token);

                    // Check for buffer overflow
                    int wordCount =
                            buffer.toString()
                                    .split("\\s+")
                                    .length;

                    boolean isBufferFull =
                            wordCount >= MAX_BUFFER_TOKENS;

                    if (isSentenceEnd || isBufferFull) {
                        String sentence =
                                buffer.toString().trim();

                        if (!sentence.isBlank()) {
                            log.debug(
                                    "Speaking sentence: "
                                            + "{}...",
                                    sentence.length() > 30
                                            ? sentence
                                            .substring(
                                                    0, 30)
                                            : sentence);

                            // Fire-and-forget TTS
                            // Do not block the stream
                            textToSpeechService
                                    .speakAndPlay(sentence)
                                    .doOnError(e ->
                                            log.warn(
                                                    "TTS failed: {}",
                                                    e.getMessage()))
                                    .subscribe();
                        }

                        buffer.setLength(0);
                    }
                })
                .doOnComplete(() -> {
                    // Speak any remaining tokens
                    String remaining =
                            buffer.toString().trim();

                    if (!remaining.isBlank()) {
                        log.debug(
                                "Speaking final buffer: "
                                        + "{}",
                                remaining.length() > 30
                                        ? remaining
                                        .substring(0, 30)
                                          + "..."
                                        : remaining);

                        textToSpeechService
                                .speakAndPlay(remaining)
                                .doOnError(e ->
                                        log.warn(
                                                "Final TTS failed: {}",
                                                e.getMessage()))
                                .subscribe();
                    }
                });
    }

    /**
     * Detect sentence boundary in token.
     *
     * A sentence ends when the token contains
     * a sentence-ending character after trimming.
     * Handles multi-character tokens from AI.
     *
     * @param token AI-generated token string
     * @return true if sentence boundary detected
     */
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
}