/**
 * Maps to: ai.jarvis.settings.SettingsResponse.VoiceSettings
 */
export interface VoiceSettings {
  voiceName: string;
  voiceSpeed: number;
  ttsEngine: string;
  whisperMode: string;
}

/**
 * Maps to: ai.jarvis.settings.SettingsResponse.ProviderSettings
 */
export interface ProviderSettings {
  primaryProvider: string;
  primaryModel: string;
  geminiConfigured: boolean;
}

/**
 * Maps to: ai.jarvis.settings.SettingsResponse.SystemInfo
 */
export interface SystemInfo {
  version: string;
  javaVersion: string;
}

/**
 * Maps to: ai.jarvis.settings.SettingsResponse
 * Response from GET /api/v1/settings
 */
export interface Settings {
  voice: VoiceSettings;
  provider: ProviderSettings;
  system: SystemInfo;
}

/**
 * Request body for PATCH /api/v1/settings/voice
 */
export interface VoiceSettingsRequest {
  voiceName?: string | null;
  voiceSpeed?: number | null;
}
