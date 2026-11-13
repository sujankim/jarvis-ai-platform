package ai.jarvis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.jarvis.tools.ToolRegistry;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

@DisplayName("OpenRouterProvider Tests")
class OpenRouterProviderTest {

    private static final String BASE_URL =
            OpenRouterProvider.DEFAULT_BASE_URL;


    private static final String SSE_RESPONSE =
            chunk("{\"role\":\"assistant\",\"content\":\"Hello\"}", "null")
                    + chunk("{\"content\":\" world\"}", "null")
                    + chunk("{}", "\"stop\"")
                    + "data: [DONE]\n\n";

    private static String chunk(
            String delta, String finishReason) {
        return "data: {\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion.chunk\","
                + "\"created\":1,"
                + "\"model\":\"vendor/some-model\","
                + "\"choices\":[{\"index\":0,\"delta\":" + delta
                + ",\"finish_reason\":" + finishReason + "}]}\n\n";
    }


    public static class PingTool {
        @Tool(description = "Replies with pong")
        public String ping() {
            return "pong";
        }
    }

    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(ToolRegistry.class);
    }

    @Nested
    @DisplayName("When an API key is configured")
    class WithApiKey {

        private OpenRouterProvider provider;

        @BeforeEach
        void createProvider() {
            provider = new OpenRouterProvider(
                    "sk-or-test",
                    BASE_URL,
                    "vendor/some-model",
                    toolRegistry);
        }

        @Test
        @DisplayName("is named openrouter")
        void name() {
            assertThat(provider.getName())
                    .isEqualTo("openrouter");
        }

        @Test
        @DisplayName("exposes the configured model")
        void modelName() {
            assertThat(provider.getModelName())
                    .isEqualTo("vendor/some-model");
        }

        @Test
        @DisplayName("falls back to the default model when the model is blank")
        void blankModelFallsBack() {
            OpenRouterProvider blankModel =
                    new OpenRouterProvider(
                            "sk-or-test", BASE_URL,
                            "  ", toolRegistry);

            assertThat(blankModel.getModelName())
                    .isEqualTo(OpenRouterProvider.DEFAULT_MODEL);
        }

        @Test
        @DisplayName("is available")
        void available() {
            StepVerifier.create(provider.isAvailable())
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("When no API key is configured")
    class WithoutApiKey {

        private OpenRouterProvider provider;

        @BeforeEach
        void createProvider() {
            provider = new OpenRouterProvider(
                    "", BASE_URL, "", toolRegistry);
        }

        @Test
        @DisplayName("is unavailable")
        void unavailable() {
            StepVerifier.create(provider.isAvailable())
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("streamChat fails with a clear error")
        void streamChatFails() {
            StepVerifier.create(
                            provider.streamChat(new Prompt("hi")))
                    .expectErrorMessage(
                            "OpenRouter API key not configured.")
                    .verify();
        }
    }


    @Nested
    @DisplayName("streamChat against a local OpenAI-compatible endpoint")
    class StreamChat {

        private static final String MODEL = "vendor/some-model";

        private HttpServer server;
        private final AtomicReference<String> requestPath =
                new AtomicReference<>();
        private final AtomicReference<String> authorization =
                new AtomicReference<>();
        private final AtomicReference<String> requestBody =
                new AtomicReference<>();

        @BeforeEach
        void startServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getByName("127.0.0.1"), 0),
                    0);
            server.createContext("/", exchange -> {
                requestPath.set(
                        exchange.getRequestURI().getPath());
                authorization.set(
                        exchange.getRequestHeaders()
                                .getFirst("Authorization"));
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));

                byte[] response =
                        SSE_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, response.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(response);
                }
            });
            server.start();
        }

        @AfterEach
        void stopServer() {
            server.stop(0);
        }

        private String baseUrl() {
            return "http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/api/v1";
        }

        private String chat() {
            OpenRouterProvider provider = new OpenRouterProvider(
                    "sk-or-test", baseUrl(), MODEL, toolRegistry);

            List<String> tokens = provider
                    .streamChat(new Prompt("hi"))
                    .collectList()
                    .block(Duration.ofSeconds(15));

            return String.join("", tokens);
        }

        @Test
        @DisplayName("streams tokens from <baseUrl>/chat/completions with the key and model")
        void streamsTokens() {
            when(toolRegistry.hasTools()).thenReturn(false);

            assertThat(chat()).isEqualTo("Hello world");

            assertThat(requestPath.get())
                    .isEqualTo("/api/v1/chat/completions");
            assertThat(authorization.get())
                    .isEqualTo("Bearer sk-or-test");
            assertThat(requestBody.get())
                    .containsPattern(
                            "\"model\"\\s*:\\s*\"vendor/some-model\"");
        }

        @Test
        @DisplayName("omits tool definitions when the registry has no tools")
        void omitsToolsWhenNone() {
            when(toolRegistry.hasTools()).thenReturn(false);

            chat();

            assertThat(requestBody.get())
                    .doesNotContain("\"tools\"");
        }

        @Test
        @DisplayName("sends tool definitions when the registry has tools")
        void sendsToolsWhenPresent() {
            when(toolRegistry.hasTools()).thenReturn(true);
            when(toolRegistry.asArray())
                    .thenReturn(new Object[] {new PingTool()});

            chat();

            assertThat(requestBody.get())
                    .containsPattern("\"tools\"\\s*:")
                    .containsPattern("\"name\"\\s*:\\s*\"ping\"");
        }
    }

    @Nested
    @DisplayName("Bean registration")
    class BeanRegistration {

        private final ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                OpenRouterProvider.class)
                        .withBean(
                                ToolRegistry.class,
                                () -> mock(ToolRegistry.class));

        @Test
        @DisplayName("is not created without OPENROUTER_API_KEY")
        void absentWithoutKey() {
            // Explicit blank value so a key in the real
            // environment cannot leak into this test.
            runner.withPropertyValues("OPENROUTER_API_KEY=")
                    .run(context -> assertThat(context)
                            .doesNotHaveBean("openrouter"));
        }

        @Test
        @DisplayName("is created as 'openrouter' when the key is set")
        void presentWithKey() {
            runner.withPropertyValues(
                            "OPENROUTER_API_KEY=sk-or-test")
                    .run(context -> {
                        assertThat(context)
                                .hasBean("openrouter");
                        assertThat(context.getBean("openrouter"))
                                .isInstanceOf(AiProvider.class);
                    });
        }

        @Test
        @DisplayName("reads the model from OPENROUTER_MODEL")
        void readsModelFromProperty() {
            runner.withPropertyValues(
                            "OPENROUTER_API_KEY=sk-or-test",
                            "OPENROUTER_MODEL=vendor/other-model")
                    .run(context -> assertThat(
                            context.getBean(
                                            "openrouter", AiProvider.class)
                                    .getModelName())
                            .isEqualTo("vendor/other-model"));
        }
    }
}