package ai.jarvis.config;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

@Service
public class RuntimeSettingsService {

    private final JarvisProperties jarvisProperties;
    private volatile String voiceName;
    private volatile double voiceSpeed;

    public RuntimeSettingsService(JarvisProperties jarvisProperties) {
        this.jarvisProperties = jarvisProperties;
    }

    @PostConstruct
    public void init() {
        this.voiceName = jarvisProperties.getVoiceName();
        double configuredSpeed = jarvisProperties.getVoiceSpeed();
        if (configuredSpeed < 0.5 || configuredSpeed > 2.0) {
            throw new IllegalStateException("Configured voice speed out of range: " + configuredSpeed);
        }
        this.voiceSpeed = configuredSpeed;
    }

    public synchronized void updateVoiceSettings(String voiceName, Double voiceSpeed) {
        if (voiceName != null) {
            this.voiceName = voiceName;
        }
        if (voiceSpeed != null) {
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
