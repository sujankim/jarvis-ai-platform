package ai.jarvis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.jarvis.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.test.StepVerifier;

@DisplayName("OpenRouterProvider Tests")
class OpenRouterProviderTest {

    private static final String BASE_URL =
            OpenRouterProvider.DEFAULT_BASE_URL;

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