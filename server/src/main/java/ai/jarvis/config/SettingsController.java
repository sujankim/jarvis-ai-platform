package ai.jarvis.config;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.settings.SettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "Runtime configuration management")
public class SettingsController {

    private final RuntimeSettingsService runtimeSettingsService;

    public SettingsController(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @PatchMapping("/voice")
    @Operation(summary = "Update voice settings", description = "Updates the voice configuration at runtime without restart")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Voice settings updated successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid voice speed provided")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<ApiResponse<SettingsResponse.VoiceSettings>> updateVoiceSettings(
            @Valid @RequestBody VoiceSettingsRequest request) {
        
        runtimeSettingsService.updateVoiceSettings(request.voiceName(), request.voiceSpeed());
        
        SettingsResponse.VoiceSettings voiceSettings = new SettingsResponse.VoiceSettings(
                runtimeSettingsService.getVoiceName(),
                runtimeSettingsService.getVoiceSpeed()
        );
        
        return ResponseEntity.ok(new ApiResponse<SettingsResponse.VoiceSettings>(voiceSettings));
    }
}
