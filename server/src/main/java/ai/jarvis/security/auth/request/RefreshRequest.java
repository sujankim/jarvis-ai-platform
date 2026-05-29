package ai.jarvis.security.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken

) {}
