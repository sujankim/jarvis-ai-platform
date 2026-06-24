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
        service = new SystemTextToSpeechService();
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
        StepVerifier
                .create(service.isAvailable())
                .expectNextMatches(
                        available -> available != null)
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
        // Even if TTS fails, returns empty bytes
        // not an exception
        StepVerifier
                .create(service.speak("test"))
                .expectNextCount(1)
                .verifyComplete();
    }
}