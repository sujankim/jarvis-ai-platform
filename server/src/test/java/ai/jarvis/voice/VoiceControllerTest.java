package ai.jarvis.voice;

import ai.jarvis.config.SecurityConfig;
import ai.jarvis.config.TestSecurityConfig;
import ai.jarvis.config.WithMockJarvisUser;
import ai.jarvis.security.jwt.JwtAuthenticationFilter;
import ai.jarvis.security.jwt.JwtService;
import ai.jarvis.voice.exception.VoiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@DisplayName("VoiceController Tests")
public class VoiceControllerTest {

    public static final String USER_ID_RAW = "3bb93254-6ce0-4cd3-91b3-a292a46e8fe9";


    /**
     * Tests authenticated Voice API requests with
     * VoiceConversationService mocked.
     * <p>
     * Only methods directly invoked by the controller are stubbed.
     * TextToSpeechService.speakAndPlay() is not stubbed because
     * controller tests do not call the real VoiceConversationService.
     */
    @Nested
    @WebFluxTest(controllers = {VoiceController.class})
    @Import(TestSecurityConfig.class)
    @WithMockJarvisUser(principal = VoiceControllerTest.USER_ID_RAW)
    class AuthenticatedTests {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private VoiceConversationService voiceConversationService;

        @MockitoBean
        private JwtService jwtService;


        /**
         * Verifies that voice status reports ready
         * when transcription and TTS services are available.
         */
        @Test
        @DisplayName("Test GET /api/v1/voice/status - Should return voice ready when both services are available")
        void testVoiceStatus_ShouldReturnVoiceReady() {

            when(voiceConversationService.isTranscriptionAvailable())
                    .thenReturn(Mono.just(true));
            when(voiceConversationService.isTtsAvailable())
                    .thenReturn(Mono.just(true));

            this.webTestClient
                    .get()
                    .uri("/api/v1/voice/status")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.transcriptionAvailable").isEqualTo(true)
                    .jsonPath("$.data.ttsAvailable").isEqualTo(true)
                    .jsonPath("$.data.voiceReady").isEqualTo(true);
        }


        /**
         * Verifies that voice status reports not ready
         * when one of the voice services is unavailable.
         */
        @Test
        @DisplayName("Test GET /api/v1/voice/status - Should return voice NOT ready when one service is down")
        void testVoiceStatus_ShouldReturnVoiceNotReady() {

            when(voiceConversationService.isTranscriptionAvailable())
                    .thenReturn(Mono.just(true));

            when(voiceConversationService.isTtsAvailable())
                    .thenReturn(Mono.just(false));

            this.webTestClient
                    .get()
                    .uri("/api/v1/voice/status")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.transcriptionAvailable").isEqualTo(true)
                    .jsonPath("$.data.ttsAvailable").isEqualTo(false)
                    .jsonPath("$.data.voiceReady").isEqualTo(false);

        }


        /**
         * Verifies that the speak endpoint returns 204
         * when TTS completes successfully.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/speak - Should return 204 when request is valid")
        void testVoiceSpeak_ShouldReturnNoContent() {


            when(voiceConversationService.speakText("This is a text"))
                    .thenReturn(Mono.empty());

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/speak")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(
                            """
                                    {
                                    "text": "This is a text"
                                    }
                                    """
                    )
                    .exchange()
                    .expectStatus().isNoContent();

        }


        /**
         * Verifies that the speak endpoint returns 503
         * when the TTS service is unavailable.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/speak - Should return 503 when TTS is unavailable")
        void testVoiceSpeak_ShouldReturnServiceUnavailable() {

            when(voiceConversationService.speakText("This is a text"))
                    .thenReturn(Mono.error(
                            new RuntimeException("TTS service unavailable")
                    ));

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/speak")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                                "text": "This is a text"
                            }
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }


        /**
         * Verifies that the speak endpoint returns 400
         * when the request contains blank text.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/speak - Should return 400 when text is blank")
        void testVoiceSpeak_ShouldReturnBadRequest() {

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/speak")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""

                                  {
                             "text": ""
                                  }
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();

            verifyNoInteractions(voiceConversationService);

        }


        /**
         * Verifies that the transcribe endpoint returns
         * the transcription for valid audio.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/transcribe - Should return transcription when audio is valid")
        void testVoiceTranscribe_ShouldReturnTranscription() {

            byte[] audioBytes = new byte[]{1, 2, 3, 4};

            when(voiceConversationService.transcribeOnly(any(byte[].class)))
                    .thenReturn(Mono.just("Hello Jarvis"));

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part(
                    "audio",
                    new ByteArrayResource(audioBytes) {
                        @Override
                        public String getFilename() {
                            return "audio.wav";
                        }
                    }
            );

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.transcription").isEqualTo("Hello Jarvis");
        }


        /**
         * Verifies that the transcribe endpoint returns 503
         * when Whisper is unavailable.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/transcribe - Should return 503 when whisper is unavailable")
        void testVoiceTranscribe_ShouldReturn503() {

            byte[] audioBytes = new byte[]{1, 2, 3, 4};

            when(voiceConversationService.transcribeOnly(any(byte[].class)))
                    .thenReturn(Mono.error(VoiceException.whisperNotAvailable()));

            ByteArrayResource resource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio.wav";
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("audio", resource);

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        }


        /**
         * Verifies that the transcribe endpoint returns 400
         * when the uploaded audio is empty.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/transcribe - Should return 400 when audio is empty")
        void testVoiceTranscribe_ShouldReturn400() {

            byte[] audioBytes = new byte[0];

            when(voiceConversationService.transcribeOnly(any(byte[].class)))
                    .thenReturn(Mono.error(
                            VoiceException.emptyAudio()));


            ByteArrayResource resource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio.wav";
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("audio", resource);

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isBadRequest();


        }
    }


    /**
     * Verifies that protected Voice API endpoints reject
     * requests without JWT authentication.
     */
    @Nested
    @WebFluxTest(controllers = VoiceController.class)
    @Import({
            SecurityConfig.class,
            JwtAuthenticationFilter.class
    })
    class UnauthorizedTests {

        @Autowired
        private WebTestClient webTestClient;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private VoiceConversationService voiceConversationService;


        /**
         * Verifies that the status endpoint returns 401
         * when no JWT token is provided.
         */
        @Test
        @DisplayName("Test GET /api/v1/voice/status - Should return 401 without JWT token")
        void testVoiceStatus_ShouldReturnUnauthorized() {

            webTestClient.get()
                    .uri("/api/v1/voice/status")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }


        /**
         * Verifies that the speak endpoint returns 401
         * when no JWT token is provided.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/speak - Should return 401 without JWT token")
        void testVoiceSpeak_ShouldReturnUnauthorized() {

            webTestClient.post()
                    .uri("/api/v1/voice/speak")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                                                    {
                                                    "text": "This is a text"
                                                    }
                            """)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }


        /**
         * Verifies that the transcribe endpoint returns 401
         * when no JWT token is provided.
         */
        @Test
        @DisplayName("Test POST /api/v1/voice/transcribe - Should return 401 without JWT token")
        void testVoiceTranscribe_ShouldReturnUnauthorized() {

            byte[] audioBytes = new byte[]{1, 2, 3, 4};

            ByteArrayResource resource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "audio.wav";
                }
            };

            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            builder.part("audio", resource);

            this.webTestClient
                    .post()
                    .uri("/api/v1/voice/transcribe")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchange()
                    .expectStatus().isUnauthorized();

        }

    }

}
