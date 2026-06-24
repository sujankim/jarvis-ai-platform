package ai.jarvis.voice;

import ai.jarvis.voice.exception.VoiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("rawtypes")
@ExtendWith(MockitoExtension.class)
@DisplayName("WhisperTranscriptionService Tests")
class WhisperTranscriptionServiceTest {

    // ── WebClient chain mocks ─────────────────────
    // NOTE: Raw types used intentionally.
    // Mockito cannot resolve thenReturn() with
    // wildcard generics (RequestHeadersUriSpec<?>).
    // @SuppressWarnings("rawtypes") on class suppresses
    // the warning for all raw type usage below.

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec
            requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec
            requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec
            requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec
            requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    // ── Service instances ─────────────────────────

    private WhisperTranscriptionService serviceWithKey;
    private WhisperTranscriptionService serviceWithoutKey;

    // ── Constants ─────────────────────────────────

    private static final String BASE_URL =
            "https://api.groq.com/openai/v1";
    private static final String FAKE_KEY =
            "gsk_fake_test_key_12345";
    private static final String MODEL =
            "whisper-large-v3-turbo";

    @BeforeEach
    void setUp() {
        // Wire builder BEFORE creating services
        when(webClientBuilder.baseUrl(anyString()))
                .thenReturn(webClientBuilder);
        when(webClientBuilder.build())
                .thenReturn(webClient);

        // Service WITH key configured
        serviceWithKey =
                new WhisperTranscriptionService(
                        webClientBuilder,
                        BASE_URL,
                        FAKE_KEY,
                        MODEL);

        // Service WITHOUT key — not configured
        serviceWithoutKey =
                new WhisperTranscriptionService(
                        webClientBuilder,
                        BASE_URL,
                        "",
                        MODEL);
    }

    // ── Guard tests ───────────────────────────────

    @Test
    @DisplayName("transcribe() fails with EMPTY_AUDIO for null bytes")
    void shouldFailForNullAudio() {
        StepVerifier
                .create(serviceWithKey
                        .transcribe(null))
                .expectErrorMatches(error ->
                        error instanceof VoiceException ve
                                && ve.getErrorCode()
                                .equals("EMPTY_AUDIO"))
                .verify();
    }

    @Test
    @DisplayName("transcribe() fails with EMPTY_AUDIO for empty bytes")
    void shouldFailForEmptyAudio() {
        StepVerifier
                .create(serviceWithKey
                        .transcribe(new byte[0]))
                .expectErrorMatches(error ->
                        error instanceof VoiceException ve
                                && ve.getErrorCode()
                                .equals("EMPTY_AUDIO"))
                .verify();
    }

    @Test
    @DisplayName("transcribe() fails when no key configured")
    void shouldFailWhenNotConfigured() {
        StepVerifier
                .create(serviceWithoutKey
                        .transcribe(
                                new byte[]{1, 2, 3}))
                .expectErrorMatches(error ->
                        error instanceof VoiceException ve
                                && ve.getErrorCode()
                                .equals(
                                        "WHISPER_NOT_CONFIGURED"))
                .verify();
    }

    @Test
    @DisplayName("transcribe() with language fails when not configured")
    void shouldFailWithLanguageWhenNotConfigured() {
        StepVerifier
                .create(serviceWithoutKey
                        .transcribe(
                                new byte[]{1, 2, 3},
                                "en"))
                .expectErrorMatches(error ->
                        error instanceof VoiceException)
                .verify();
    }

    @Test
    @DisplayName("transcribe() null language accepted (auto-detect)")
    void shouldAcceptNullLanguage() {
        StepVerifier
                .create(serviceWithKey
                        .transcribe(null, null))
                .expectErrorMatches(error ->
                        error instanceof VoiceException ve
                                && ve.getErrorCode()
                                .equals("EMPTY_AUDIO"))
                .verify();
    }

    // ── Success tests ─────────────────────────────

