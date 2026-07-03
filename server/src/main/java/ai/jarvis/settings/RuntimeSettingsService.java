package ai.jarvis.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class RuntimeSettingsService {

    @Value("${jarvis.voice.tts.voice:}")
    private String initialVoiceName;

    @Value("${jarvis.voice.tts.speed:1.0}")
    private double initialVoiceSpeed;

    private volatile String voiceName;
    private volatile double voiceSpeed;

    @PostConstruct
    public void init() {
        this.voiceName = initialVoiceName != null ? initialVoiceName : "";
        if (initialVoiceSpeed < 0.5 || initialVoiceSpeed > 2.0) {
            throw new IllegalStateException("Configured voice speed out of range: " + initialVoiceSpeed);
        }
        this.voiceSpeed = initialVoiceSpeed;
    }

    public synchronized void updateVoiceSettings(String voiceName, Double voiceSpeed) {
        if (voiceName != null) {
            this.voiceName = voiceName;
        }
        if (voiceSpeed != null) {
            if (voiceSpeed < 0.5 || voiceSpeed > 2.0) {
                throw new IllegalArgumentException("Voice speed out of range: " + voiceSpeed);
            }
            this.voiceSpeed = voiceSpeed;
        }
    }

    public String getVoiceName() {
        return voiceName;
    }

    public double getVoiceSpeed() {
        return voiceSpeed;
    }
}
