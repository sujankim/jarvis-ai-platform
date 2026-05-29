package ai.jarvis.security.auth;

import ai.jarvis.security.auth.request.LoginRequest;
import ai.jarvis.security.auth.request.RegisterRequest;
import ai.jarvis.security.auth.response.RegisterResponse;
import ai.jarvis.security.auth.response.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@SecurityRequirement(name = "Bearer Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication",
        description = "Register and login endpoints")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a new user",
            description = "First registered user becomes ADMIN. " +
                    "All subsequent users become USER."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "User registered successfully"),
            @ApiResponse(responseCode = "409",
                    description = "Username or email already taken")
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(
            summary = "Login",
            description = "Returns JWT access token and refresh token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Login successful"),
            @ApiResponse(responseCode = "401",
                    description = "Invalid credentials")
    })
    @PostMapping("/login")
    public Mono<TokenResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}