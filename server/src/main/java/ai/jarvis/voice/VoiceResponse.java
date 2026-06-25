package ai.jarvis.voice;

/**
 * Response for transcription endpoint.
 * POST /api/v1/voice/transcribe
 */
public record VoiceResponse(

        // Transcribed text from audio
        String transcription,

        // Detected or specified language
        String language,

        // Which transcription mode was used
        // "groq-cloud" or "local-whisper"
        String mode

) {}