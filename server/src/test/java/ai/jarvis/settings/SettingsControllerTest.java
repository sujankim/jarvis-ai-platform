package ai.jarvis.settings;

import ai.jarvis.config.JarvisProperties;
import ai.jarvis.config.SecurityConfig;
import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import ai.jarvis.security.jwt.JwtService;
import ai.jarvis.voice.TextToSpeechService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.when;

@DisplayName("SettingsController Tests")
class SettingsControllerTest {

    private static final String TOKEN = "settings-token";
    private static final String USER_ID =
            "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";

    @Nested
    @DisplayName("When voice and Gemini are configured")
    @WebFluxTest(controllers = SettingsController.class)
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    @EnableConfigurationProperties(JarvisProperties.class)
    @TestPropertySource(properties = {
            "jarvis.version=1.2.3-test",
            "jarvis.security.jwt.secret=test-secret-key-long-enough-for-settings",
            "jarvis.voice.tts.voice=Test Voice",
            "jarvis.voice.tts.speed=1.25",
            "jarvis.voice.whisper.model=whisper-test",
            "jarvis.ai.primary-provider=gemini",
            "spring.ai.ollama.chat.model=llama-test",
            "spring.ai.google.genai.api-key=test-gemini-key"
    })
    class ConfiguredSettings {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private TextToSpeechService textToSpeechService;

        @BeforeEach
        void setUp() {
            setUpJwt(jwtService);
            when(textToSpeechService.getName())
                    .thenReturn("test-tts-engine");
        }

        @Test
        @DisplayName("GET /api/v1/settings returns current settings")
        void getSettings_ReturnsCurrentSettings() {
            webTestClient
                    .get()
                    .uri("/api/v1/settings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.voice.voiceName").isEqualTo("Test Voice")
                    .jsonPath("$.data.voice.voiceSpeed").isEqualTo(1.25)
                    .jsonPath("$.data.voice.ttsEngine").isEqualTo("test-tts-engine")
                    .jsonPath("$.data.voice.whisperMode").isEqualTo("whisper-test")
                    .jsonPath("$.data.provider.primaryProvider").isEqualTo("gemini")
                    .jsonPath("$.data.provider.primaryModel").isEqualTo("llama-test")
                    .jsonPath("$.data.provider.geminiConfigured").isEqualTo(true)
                    .jsonPath("$.data.system.version").isEqualTo("1.2.3-test")
                    .jsonPath("$.data.system.javaVersion").exists();
        }

        @Test
        @DisplayName("GET /api/v1/settings returns 401 without JWT")
        void getSettings_ReturnsUnauthorizedWithoutJwt() {
            webTestClient
                    .get()
                    .uri("/api/v1/settings")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("When voice and Gemini are not configured")
    @WebFluxTest(controllers = SettingsController.class)
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    @EnableConfigurationProperties(JarvisProperties.class)
    @TestPropertySource(properties = {
            "jarvis.version=1.2.3-test",
            "jarvis.security.jwt.secret=test-secret-key-long-enough-for-settings",
            "jarvis.voice.tts.voice=",
            "jarvis.voice.tts.speed=1.25",
            "jarvis.voice.whisper.model=whisper-test",
            "jarvis.ai.primary-provider=ollama",
            "spring.ai.ollama.chat.model=llama-test",
            "spring.ai.google.genai.api-key="
    })
    class DefaultSettings {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private TextToSpeechService textToSpeechService;

        @BeforeEach
        void setUp() {
            setUpJwt(jwtService);
            when(textToSpeechService.getName())
                    .thenReturn("default-tts-engine");
        }

        @Test
        @DisplayName("GET /api/v1/settings returns empty voice and unconfigured Gemini")
        void getSettings_ReturnsDefaultsWhenVoiceAndGeminiAreBlank() {
            webTestClient
                    .get()
                    .uri("/api/v1/settings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.voice.voiceName").isEqualTo("")
                    .jsonPath("$.data.voice.voiceSpeed").isEqualTo(1.25)
                    .jsonPath("$.data.voice.ttsEngine").isEqualTo("default-tts-engine")
                    .jsonPath("$.data.voice.whisperMode").isEqualTo("whisper-test")
                    .jsonPath("$.data.provider.primaryProvider").isEqualTo("ollama")
                    .jsonPath("$.data.provider.primaryModel").isEqualTo("llama-test")
                    .jsonPath("$.data.provider.geminiConfigured").isEqualTo(false)
                    .jsonPath("$.data.system.version").isEqualTo("1.2.3-test")
                    .jsonPath("$.data.system.javaVersion").exists();
        }
    }

    private void setUpJwt(JwtService jwtService) {
        when(jwtService.validateToken(TOKEN)).thenReturn(true);
        when(jwtService.extractTokenType(TOKEN)).thenReturn("access");
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.extractUsername(TOKEN)).thenReturn("test-user");
        when(jwtService.extractRole(TOKEN)).thenReturn("USER");
    }
}
