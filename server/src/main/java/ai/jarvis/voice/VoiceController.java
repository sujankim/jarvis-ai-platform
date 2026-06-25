package ai.jarvis.voice;

import ai.jarvis.common.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * ENDPOINTS:
 * POST /api/v1/voice/transcribe
 *   Audio file → transcribed text
 *
 * POST /api/v1/voice/speak
 *   Text → audio/wav bytes
 *
 * POST /api/v1/voice/chat
 *   Audio file → AI response (SSE stream)
 *
 * GET /api/v1/voice/status
 *   Check voice feature availability
 *
 * ALL ENDPOINTS require JWT authentication.
 * Audio files accepted as multipart/form-data.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Voice",
        description = "Voice assistant endpoints")
public class VoiceController {

    private final VoiceConversationService
            voiceConversationService;
    private final WhisperTranscriptionService
            transcriptionService;
    private final TextToSpeechService
            textToSpeechService;

    // ── POST /api/v1/voice/transcribe ─────────────

    @Operation(
            summary = "Transcribe audio to text",
            description =
                    "Upload an audio file and receive "
                            + "the transcribed text. "
                            + "Supports: wav, mp3, "
                            + "webm, ogg, m4a"
    )
    @PostMapping(
            value = "/transcribe",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<VoiceResponse>> transcribe(
            @RequestPart("audio") FilePart audioPart) {

        log.info(
                "Transcription request: file={}",
                audioPart.filename());

        return audioPart
                .content()
                .reduce(
                        new byte[0],
                        (acc, dataBuffer) -> {
                            byte[] bytes =
                                    new byte[dataBuffer
                                            .readableByteCount()];
                            dataBuffer.read(bytes);
                            byte[] result =
                                    new byte[acc.length
                                            + bytes.length];
                            System.arraycopy(
                                    acc, 0,
                                    result, 0,
                                    acc.length);
                            System.arraycopy(
                                    bytes, 0,
                                    result,
                                    acc.length,
                                    bytes.length);
                            return result;
                        })
                .flatMap(transcriptionService::transcribe)
                .map(text -> new VoiceResponse(
                        text,
                        null,
                        transcriptionService.getMode()))
                .map(ApiResponse::ok)
                .onErrorMap(error -> {
                    log.error(
                            "Transcription failed: {}",
                            error.getMessage());
                    return new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            error.getMessage());
                });
    }

    // ── POST /api/v1/voice/speak ──────────────────

    @Operation(
            summary = "Convert text to speech",
            description =
                    "Send text and receive audio/wav bytes. "
                            + "Plays through system speakers "
                            + "on the server."
    )
    @PostMapping(
            value = "/speak",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> speak(
            @Valid @RequestBody VoiceRequest request) {

        log.info(
                "TTS request: {} chars",
                request.text().length());

        return textToSpeechService
                .speakAndPlay(request.text())
                .onErrorResume(error -> {
                    log.error(
                            "TTS failed: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    // ── POST /api/v1/voice/speak/bytes ────────────

    @Operation(
            summary = "Get audio bytes for text",
            description =
                    "Send text and receive "
                            + "raw audio/wav bytes. "
                            + "Use for client-side playback."
    )
    @PostMapping(
            value = "/speak/bytes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "audio/wav"
    )
    public Mono<byte[]> speakBytes(
            @Valid @RequestBody VoiceRequest request) {

        return textToSpeechService
                .speak(request.text())
                .onErrorReturn(new byte[0]);
    }

    // ── POST /api/v1/voice/chat ───────────────────

    @Operation(
            summary = "Voice chat (audio in, SSE out)",
            description =
                    "Upload audio, receive AI response "
                            + "as Server-Sent Events. "
                            + "TTS plays on server speakers."
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

                    return audioPart
                            .content()
                            .reduce(
                                    new byte[0],
                                    (acc, buf) -> {
                                        byte[] bytes =
                                                new byte[buf
                                                        .readableByteCount()];
                                        buf.read(bytes);
                                        byte[] result =
                                                new byte[acc.length
                                                        + bytes.length];
                                        System.arraycopy(
                                                acc, 0,
                                                result, 0,
                                                acc.length);
                                        System.arraycopy(
                                                bytes, 0,
                                                result,
                                                acc.length,
                                                bytes.length);
                                        return result;
                                    })
                            .flatMapMany(audioBytes ->
                                    voiceConversationService
                                            .voiceChat(
                                                    audioBytes,
                                                    sessionId,
                                                    userId,
                                                    username,
                                                    role))
                            .map(token ->
                                    ServerSentEvent
                                            .<String>builder()
                                            .event("token")
                                            .data(token)
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
            summary = "Check voice feature status",
            description =
                    "Returns availability of "
                            + "transcription (Whisper) "
                            + "and TTS services."
    )
    @GetMapping(
            value = "/status",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ApiResponse<VoiceStatusResponse>>
    status() {

        return Mono.zip(
                        transcriptionService.isAvailable(),
                        textToSpeechService.isAvailable()
                )
                .map(tuple ->
                        new VoiceStatusResponse(
                                tuple.getT1(),
                                tuple.getT2(),
                                tuple.getT1()
                                        && tuple.getT2(),
                                transcriptionService
                                        .getMode(),
                                textToSpeechService
                                        .getName()))
                .map(ApiResponse::ok);
    }

    // ── Private Helpers ───────────────────────────

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

    /**
     * Voice status response for GET /status.
     */
    public record VoiceStatusResponse(
            boolean transcriptionAvailable,
            boolean ttsAvailable,
            boolean voiceReady,
            String transcriptionMode,
            String ttsEngine
    ) {}
}