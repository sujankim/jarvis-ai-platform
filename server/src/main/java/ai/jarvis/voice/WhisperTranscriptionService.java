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

/**
 * Transcribes audio to text using Whisper.
 *
 * IMPORTANT: Ollama does NOT support Whisper natively.
 * `ollama pull whisper` → "file does not exist"
 *
 * TWO MODES depending on configuration:
 *
 * MODE 1: Groq API (cloud, free tier)
 *   Set: GROQ_API_KEY in .env
 *   Free: 6000 requests/day on whisper-large-v3-turbo
 *   URL: api.groq.com/openai/v1/audio/transcriptions
 *   WHY: Zero local setup, fastest, OpenAI-compatible API
 *
 * MODE 2: Local whisper.cpp server
 *   Setup: whisper.cpp running on port 8178
 *   URL: http://localhost:8178/inference
 *   WHY: 100% local, no API key, full privacy
 *   Docs: github.com/ggerganov/whisper.cpp#http-server
 *
 * SPRING AI INTEGRATION:
 * Spring AI AudioTranscriptionModel uses OpenAI API format.
 * Both Groq + whisper.cpp speak OpenAI-compatible format.
 * We call via WebClient for direct control.
 *
 * INPUT:  byte[] audio (wav/mp3/webm/ogg/m4a)
 * OUTPUT: Mono<String> transcribed text
 */
@Slf4j
@Service
public class WhisperTranscriptionService {

    // OpenAI-compatible transcription endpoint
    private static final String TRANSCRIPTION_PATH =
            "/audio/transcriptions";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final boolean isGroq;

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

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();

        if (apiKey != null && !apiKey.isBlank()) {
            log.info(
                    "WhisperTranscriptionService: "
                            + "mode={} model={}",
                    isGroq ? "groq-cloud"
                            : "local-server",
                    model);
        } else {
            log.warn(
                    "WhisperTranscriptionService: "
                            + "no API key configured. "
                            + "Set GROQ_API_KEY in .env "
                            + "OR point to local "
                            + "whisper.cpp server.");
        }
    }

    /**
     * Transcribe audio bytes to text.
     *
     * Uses OpenAI-compatible multipart API.
     * Works with: Groq API + whisper.cpp server.
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
                                    + "Set GROQ_API_KEY in .env "
                                    + "for cloud transcription, "
                                    + "or set up local "
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
     * @return true if ready to transcribe
     */
    public Mono<Boolean> isAvailable() {
        if (!isConfigured()) {
            return Mono.just(false);
        }

        // Quick health check
        return webClient
                .get()
                .uri("/models")
                .header("Authorization",
                        "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    /**
     * Get current mode name.
     * Used for health checks + logging.
     */
    public String getMode() {
        if (!isConfigured()) {
            return "not-configured";
        }
        return isGroq ? "groq-cloud" : "local-whisper";
    }

    // ── Private Helpers ───────────────────────────

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Call Whisper API via OpenAI-compatible multipart.
     * Works with Groq API + whisper.cpp server.
     */
    private String callWhisperApi(
            byte[] audioBytes,
            String language) {

        MultipartBodyBuilder bodyBuilder =
                new MultipartBodyBuilder();

        // Audio file as multipart
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

        String response = webClient
                .post()
                .uri(TRANSCRIPTION_PATH)
                .header("Authorization",
                        "Bearer " + apiKey)
                .contentType(
                        MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(
                        bodyBuilder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (response == null
                || response.isBlank()) {
            throw new RuntimeException(
                    "Empty response from "
                            + "Whisper API");
        }

        return response.trim();
    }
}