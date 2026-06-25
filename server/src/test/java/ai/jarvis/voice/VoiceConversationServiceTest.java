package ai.jarvis.voice;

import ai.jarvis.ai.orchestrator.AiOrchestrator;
import ai.jarvis.ai.orchestrator.OrchestratorRequest;
import ai.jarvis.voice.VoiceConversationService.VoiceChatEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    private final UUID userId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoiceConversationService(
                transcriptionService,
                textToSpeechService,
                orchestrator);
    }

    // ── voiceChat() SESSION event tests ───────────

    @Test
    @DisplayName("voiceChat() first event is SESSION with session ID")
    void shouldEmitSessionEventFirst() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello Jarvis"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi there."));
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION
                                && event.data().equals(
                                sessionId.toString()))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN)
                .verifyComplete();
    }

    @Test
    @DisplayName("voiceChat() generates new session ID when null")
    void shouldGenerateNewSessionIdWhenNull() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi."));
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio,
                        null,  // ← no session
                        userId, "dravin", "USER"))
                .expectNextMatches(event -> {
                    // SESSION event must have a valid UUID
                    if (event.type() !=
                            VoiceChatEvent.EventType.SESSION) {
                        return false;
                    }
                    try {
                        UUID generated =
                                UUID.fromString(event.data());
                        // Must be a NEW UUID (not null, not zero)
                        return generated != null;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("voiceChat() SESSION event uses provided session ID")
    void shouldUseProvidedSessionId() {
        byte[] audio = new byte[]{1, 2, 3};
        UUID existingSession = UUID.randomUUID();

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi."));
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, existingSession,
                        userId, "dravin", "USER"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION
                                && event.data().equals(
                                existingSession.toString()))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ── voiceChat() token tests ───────────────────

    @Test
    @DisplayName("voiceChat() transcribes then calls AI")
    void shouldTranscribeThenCallAi() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello Jarvis"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi there."));
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                // First: SESSION
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION)
                // Then: TOKEN with AI text
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN
                                && event.data()
                                .contains("Hi there"))
                .verifyComplete();

        verify(transcriptionService).transcribe(audio);
        verify(orchestrator).chat(
                any(OrchestratorRequest.class));
    }

    @Test
    @DisplayName("voiceChat() TOKEN events contain AI response text")
    void shouldEmitTokenEventsWithAiText() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("What time is it?"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("It is 3 PM."));
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                // Skip SESSION
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION)
                // TOKEN has text content
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN
                                && event.data()
                                .contains("It is 3 PM"))
                .verifyComplete();
    }

    @Test
    @DisplayName("voiceChat() fails when transcription fails")
    void shouldFailWhenTranscriptionFails() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.error(
                        VoiceException.emptyAudio()));

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectError(VoiceException.class)
                .verify();

        // AI must NOT be called
        verify(orchestrator, never())
                .chat(any(OrchestratorRequest.class));
    }

    @Test
    @DisplayName("voiceChat() TTS failure does not stop stream")
    void shouldContinueStreamWhenTtsFails() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello!"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi there."));

        // TTS fails but stream should continue
        when(textToSpeechService.speakAndPlay(any()))
                .thenReturn(Mono.error(
                        new RuntimeException("TTS error")));

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                // SESSION still emitted
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION)
                // TOKEN still emitted despite TTS failure
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN)
                .verifyComplete();
    }

    // ── VoiceChatEvent record tests ───────────────

    @Test
    @DisplayName("VoiceChatEvent.session() creates SESSION event")
    void shouldCreateSessionEvent() {
        UUID id = UUID.randomUUID();
        VoiceChatEvent event =
                VoiceChatEvent.session(id);

        assertThat(event.type())
                .isEqualTo(
                        VoiceChatEvent.EventType.SESSION);
        assertThat(event.data())
                .isEqualTo(id.toString());
    }

    @Test
    @DisplayName("VoiceChatEvent.token() creates TOKEN event")
    void shouldCreateTokenEvent() {
        VoiceChatEvent event =
                VoiceChatEvent.token("Hello Jarvis");

        assertThat(event.type())
                .isEqualTo(
                        VoiceChatEvent.EventType.TOKEN);
        assertThat(event.data())
                .isEqualTo("Hello Jarvis");
    }

    @Test
    @DisplayName("VoiceChatEvent.done() creates DONE event")
    void shouldCreateDoneEvent() {
        VoiceChatEvent event = VoiceChatEvent.done();

        assertThat(event.type())
                .isEqualTo(
                        VoiceChatEvent.EventType.DONE);
        assertThat(event.data())
                .isEqualTo("[DONE]");
    }

    // ── transcribeOnly() tests ────────────────────

    @Test
    @DisplayName("transcribeOnly() delegates to transcription service")
    void shouldDelegateTranscription() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just(
                        "Test transcription"));

        StepVerifier
                .create(service.transcribeOnly(audio))
                .expectNext("Test transcription")
                .verifyComplete();

        verify(transcriptionService).transcribe(audio);
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

    @Test
    @DisplayName("isTranscriptionAvailable() delegates correctly")
    void shouldDelegateTranscriptionAvailability() {
        when(transcriptionService.isAvailable())
                .thenReturn(Mono.just(true));

        StepVerifier
                .create(service
                        .isTranscriptionAvailable())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTtsAvailable() delegates correctly")
    void shouldDelegateTtsAvailability() {
        when(textToSpeechService.isAvailable())
                .thenReturn(Mono.just(false));

        StepVerifier
                .create(service.isTtsAvailable())
                .expectNext(false)
                .verifyComplete();
    }
}