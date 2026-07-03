package ai.jarvis.settings;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.config.JarvisProperties;
import ai.jarvis.voice.TextToSpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Settings", description = "Current Jarvis runtime configuration")
public class SettingsController {

    private final JarvisProperties jarvisProperties;
    private final Environment environment;
    private final TextToSpeechService textToSpeechService;
    private final RuntimeSettingsService runtimeSettingsService;

    @Operation(
            summary = "Get current settings",
            description = "Returns the current Jarvis configuration used by the web UI settings page."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Settings returned successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "JWT token is missing or invalid")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<SettingsResponse>> getSettings() {
        return getUserId()
                .thenReturn(ApiResponse.ok(buildResponse()));
    }

    @Operation(
            summary = "Update voice settings",
            description = "Updates the voice configuration at runtime without restart."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Voice settings updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid voice speed or name provided"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "JWT token is missing or invalid")
    })
    @PatchMapping(value = "/voice", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<SettingsResponse.VoiceSettings>> updateVoiceSettings(
            @Valid @RequestBody VoiceSettingsRequest request) {
        return getUserId()
                .map(userId -> {
                    runtimeSettingsService.updateVoiceSettings(request.voiceName(), request.voiceSpeed());
                    return buildVoiceSettings();
                })
                .map(ApiResponse::ok);
    }

    private SettingsResponse buildResponse() {
        JarvisProperties.AiProperties ai = jarvisProperties.ai();

        return new SettingsResponse(
                buildVoiceSettings(),
                new SettingsResponse.ProviderSettings(
                        safeString(ai.primaryProvider()),
                        environment.getProperty("spring.ai.ollama.chat.model", ""),
                        hasText(environment.getProperty("spring.ai.google.genai.api-key", ""))),
                new SettingsResponse.SystemInfo(
                        safeString(jarvisProperties.version()),
                        System.getProperty("java.version", "")));
    }

    private SettingsResponse.VoiceSettings buildVoiceSettings() {
        JarvisProperties.VoiceProperties voice = jarvisProperties.voice();
        return new SettingsResponse.VoiceSettings(
                safeString(runtimeSettingsService.getVoiceName()),
                runtimeSettingsService.getVoiceSpeed(),
                safeString(textToSpeechService.getName()),
                safeString(voice.whisper().model()));
    }

    private Mono<UUID> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString)
                .onErrorMap(
                        IllegalArgumentException.class,
                        ex -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid token subject",
                                ex));
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
