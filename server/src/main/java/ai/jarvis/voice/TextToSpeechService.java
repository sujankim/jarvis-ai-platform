package ai.jarvis.voice;

import reactor.core.publisher.Mono;

/**
 * Contract for text-to-speech implementations.
 *
 * STRATEGY PATTERN:
 * VoiceConversationService depends on this interface.
 * Implementations can be swapped without any changes
 * to VoiceConversationService.
 *
 * CURRENT IMPLEMENTATION:
 * SystemTextToSpeechService — uses OS commands.
 * Works on Windows, macOS, Linux with zero setup.
 *
 * FUTURE IMPLEMENTATIONS:
 * OllamaTextToSpeechService — when Ollama TTS stable
 * CoquiTextToSpeechService  — higher quality local TTS
 */
public interface TextToSpeechService {

    /**
     * Convert text to speech audio bytes.
     *
     * Returns audio as byte[] (wav format).
     * Caller can write to file or stream to client.
     *
     * @param text the text to speak
     * @return Mono<byte[]> audio data
     */
    Mono<byte[]> speak(String text);

    /**
     * Speak text and play immediately on system audio.
     * Non-blocking — uses system audio player.
     *
     * @param text the text to speak aloud
     * @return Mono<Void> completes when speaking done
     */
    Mono<Void> speakAndPlay(String text);

    /**
     * Check if TTS is available on this system.
     *
     * @return true if TTS commands available
     */
    Mono<Boolean> isAvailable();

    /**
     * Get the name of this TTS implementation.
     * Used for logging and health checks.
     *
     * @return implementation name
     */
    String getName();
}