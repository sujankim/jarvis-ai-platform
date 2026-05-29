package ai.jarvis.security.auth.response;

import ai.jarvis.user.UserRole;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {

    public record UserInfo(
            UUID userId,
            String username,
            String displayName,
            UserRole role
    ) {}

    public static TokenResponse of(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserInfo user
    ) {
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                user
        );
    }
}
