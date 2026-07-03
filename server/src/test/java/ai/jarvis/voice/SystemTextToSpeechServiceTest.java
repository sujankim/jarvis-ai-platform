package ai.jarvis.voice;

import ai.jarvis.config.RuntimeSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SystemTextToSpeechService Tests")
class SystemTextToSpeechServiceTest {

    private SystemTextToSpeechService service;
    private RuntimeSettingsService mockSettings;

    @BeforeEach
    void setUp() {
        mockSettings = mock(RuntimeSettingsService.class);
        when(mockSettings.getVoiceName()).thenReturn("");
        when(mockSettings.getVoiceSpeed()).thenReturn(1.0);
        service = new SystemTextToSpeechService(mockSettings);
    }

    @Test
    @DisplayName("speak() returns empty bytes for null text")
    void shouldReturnEmptyForNullText() {
        StepVerifier
                .create(service.speak(null))
                .expectNextMatches(bytes -> bytes.length == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("speak() returns empty bytes for blank text")
    void shouldReturnEmptyForBlankText() {
        StepVerifier
                .create(service.speak("   "))
                .expectNextMatches(bytes -> bytes.length == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("speakAndPlay() completes for null text")
    void shouldCompleteForNullText() {
        StepVerifier
                .create(service.speakAndPlay(null))
                .verifyComplete();
    }

    @Test
    @DisplayName("speakAndPlay() completes for blank text")
    void shouldCompleteForBlankText() {
        StepVerifier
                .create(service.speakAndPlay("  "))
                .verifyComplete();
    }

    @Test
    @DisplayName("isAvailable() returns boolean without error")
    void shouldReturnAvailabilityWithoutError() {
        StepVerifier
                .create(service.isAvailable())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("getName() returns system implementation name")
    void shouldReturnSystemName() {
        String name = service.getName();

        assertThat(name)
                .isNotBlank()
                .startsWith("system-");
    }

    @Test
    @DisplayName("getName() detects correct OS")
    void shouldDetectCorrectOs() {
        String name = service.getName();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            assertThat(name).isEqualTo("system-windows");
        } else if (os.contains("mac")) {
            assertThat(name).isEqualTo("system-macos");
        } else {
            assertThat(name).isEqualTo("system-linux");
        }
    }

    @Test
    @DisplayName("speak() never throws exception")
    void shouldNeverThrowException() {
        StepVerifier
                .create(service.speak("test"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("constructor accepts custom voice name")
    void shouldAcceptCustomVoiceName() {
        RuntimeSettingsService customSettings = mock(RuntimeSettingsService.class);
        when(customSettings.getVoiceName()).thenReturn("Microsoft Zira Desktop");
        when(customSettings.getVoiceSpeed()).thenReturn(1.0);
        
        SystemTextToSpeechService customService = new SystemTextToSpeechService(customSettings);

        assertThat(customService.getName())
                .isNotBlank()
                .startsWith("system-");
    }

    @Test
    @DisplayName("constructor accepts custom speed")
    void shouldAcceptCustomSpeed() {
        RuntimeSettingsService fastSettings = mock(RuntimeSettingsService.class);
        when(fastSettings.getVoiceName()).thenReturn("");
        when(fastSettings.getVoiceSpeed()).thenReturn(1.5);
        
        SystemTextToSpeechService fastService = new SystemTextToSpeechService(fastSettings);

        assertThat(fastService.getName())
                .isNotBlank();
    }

    @Test
    @DisplayName("constructor handles empty voice name")
    void shouldHandleEmptyVoiceName() {
        RuntimeSettingsService defaultSettings = mock(RuntimeSettingsService.class);
        when(defaultSettings.getVoiceName()).thenReturn("");
        when(defaultSettings.getVoiceSpeed()).thenReturn(1.0);
        
        SystemTextToSpeechService defaultService = new SystemTextToSpeechService(defaultSettings);

        assertThat(defaultService.getName())
                .startsWith("system-");
    }
}
