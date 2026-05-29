package ai.jarvis.security.auth.response;

import ai.jarvis.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        UserRole role,
        boolean active,
        Instant createdAt
) {}
