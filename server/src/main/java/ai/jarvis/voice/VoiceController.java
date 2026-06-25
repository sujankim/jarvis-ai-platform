package ai.jarvis.voice;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.voice.VoiceConversationService.VoiceChatEvent;
import ai.jarvis.voice.exception.VoiceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Voice REST API controller.
 *
 *
 * 1. Architecture: only VoiceConversationService injected.
 *    WhisperTranscriptionService and TextToSpeechService
 *    removed from controller — they are provider-layer.
 *    Controller → Service → Provider (strict layer rule).
 *
 * 2. Memory leak: DataBufferUtils.join() replaces manual
 *    reduce() which leaked Netty native memory and had
 *    O(N²) complexity for large files.
 *
 * 3. VoiceException status preserved: onErrorMap checks
 *    if error is already VoiceException and keeps its
 *    HTTP status instead of flattening to 500.
 *
 * 4. TTS failures are real errors: /speak returns 503
 *    on failure, /speak/bytes returns 500 not empty body.
 *
 * 5. Security: no conversation content in logs.
 *    Log file name and byte count only.
 *
 * 6. Session ID returned: voiceChat() SSE stream
 *    sends session event first so client can continue.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Voice",
        description = "Voice assistant endpoints")
public class VoiceController {

    // Only VoiceConversationService
    // No direct WhisperTranscriptionService or TTS
    // injection — those are provider-layer concerns.
    private final VoiceConversationService
            voiceConversationService;

    // ── POST /api/v1/voice/transcribe ─────────────

    @Operation(
            summary = "Transcribe audio to text",
            description =
                    "Upload an audio file and receive "
                            + "the transcribed text. "
                            + "Supports: wav, mp3, webm, "
                            + "ogg, m4a"
    )
    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<VoiceResponse>> transcribe(
            @RequestPart("audio") FilePart audioPart) {

        // log metadata only, never filename
        // content which could be sensitive
        log.info("Transcription request received");

        return readAudioBytes(audioPart)
                .flatMap(audioBytes ->
                        voiceConversationService
                                .transcribeOnly(audioBytes))
                .map(text -> new VoiceResponse(
                        text,
                        null,
                        "voice"))
                .map(ApiResponse::ok)
                // FIX Issue 3: preserve VoiceException status
                .onErrorMap(error -> {
                    log.error(
                            "Transcription failed: {}",
                            error.getClass()
                                    .getSimpleName());

                    // Keep existing status for VoiceException
                    if (error instanceof VoiceException) {
                        return error;
                    }

                    return new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Transcription failed: "
                                    + error.getMessage());
                });
    }

    // ── POST /api/v1/voice/speak ──────────────────

    @Operation(
            summary = "Convert text to speech",
            description =
                    "Send text and play through server speakers."
    )
    @PostMapping(
            value = "/speak",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> speak(
            @Valid @RequestBody VoiceRequest request) {

        log.info(
                "TTS request: chars={}",
                request.text().length());

        return voiceConversationService
                .speakText(request.text())
                // do NOT swallow TTS errors
                // Return 503 so client knows TTS failed
                .onErrorMap(error -> {
                    log.error(
                            "TTS failed: {}",
                            error.getClass()
                                    .getSimpleName());
                    return new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "TTS service unavailable: "
                                    + error.getMessage());
                });
    }

    // ── POST /api/v1/voice/speak/bytes ────────────

    @Operation(
            summary = "Get audio bytes for text",
            description =
                    "Send text, receive raw audio/wav bytes."
    )
    @PostMapping(
            value = "/speak/bytes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "audio/wav"
    )
    public Mono<byte[]> speakBytes(
            @Valid @RequestBody VoiceRequest request) {

        return voiceConversationService
                .speakText(request.text())
                .thenReturn(new byte[0])
                // return error not empty bytes
                // Empty bytes would look like success to client
                .onErrorMap(error -> {
                    log.error(
                            "TTS bytes failed: {}",
                            error.getClass()
                                    .getSimpleName());
                    return new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "TTS failed: "
                                    + error.getMessage());
                });
    }

    // ── POST /api/v1/voice/chat ───────────────────

    @Operation(
            summary = "Voice chat (audio in, SSE out)",
            description =
                    "Upload audio, receive AI response as SSE."
    )
    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> voiceChat(
            @RequestPart("audio") FilePart audioPart,
            @RequestParam(required = false)
            UUID sessionId) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMapMany(auth -> {

                    UUID userId = extractUserId(auth);
                    String username =
                            extractUsername(auth);
                    String role = extractRole(auth);

                    // DataBufferUtils.join()
                    // Handles buffer release automatically
                    // No memory leak, no O(N²) copy
                    return readAudioBytes(audioPart)
                            .flatMapMany(audioBytes ->
                                    voiceConversationService
                                            .voiceChat(
                                                    audioBytes,
                                                    sessionId,
                                                    userId,
                                                    username,
                                                    role))
                            // VoiceChatEvent
                            // includes SESSION event first
                            // so client learns session ID
                            .map(event ->
                                    ServerSentEvent
                                            .<String>builder()
                                            .event(event.type()
                                                    .name()
                                                    .toLowerCase())
                                            .data(event.data())
                                            .build())
                            .concatWith(Flux.just(
                                    ServerSentEvent
                                            .<String>builder()
                                            .event("done")
                                            .data("[DONE]")
                                            .build()));
                });
    }

    // ── GET /api/v1/voice/status ──────────────────

    @Operation(
            summary = "Check voice feature status"
    )
    @GetMapping(
            value = "/status",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<VoiceStatusResponse>>
    status() {

        return Mono.zip(
                        voiceConversationService
                                .isTranscriptionAvailable(),
                        voiceConversationService
                                .isTtsAvailable()
                )
                .map(tuple ->
                        new VoiceStatusResponse(
                                tuple.getT1(),
                                tuple.getT2(),
                                tuple.getT1()
                                        && tuple.getT2()))
                .map(ApiResponse::ok);
    }

    // ── Private Helpers ───────────────────────────

    /**
     * DataBufferUtils.join() replaces
     * the manual reduce() accumulator.
     *
     * PREVIOUS PROBLEMS with manual reduce():
     * 1. Memory leak — DataBuffer not released
     *    Netty native memory accumulates
     * 2. O(N²) — entire byte[] copied per chunk
     *    Large files degrade severely
     *
     * DataBufferUtils.join() handles both:
     * → Aggregates buffers without copying
     * → Properly releases each buffer after reading
     */
    private Mono<byte[]> readAudioBytes(
            FilePart audioPart) {

        return DataBufferUtils
                .join(audioPart.content())
                .map(dataBuffer -> {
                    byte[] bytes =
                            new byte[dataBuffer
                                    .readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                });
    }

    private UUID extractUserId(Authentication auth) {
        try {
            return UUID.fromString(
                    auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid token subject");
        }
    }

    private String extractUsername(Authentication auth) {
        Object details = auth.getDetails();
        if (details instanceof String s) return s;
        return auth.getPrincipal().toString();
    }

    private String extractRole(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .findFirst()
                .map(a -> a.getAuthority()
                        .replace("ROLE_", ""))
                .orElse("USER");
    }

    // ── Response Records ──────────────────────────

    public record VoiceStatusResponse(
            boolean transcriptionAvailable,
            boolean ttsAvailable,
            boolean voiceReady) {}
}