package ai.jarvis.voice.exception;

import ai.jarvis.common.exception.JarvisException;
import org.springframework.http.HttpStatus;

/**
 * Exception for voice-related errors.
 *
 * Covers:
 * - Whisper model not installed
 * - Transcription failures
 * - TTS failures
 * - Unsupported audio format
 */
public class VoiceException extends JarvisException {

    public VoiceException(
            String message) {
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

    public static VoiceException whisperNotAvailable() {
        return new VoiceException(
                "WHISPER_NOT_AVAILABLE",
                "Whisper model not available. "
                        + "Run: ollama pull whisper",
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