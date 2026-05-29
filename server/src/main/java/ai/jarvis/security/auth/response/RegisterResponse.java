package ai.jarvis.security.auth.response;

import ai.jarvis.user.UserRole;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String username,
        String displayName,
        UserRole role,
        String message
) {}
