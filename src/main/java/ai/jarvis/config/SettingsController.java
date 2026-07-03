package ai.jarvis.config;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final RuntimeSettingsService runtimeSettingsService;

    public SettingsController(RuntimeSettingsService runtimeSettingsService) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @PatchMapping("/voice")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateVoiceSettings(
            @Valid @RequestBody VoiceSettingsRequest request) {
        
        runtimeSettingsService.updateVoiceSettings(request.voiceName(), request.voiceSpeed());
        
        SettingsResponse response = new SettingsResponse(
                runtimeSettingsService.getVoiceName(),
                runtimeSettingsService.getVoiceSpeed()
        );
        
        return ResponseEntity.ok(new ApiResponse<>(response));
    }
}
