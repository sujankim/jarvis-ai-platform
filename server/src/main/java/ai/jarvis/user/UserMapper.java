package ai.jarvis.user;

import ai.jarvis.security.auth.request.RegisterRequest;
import ai.jarvis.security.auth.response.RegisterResponse;
import ai.jarvis.security.auth.response.TokenResponse.UserInfo;
import ai.jarvis.security.auth.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    // RegisterRequest → User
    // Note: id, passwordHash, role, active, timestamps
    // are set by AuthService — NOT mapped here
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "displayName",
            expression = "java(request.displayName() != null " +
                    "&& !request.displayName().isBlank() " +
                    "? request.displayName() " +
                    ": request.username())")
    User toEntity(RegisterRequest request);

    // User → UserResponse (SAFE — no password hash)
    @Mapping(target = "active", source = "active")
    UserResponse toResponse(User user);

    // User → RegisterResponse
    @Mapping(target = "message",
            constant = "Account created successfully")
    @Mapping(target = "userId", source = "id")
    RegisterResponse toRegisterResponse(User user);

    // User → UserInfo (for JWT token response)
    @Mapping(target = "userId", source = "id")
    UserInfo toUserInfo(User user);
}
