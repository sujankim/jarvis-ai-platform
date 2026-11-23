package ai.jarvis.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ProviderRouter Tests")
class ProviderRouterTest {

    private OllamaProvider ollama;
    private AiProvider gemini;
    private AiProvider openRouter;

    @BeforeEach
    void setUp() {
        ollama = mock(OllamaProvider.class);
        gemini = mock(AiProvider.class);
        openRouter = mock(AiProvider.class);

        when(gemini.getName()).thenReturn("gemini");
        when(gemini.getModelName()).thenReturn("gemini-test");
        when(openRouter.getName()).thenReturn("openrouter");
        when(openRouter.getModelName())
                .thenReturn("openrouter/free");
    }

    // ── helpers ─────────────────────────────────────────

    /** Router with OpenRouter present, or absent when null. */
    @SuppressWarnings("unchecked")
    private ProviderRouter routerWith(
            AiProvider openRouterOrNull) {
        ObjectProvider<AiProvider> openRouterBean =
                mock(ObjectProvider.class);
        when(openRouterBean.getIfAvailable())
                .thenReturn(openRouterOrNull);
        return new ProviderRouter(
                ollama, gemini, openRouterBean);
    }

    private static void available(
            AiProvider provider, boolean up) {
        when(provider.isAvailable())
                .thenReturn(Mono.just(up));
    }

    // ── route() ─────────────────────────────────────────

    @Test
    @DisplayName("routes to Ollama when it is available")
    void routesToOllama() {
        available(ollama, true);

        StepVerifier.create(routerWith(openRouter).route())
                .expectNext(ollama)
                .verifyComplete();

        verify(gemini, never()).isAvailable();
        verify(openRouter, never()).isAvailable();
    }

    @Test
    @DisplayName("falls back to Gemini when Ollama is down")
    void fallsBackToGemini() {
        available(ollama, false);
        available(gemini, true);

        StepVerifier.create(routerWith(openRouter).route())
                .expectNext(gemini)
                .verifyComplete();

        verify(openRouter, never()).isAvailable();
    }

    @Test
    @DisplayName("falls back to OpenRouter when Ollama and Gemini are down")
    void fallsBackToOpenRouter() {
        available(ollama, false);
        available(gemini, false);
        available(openRouter, true);

        StepVerifier.create(routerWith(openRouter).route())
                .expectNext(openRouter)
                .verifyComplete();
    }

    @Test
    @DisplayName("fails when every provider is unavailable")
    void failsWhenAllDown() {
        available(ollama, false);
        available(gemini, false);
        available(openRouter, false);

        StepVerifier.create(routerWith(openRouter).route())
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(
                                "No AI provider available"))
                .verify();
    }

    @Test
    @DisplayName("fails when OpenRouter is not configured and the others are down")
    void failsWhenOpenRouterAbsent() {
        available(ollama, false);
        available(gemini, false);

        StepVerifier.create(routerWith(null).route())
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining(
                                "No AI provider available"))
                .verify();
    }

    @Test
    @DisplayName("still routes to Gemini when OpenRouter is not configured")
    void geminiStillWorksWithoutOpenRouter() {
        available(ollama, false);
        available(gemini, true);

        StepVerifier.create(routerWith(null).route())
                .expectNext(gemini)
                .verifyComplete();
    }

    // ── routeTo(name) ───────────────────────────────────

    @Test
    @DisplayName("routeTo returns OpenRouter (case-insensitive)")
    void routeToOpenRouter() {
        ProviderRouter router = routerWith(openRouter);

        StepVerifier.create(router.routeTo("openrouter"))
                .expectNext(openRouter)
                .verifyComplete();
        StepVerifier.create(router.routeTo("OpenRouter"))
                .expectNext(openRouter)
                .verifyComplete();
    }

    @Test
    @DisplayName("routeTo openrouter explains how to enable it when not configured")
    void routeToOpenRouterNotConfigured() {
        StepVerifier.create(routerWith(null).routeTo("openrouter"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("OPENROUTER_API_KEY"))
                .verify();
    }

    @Test
    @DisplayName("routeTo keeps working for ollama and gemini")
    void routeToExistingProviders() {
        ProviderRouter router = routerWith(openRouter);

        StepVerifier.create(router.routeTo("ollama"))
                .expectNext(ollama)
                .verifyComplete();
        StepVerifier.create(router.routeTo("gemini"))
                .expectNext(gemini)
                .verifyComplete();
    }

    @Test
    @DisplayName("routeTo rejects unknown providers")
    void routeToUnknown() {
        StepVerifier.create(routerWith(openRouter).routeTo("nope"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .hasMessageContaining("Unknown provider"))
                .verify();
    }
}