package ai.jarvis.security;

import ai.jarvis.config.JarvisProperties;
import ai.jarvis.security.jwt.JwtService;
import ai.jarvis.user.User;
import ai.jarvis.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Build test JarvisProperties
        JarvisProperties.JwtProperties jwtProps =
                new JarvisProperties.JwtProperties(
                        "test-secret-key-minimum-32-characters-long",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        "jarvis-ai-platform"
                );

        JarvisProperties.SecurityProperties secProps =
                new JarvisProperties.SecurityProperties(
                        jwtProps,
                        new JarvisProperties
                                .RateLimitingProperties(
                                true, 30, 5, 10),
                        new JarvisProperties
                                .Argon2Properties(
                                65536, 3, 1, 32, 16)
                );

        JarvisProperties props = new JarvisProperties(
                "0.1.0-SNAPSHOT",
                secProps,
                new JarvisProperties.AiProperties(
                        "ollama", "gemini",
                        Duration.ofSeconds(3),
                        new JarvisProperties
                                .StreamingProperties(
                                Duration.ofSeconds(120), 1),
                        new JarvisProperties
                                .ContextProperties(
                                8000, 2000, 25, 20, 6),
                        new JarvisProperties
                                .GuardrailProperties(
                                10000, true)
                ),
                new JarvisProperties.CliProperties(
                        "~/.jarvis",
                        "~/.jarvis/logs",
                        "~/.jarvis/auth.json",
                        "~/.jarvis/session.json",
                        "~/.jarvis/preferences.json"
                ),
                new JarvisProperties
                        .ObservabilityProperties(
                        true, true, true,
                        Duration.ofMinutes(5)),

                // Added VoiceProperties (6th arg)
                // JarvisProperties record now requires it
                // Use defaults: empty voice name, speed 1.0
                new JarvisProperties.VoiceProperties(
                        new JarvisProperties.WhisperProperties(
                                "https://api.groq.com/openai/v1",
                                "",
                                "whisper-large-v3-turbo"),
                        new JarvisProperties.TtsProperties(
                                true,
                                "",
                                1.0)
                )
        );

        jwtService = new JwtService(props);

        testUser = new User(
                UUID.randomUUID(),
                "dravin",
                "dravin@jarvis.ai",
                "$2a$12$hashedpassword",
                "Dravin",
                UserRole.ADMIN,
                true,
                Instant.now(),
                Instant.now(),
                null
        );
    }

    @Test
    @DisplayName("Should generate valid access token")
    void shouldGenerateAccessToken() {
        String token = jwtService
                .generateAccessToken(testUser);

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Access token should be valid")
    void accessTokenShouldBeValid() {
        String token = jwtService
                .generateAccessToken(testUser);

        assertThat(jwtService.validateToken(token))
                .isTrue();
    }

    @Test
    @DisplayName("Should extract userId from token")
    void shouldExtractUserId() {
        String token = jwtService
                .generateAccessToken(testUser);

        String extractedId =
                jwtService.extractUserId(token);

        assertThat(extractedId)
                .isEqualTo(testUser.id().toString());
    }

    @Test
    @DisplayName("Should extract username from token")
    void shouldExtractUsername() {
        String token = jwtService
                .generateAccessToken(testUser);

        String username =
                jwtService.extractUsername(token);

        assertThat(username)
                .isEqualTo(testUser.username());
    }

    @Test
    @DisplayName("Should extract role from token")
    void shouldExtractRole() {
        String token = jwtService
                .generateAccessToken(testUser);

        String role = jwtService.extractRole(token);

        assertThat(role)
                .isEqualTo(UserRole.ADMIN.name());
    }

    @Test
    @DisplayName("Access token type should be 'access'")
    void accessTokenTypeShouldBeAccess() {
        String token = jwtService
                .generateAccessToken(testUser);

        String type =
                jwtService.extractTokenType(token);

        assertThat(type).isEqualTo("access");
    }

    @Test
    @DisplayName("Refresh token type should be 'refresh'")
    void refreshTokenTypeShouldBeRefresh() {
        String token = jwtService
                .generateRefreshToken(testUser);

        String type =
                jwtService.extractTokenType(token);

        assertThat(type).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Tampered token should be invalid")
    void tamperedTokenShouldBeInvalid() {
        String token = jwtService
                .generateAccessToken(testUser);

        // Tamper with the payload
        String tampered = token.substring(0,
                token.lastIndexOf('.') + 1)
                + "invalidsignature";

        assertThat(jwtService.validateToken(tampered))
                .isFalse();
    }

    @Test
    @DisplayName("Token should not be expired immediately")
    void tokenShouldNotBeExpiredImmediately() {
        String token = jwtService
                .generateAccessToken(testUser);

        assertThat(jwtService.isTokenExpired(token))
                .isFalse();
    }

    @Test
    @DisplayName("Access token expiry should be 900 seconds")
    void accessTokenExpiryShouldBe900Seconds() {
        assertThat(jwtService
                .getAccessTokenExpirySeconds())
                .isEqualTo(900L);
    }
}