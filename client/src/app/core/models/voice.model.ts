/**
 * Maps to: ai.jarvis.voice.VoiceResponse
 * Response from POST /api/v1/voice/transcribe
 */
export interface VoiceTranscription {
  transcription: string;
  language: string | null;
  mode: 'groq-cloud' | 'local-whisper';
}

/**
 * Maps to: ai.jarvis.voice.VoiceController.VoiceStatusResponse
 * Response from GET /api/v1/voice/status
 */
export interface VoiceStatus {
  transcriptionAvailable: boolean;
  ttsAvailable: boolean;
  voiceReady: boolean;
}

/**
 * Maps to: ai.jarvis.voice.VoiceRequest
 * Request body for POST /api/v1/voice/speak
 */
export interface VoiceRequest {
  text: string;
  language?: string | null;
  sessionId?: string | null;
}

/**
 * SSE events from POST /api/v1/voice/chat
 *
 * event: session → data = sessionId string
 * event: token   → data = token text
 * event: done    → data = "[DONE]"
 */
export interface VoiceChatEvent {
  type: 'session' | 'token' | 'done';
  data: string;
}
