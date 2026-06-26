package ai.jarvis.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemTextToSpeechService Tests")
class SystemTextToSpeechServiceTest {

    private SystemTextToSpeechService service;

    @BeforeEach
    void setUp() {
        // FIX: Constructor now requires 2 params
        // since voice selection was added.
        // voiceName="" = system default voice
        // voiceSpeed=1.0 = normal speed
        service = new SystemTextToSpeechService(
                "", 1.0);
    }

    @Test
    @DisplayName("speak() returns empty bytes for null text")
    void shouldReturnEmptyForNullText() {
        StepVerifier
                .create(service.speak(null))
                .expectNextMatches(
                        bytes -> bytes.length == 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("speak() returns empty bytes for blank text")
    void shouldReturnEmptyForBlankText() {
        StepVerifier
                .create(service.speak("   "))
                .expectNextMatches(
                        bytes -> bytes.length == 0)
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
        // FIX: boolean primitive is never null
        // Just verify a value is emitted
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
        String os = System.getProperty("os.name")
                .toLowerCase();

        if (os.contains("win")) {
            assertThat(name)
                    .isEqualTo("system-windows");
        } else if (os.contains("mac")) {
            assertThat(name)
                    .isEqualTo("system-macos");
        } else {
            assertThat(name)
                    .isEqualTo("system-linux");
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

    // ── Voice selection tests ─────────────────────

    @Test
    @DisplayName("constructor accepts custom voice name")
    void shouldAcceptCustomVoiceName() {
        SystemTextToSpeechService customService =
                new SystemTextToSpeechService(
                        "Microsoft Zira Desktop",
                        1.0);

        assertThat(customService.getName())
                .isNotBlank()
                .startsWith("system-");
    }

    @Test
    @DisplayName("constructor accepts custom speed")
    void shouldAcceptCustomSpeed() {
        SystemTextToSpeechService fastService =
                new SystemTextToSpeechService(
                        "", 1.5);

        assertThat(fastService.getName())
                .isNotBlank();
    }

    @Test
    @DisplayName("constructor handles empty voice name")
    void shouldHandleEmptyVoiceName() {
        SystemTextToSpeechService defaultService =
                new SystemTextToSpeechService(
                        "", 1.0);

        // Should use system default voice
        assertThat(defaultService.getName())
                .startsWith("system-");
    }
}