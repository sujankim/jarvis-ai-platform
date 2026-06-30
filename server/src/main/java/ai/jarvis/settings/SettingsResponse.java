package ai.jarvis.settings;

public record SettingsResponse(
        VoiceSettings voice,
        ProviderSettings provider,
        SystemInfo system
) {

    public record VoiceSettings(
            String voiceName,
            double voiceSpeed,
            String ttsEngine,
            String whisperMode
    ) {}

    public record ProviderSettings(
            String primaryProvider,
            String primaryModel,
            boolean geminiConfigured
    ) {}

    public record SystemInfo(
            String version,
            String javaVersion
    ) {}
}
