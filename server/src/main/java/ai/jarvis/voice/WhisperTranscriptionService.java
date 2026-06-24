package ai.jarvis.voice;

import ai.jarvis.voice.exception.VoiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * Transcribes audio to text using Whisper.
 *
 * Ollama does NOT support Whisper natively.
 * Use one of two modes:
 *
 * MODE 1 — Groq API (cloud, free tier):
 *   Set GROQ_API_KEY in .env
 *   Free: 6000 requests/day
 *   URL: api.groq.com/openai/v1
 *
 * MODE 2 — Local whisper.cpp server:
 *   No API key required
 *   URL: http://localhost:8178
 *   Setup: github.com/ggerganov/whisper.cpp
 *
 *
 * 1. Added timeouts: 30s transcription, 5s health check
 * 2. Local mode works without API key
 *    isLocalMode=true when URL contains localhost
 * 3. Separate code paths for local vs cloud
 *    No incompatible WebClient type casting
 */
@Slf4j
@Service
public class WhisperTranscriptionService {

    private static final String TRANSCRIPTION_PATH =
            "/audio/transcriptions";

    private static final Duration TRANSCRIPTION_TIMEOUT =
            Duration.ofSeconds(30);

    private static final Duration HEALTH_TIMEOUT =
            Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final boolean isGroq;
    private final boolean isLocalMode;

    public WhisperTranscriptionService(
            WebClient.Builder webClientBuilder,
            @Value("${jarvis.voice.whisper.base-url:"
                    + "https://api.groq.com/openai/v1}")
            String baseUrl,
            @Value("${jarvis.voice.whisper.api-key:}")
            String apiKey,
            @Value("${jarvis.voice.whisper.model:"
                    + "whisper-large-v3-turbo}")
            String model) {

        this.apiKey = apiKey;
        this.model = model;
        this.isGroq = baseUrl.contains("groq.com");
        this.isLocalMode =
                baseUrl.contains("localhost")
                        || baseUrl.contains("127.0.0.1");

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();

        if (isConfigured()) {
            log.info(
                    "WhisperTranscriptionService: "
                            + "mode={} model={}",
                    getMode(), model);
        } else {
            log.warn(
                    "WhisperTranscriptionService: "
                            + "not configured. "
                            + "Set GROQ_API_KEY for cloud "
                            + "or configure local "
                            + "whisper.cpp server.");
        }
    }

    /**
     * Transcribe audio bytes to text.
     *
     * @param audioBytes raw audio file bytes
     * @return Mono<String> transcribed text
     */
    public Mono<String> transcribe(byte[] audioBytes) {
        return transcribe(audioBytes, null);
    }

    /**
     * Transcribe with explicit language hint.
     *
     * @param audioBytes raw audio file bytes
     * @param language   ISO code ("en", "ne") or null
     * @return Mono<String> transcribed text
     */
    public Mono<String> transcribe(
            byte[] audioBytes,
            String language) {

        if (audioBytes == null
                || audioBytes.length == 0) {
            return Mono.error(
                    VoiceException.emptyAudio());
        }

        if (!isConfigured()) {
            return Mono.error(
                    new VoiceException(
                            "WHISPER_NOT_CONFIGURED",
                            "Whisper not configured. "
                                    + "Set GROQ_API_KEY "
                                    + "in .env for cloud, "
                                    + "or start local "
                                    + "whisper.cpp server.",
                            org.springframework.http
                                    .HttpStatus
                                    .SERVICE_UNAVAILABLE));
        }

        log.debug(
                "Transcribing: {}KB language={}",
                audioBytes.length / 1024,
                language != null ? language : "auto");

        return Mono.fromCallable(() ->
                        callWhisperApi(
                                audioBytes, language))
                .subscribeOn(
                        Schedulers.boundedElastic())
                .doOnSuccess(text ->
                        log.info(
                                "Transcribed: {} chars",
                                text != null
                                        ? text.length()
                                        : 0))
                .onErrorMap(error -> {
                    log.error(
                            "Transcription failed: {}",
                            error.getMessage());
                    return VoiceException
                            .transcriptionFailed(
                                    error.getMessage());
                });
    }

    /**
     * Check if Whisper is configured and reachable.
     *
     * Separate code paths for local vs cloud.
     * No type casting between incompatible WebClient
     * spec types (RequestHeadersSpec vs RequestBodySpec).
     *
     * @return Mono<Boolean> true if ready to transcribe
     */
    public Mono<Boolean> isAvailable() {
        if (!isConfigured()) {
            return Mono.just(false);
        }

        // FIX: Separate paths — no variable reassignment
        // Local mode: no Authorization header needed
        // Cloud mode: Bearer token required
        if (isLocalMode) {
            return webClient
                    .get()
                    .uri("/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(HEALTH_TIMEOUT)
                    .map(response -> true)
                    .onErrorReturn(false);
        }

        return webClient
                .get()
                .uri("/models")
                .header(
                        "Authorization",
                        "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HEALTH_TIMEOUT)
                .map(response -> true)
                .onErrorReturn(false);
    }

    /**
     * Get current mode name for health checks + logs.
     *
     * @return mode identifier string
     */
    public String getMode() {
        if (!isConfigured()) {
            return "not-configured";
        }
        if (isLocalMode) {
            return "local-whisper";
        }
        return isGroq ? "groq-cloud" : "remote-whisper";
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Local mode: no key needed (whisper.cpp).
     * Cloud mode: key required (Groq or OpenAI).
     *
     * @return true if ready to make API calls
     */
    private boolean isConfigured() {
        if (isLocalMode) {
            return true;
        }
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Call Whisper via OpenAI-compatible multipart API.
     * Works with Groq API and whisper.cpp server.
     *
     * Separate code paths for local vs cloud.
     * Added TRANSCRIPTION_TIMEOUT before .block()
     *      to prevent thread starvation.
     *
     * @param audioBytes raw audio bytes
     * @param language   optional language hint (nullable)
     * @return transcribed text string
     */
    private String callWhisperApi(
            byte[] audioBytes,
            String language) {

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder();

        bodyBuilder.part("file",
                new ByteArrayResource(audioBytes) {
                    @Override
                    public String getFilename() {
                        return "audio.wav";
                    }
                });

        bodyBuilder.part("model", model);
        bodyBuilder.part("response_format", "text");

        if (language != null
                && !language.isBlank()) {
            bodyBuilder.part("language", language);
        }

        // FIX: Separate paths — no incompatible casting
        // Local whisper.cpp: no auth header
        // Cloud (Groq): Bearer token required
        Mono<String> responseMono;

        if (isLocalMode) {
            responseMono = webClient
                    .post()
                    .uri(TRANSCRIPTION_PATH)
                    .contentType(
                            MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters
                            .fromMultipartData(
                                    bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TRANSCRIPTION_TIMEOUT);
        } else {
            responseMono = webClient
                    .post()
                    .uri(TRANSCRIPTION_PATH)
                    .header(
                            "Authorization",
                            "Bearer " + apiKey)
                    .contentType(
                            MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters
                            .fromMultipartData(
                                    bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TRANSCRIPTION_TIMEOUT);
        }

        String response = responseMono.block();

        if (response == null
                || response.isBlank()) {
            throw new RuntimeException(
                    "Empty response from Whisper");
        }

        return response.trim();
    }
}