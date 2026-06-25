package ai.jarvis.voice;

import ai.jarvis.ai.orchestrator.AiOrchestrator;
import ai.jarvis.ai.orchestrator.OrchestratorRequest;
import ai.jarvis.voice.exception.VoiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoiceConversationService Tests")
class VoiceConversationServiceTest {

    @Mock
    private WhisperTranscriptionService
            transcriptionService;

    @Mock
    private TextToSpeechService textToSpeechService;

    @Mock
    private AiOrchestrator orchestrator;

    private VoiceConversationService service;

    private final UUID userId =
            UUID.randomUUID();
    private final UUID sessionId =
            UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoiceConversationService(
                transcriptionService,
                textToSpeechService,
                orchestrator);
    }

    // ── voiceChat() tests ─────────────────────────

    @Test
    @DisplayName("voiceChat() transcribes then calls AI")
    void shouldTranscribeThenCallAi() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService
                .transcribe(audio))
                .thenReturn(Mono.just(
                        "Hello Jarvis"));

        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(
                        Flux.just("Hi ", "there!"));

        when(textToSpeechService
                .speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectNext("Hi ")
                .expectNext("there!")
                .verifyComplete();

        verify(transcriptionService)
                .transcribe(audio);
        verify(orchestrator)
                .chat(any(OrchestratorRequest.class));
    }

    @Test
    @DisplayName("voiceChat() fails when transcription fails")
    void shouldFailWhenTranscriptionFails() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService
                .transcribe(audio))
                .thenReturn(Mono.error(
                        VoiceException.emptyAudio()));

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectError(VoiceException.class)
                .verify();
    }

    @Test
    @DisplayName("voiceChat() passes text to orchestrator")
    void shouldPassTranscribedTextToOrchestrator() {
        byte[] audio = new byte[]{1, 2, 3};
        String transcribed = "What time is it?";

        when(transcriptionService
                .transcribe(audio))
                .thenReturn(Mono.just(transcribed));

        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(
                        Flux.just("It is 3 PM."));

        when(textToSpeechService
                .speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectNext("It is 3 PM.")
                .verifyComplete();
    }

    // ── transcribeOnly() tests ────────────────────

    @Test
    @DisplayName("transcribeOnly() returns transcribed text")
    void shouldTranscribeOnly() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService
                .transcribe(audio))
                .thenReturn(Mono.just(
                        "Test transcription"));

        StepVerifier
                .create(service
                        .transcribeOnly(audio))
                .expectNext("Test transcription")
                .verifyComplete();
    }

    // ── speakText() tests ─────────────────────────

    @Test
    @DisplayName("speakText() delegates to TTS service")
    void shouldDelegateToTtsService() {
        when(textToSpeechService
                .speakAndPlay("Hello"))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.speakText("Hello"))
                .verifyComplete();

        verify(textToSpeechService)
                .speakAndPlay("Hello");
    }

    // ── isVoiceAvailable() tests ──────────────────

    @Test
    @DisplayName("isVoiceAvailable() true when both available")
    void shouldReturnTrueWhenBothAvailable() {
        when(transcriptionService.isAvailable())
                .thenReturn(Mono.just(true));
        when(textToSpeechService.isAvailable())
                .thenReturn(Mono.just(true));

        StepVerifier
                .create(service.isVoiceAvailable())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("isVoiceAvailable() false when TTS unavailable")
    void shouldReturnFalseWhenTtsUnavailable() {
        when(transcriptionService.isAvailable())
                .thenReturn(Mono.just(true));
        when(textToSpeechService.isAvailable())
                .thenReturn(Mono.just(false));

        StepVerifier
                .create(service.isVoiceAvailable())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isVoiceAvailable() false when transcription unavailable")
    void shouldReturnFalseWhenTranscriptionUnavailable() {
        when(transcriptionService.isAvailable())
                .thenReturn(Mono.just(false));
        when(textToSpeechService.isAvailable())
                .thenReturn(Mono.just(true));

        StepVerifier
                .create(service.isVoiceAvailable())
                .expectNext(false)
                .verifyComplete();
    }
}