package ai.jarvis.voice.exception;

import ai.jarvis.common.exception.JarvisException;
import org.springframework.http.HttpStatus;

/**
 * Exception for voice-related errors.
 *
 * FIX (CodeRabbit):
 * whisperNotAvailable() message updated.
 * Old: "Run: ollama pull whisper" — WRONG
 *      Ollama does not support Whisper natively.
 * New: Points to correct backends (Groq or whisper.cpp)
 */
public class VoiceException extends JarvisException {

    public VoiceException(String message) {
        super(
                "VOICE_ERROR",
                message,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public VoiceException(
            String errorCode,
            String message,
            HttpStatus status) {
        super(errorCode, message, status);
    }

    // ── Factory methods ───────────────────────────

    /**
     * FIX: Message now points to correct backends.
     * Ollama does NOT support Whisper.
     * Use Groq API or local whisper.cpp server.
     */
    public static VoiceException whisperNotAvailable() {
        return new VoiceException(
                "WHISPER_NOT_AVAILABLE",
                "Whisper transcription not available. "
                        + "Set GROQ_API_KEY for cloud "
                        + "transcription, or start a "
                        + "local whisper.cpp server. "
                        + "See: github.com/ggerganov"
                        + "/whisper.cpp",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static VoiceException transcriptionFailed(
            String reason) {
        return new VoiceException(
                "TRANSCRIPTION_FAILED",
                "Audio transcription failed: "
                        + reason,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static VoiceException ttsNotAvailable() {
        return new VoiceException(
                "TTS_NOT_AVAILABLE",
                "Text-to-speech not available "
                        + "on this system.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static VoiceException emptyAudio() {
        return new VoiceException(
                "EMPTY_AUDIO",
                "Audio data is empty. "
                        + "Please provide a valid "
                        + "audio recording.",
                HttpStatus.BAD_REQUEST);
    }
}