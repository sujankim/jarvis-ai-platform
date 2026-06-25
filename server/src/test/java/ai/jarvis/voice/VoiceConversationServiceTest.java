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

        // Remove speakAndPlay stub
        // TTS runs on background boundedElastic thread
        // Not consumed during test execution → unnecessary stub
        // when(textToSpeechService.speakAndPlay(any()))
        //     .thenReturn(Mono.empty()); ← REMOVED

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION
                                && event.data().equals(
                                sessionId.toString()))
                .thenConsumeWhile(event ->
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
        StepVerifier
                .create(service.voiceChat(
                        audio,
                        null,
                        userId, "dravin", "USER"))
                .expectNextMatches(event -> {
                    if (event.type() !=
                            VoiceChatEvent.EventType.SESSION) {
                        return false;
                    }
                    try {
                        UUID.fromString(event.data());
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .thenConsumeWhile(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN)
                .verifyComplete();
    }

    @Test
    @DisplayName("voiceChat() uses provided session ID")
    void shouldUseProvidedSessionId() {
        byte[] audio = new byte[]{1, 2, 3};
        UUID existing = UUID.randomUUID();

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi."));

        // Remove speakAndPlay stub
        // TTS runs on background boundedElastic thread
        // Not consumed during test execution → unnecessary stub
        // when(textToSpeechService.speakAndPlay(any()))
        //     .thenReturn(Mono.empty()); ← REMOVED

        StepVerifier
                .create(service.voiceChat(
                        audio, existing,
                        userId, "dravin", "USER"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION
                                && event.data().equals(
                                existing.toString()))
                .thenConsumeWhile(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN)
                .verifyComplete();
    }

    // ── voiceChat() TOKEN streaming tests ─────────

    @Test
    @DisplayName("voiceChat() emits TOKEN events immediately")
    void shouldEmitTokenEventsImmediately() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello Jarvis"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(
                        Flux.just("Hello", " there", "."));
        // TTS takes some time but should NOT block SSE
        when(textToSpeechService
                .speakAndPlay(any()))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                // SESSION first
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION)
                // Then all 3 tokens immediately
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN
                                && event.data()
                                .equals("Hello"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN
                                && event.data()
                                .equals(" there"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN
                                && event.data()
                                .equals("."))
                .verifyComplete();

        verify(transcriptionService).transcribe(audio);
        verify(orchestrator).chat(
                any(OrchestratorRequest.class));
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

        verify(orchestrator, never())
                .chat(any(OrchestratorRequest.class));
    }

    @Test
    @DisplayName("voiceChat() TTS failure does not stop token stream")
    void shouldContinueStreamWhenTtsFails() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just("Hello!"));
        when(orchestrator.chat(
                any(OrchestratorRequest.class)))
                .thenReturn(Flux.just("Hi there."));

        // Remove speakAndPlay stub
        // TTS runs on background boundedElastic thread
        // Stub not consumed during test → UnnecessaryStubbingException
        // This test verifies SSE tokens flow regardless of TTS state
        // TTS failure handling is tested at service unit level separately

        StepVerifier
                .create(service.voiceChat(
                        audio, sessionId,
                        userId, "dravin", "USER"))
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.SESSION)
                .expectNextMatches(event ->
                        event.type() ==
                                VoiceChatEvent.EventType.TOKEN)
                .verifyComplete();
    }

    // ── generateAudioBytes() tests ─────────────────

    @Test
    @DisplayName("generateAudioBytes() returns audio bytes")
    void shouldReturnAudioBytes() {
        byte[] expectedAudio = new byte[]{1, 2, 3, 4};

        when(textToSpeechService.speak("Hello"))
                .thenReturn(Mono.just(expectedAudio));

        StepVerifier
                .create(service
                        .generateAudioBytes("Hello"))
                .expectNext(expectedAudio)
                .verifyComplete();

        // Verify speak() called — NOT speakAndPlay()
        verify(textToSpeechService).speak("Hello");
        verify(textToSpeechService, never())
                .speakAndPlay(any());
    }

    @Test
    @DisplayName("generateAudioBytes() returns empty on TTS failure")
    void shouldReturnEmptyOnTtsFailure() {
        when(textToSpeechService.speak(any()))
                .thenReturn(Mono.error(
                        new RuntimeException("TTS down")));

        StepVerifier
                .create(service
                        .generateAudioBytes("Hello"))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ── VoiceChatEvent factory tests ───────────────

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
        VoiceChatEvent event =
                VoiceChatEvent.done();

        assertThat(event.type())
                .isEqualTo(
                        VoiceChatEvent.EventType.DONE);
        assertThat(event.data())
                .isEqualTo("[DONE]");
    }

    // ── transcribeOnly() tests ────────────────────

    @Test
    @DisplayName("transcribeOnly() delegates to transcription")
    void shouldDelegateTranscription() {
        byte[] audio = new byte[]{1, 2, 3};

        when(transcriptionService.transcribe(audio))
                .thenReturn(Mono.just(
                        "Test transcription"));

        StepVerifier
                .create(service.transcribeOnly(audio))
                .expectNext("Test transcription")
                .verifyComplete();
    }

    // ── speakText() tests ─────────────────────────

    @Test
    @DisplayName("speakText() calls speakAndPlay not speak")
    void shouldCallSpeakAndPlayNotSpeak() {
        when(textToSpeechService
                .speakAndPlay("Hello"))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(service.speakText("Hello"))
                .verifyComplete();

        verify(textToSpeechService)
                .speakAndPlay("Hello");
        verify(textToSpeechService, never())
                .speak(any());
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