    @Test
    @DisplayName("transcribe() returns text on success")
    void shouldReturnTranscribedText() {
        stubPostChain(Mono.just("Hello from Jarvis"));

        StepVerifier
                .create(serviceWithKey
                        .transcribe(
                                new byte[]{1, 2, 3}))
                .expectNext("Hello from Jarvis")
                .verifyComplete();
    }

    @Test
    @DisplayName("transcribe() trims whitespace from response")
    void shouldTrimTranscribedText() {
        // Whisper API often returns trailing newlines
        stubPostChain(Mono.just(
                "  Hello Jarvis  \n"));

        StepVerifier
                .create(serviceWithKey
                        .transcribe(
                                new byte[]{1, 2, 3}))
                .expectNext("Hello Jarvis")
                .verifyComplete();
    }

    @Test
    @DisplayName("transcribe() maps API error to TRANSCRIPTION_FAILED")
    void shouldMapApiErrorToVoiceException() {
        stubPostChain(Mono.error(
                new RuntimeException(
                        "API unavailable")));

        StepVerifier
                .create(serviceWithKey
                        .transcribe(
                                new byte[]{1, 2, 3}))
                .expectErrorMatches(error ->
                        error instanceof VoiceException ve
                                && ve.getErrorCode()
                                .equals(
                                        "TRANSCRIPTION_FAILED"))
                .verify();
    }

    // ── isAvailable() tests ───────────────────────

    @Test
    @DisplayName("isAvailable() returns false when not configured")
    void shouldReturnFalseWhenNotConfigured() {
        StepVerifier
                .create(serviceWithoutKey
                        .isAvailable())
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isAvailable() returns true when API responds")
    void shouldReturnTrueWhenApiResponds() {
        stubGetChain(Mono.just(
                "{\"object\":\"list\"}"));

        StepVerifier
                .create(serviceWithKey.isAvailable())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("isAvailable() returns false on connection error")
    void shouldReturnFalseOnConnectionError() {
        stubGetChain(Mono.error(
                new RuntimeException(
                        "Connection refused")));

        StepVerifier
                .create(serviceWithKey.isAvailable())
                .expectNext(false)
                .verifyComplete();
    }

    // ── getMode() tests ───────────────────────────

    @Test
    @DisplayName("getMode() returns cloud for cloud URL")
    void shouldReturnCloudModeForCloudUrl() {
        assertThat(serviceWithKey.getMode())
                .isEqualTo("groq-cloud");
    }

    @Test
    @DisplayName("getMode() returns not-configured when no key")
    void shouldReturnNotConfiguredWhenNoKey() {
        assertThat(serviceWithoutKey.getMode())
                .isEqualTo("not-configured");
    }

    @Test
    @DisplayName("getMode() returns local for local URL")
    void shouldReturnLocalModeForLocalUrl() {
        WhisperTranscriptionService localService =
                new WhisperTranscriptionService(
                        webClientBuilder,
                        "http://localhost:8178",
                        "not-needed",
                        "whisper-1");

        assertThat(localService.getMode())
                .isEqualTo("local-whisper");
    }

    // ── Private helpers ───────────────────────────

    /**
     * Stub POST chain for transcription calls.
     * Extracted to avoid repeating 6-line chain
     * in every test method.
     */
    @SuppressWarnings("unchecked")
    private void stubPostChain(
            Mono<String> responseBody) {
        when(webClient.post())
                .thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec
                .header(anyString(), anyString()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any()))
                .thenReturn(requestBodySpec);
        when(requestBodySpec.body(any()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(responseBody);
    }

    /**
     * Stub GET chain for isAvailable() calls.
     * Extracted helper — same reason as stubPostChain.
     */
    @SuppressWarnings("unchecked")
    private void stubGetChain(
            Mono<String> responseBody) {
        when(webClient.get())
                .thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec
                .header(anyString(), anyString()))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve())
                .thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(responseBody);
    }
}