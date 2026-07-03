package ai.jarvis.voice;

import ai.jarvis.config.RuntimeSettingsService;
import org.springframework.stereotype.Service;

@Service
public class SystemTextToSpeechService {

    private final RuntimeSettingsService runtimeSettingsService;

    public SystemTextToSpeechService(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public void speak(String text) {
        String currentVoiceName = runtimeSettingsService.getVoiceName();
        double currentVoiceSpeed = runtimeSettingsService.getVoiceSpeed();
        
        // TTS ইঞ্জিন কল করার লজিক এখানে থাকবে...
    }
}
