package ai.jarvis.ai.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Routes AI requests to the correct provider.
 *
 * ROUTING LOGIC:
 * 1. Check Ollama availability (local, primary)
 * 2. If Ollama down → fallback to Gemini
 * 3. If Gemini unavailable → fallback to OpenRouter
 *    (only when OPENROUTER_API_KEY is set)
 * 4. If all down → error with helpful message
 *
 * FIX (CI Job #81959644503):
 * Added @Qualifier("gemini") to constructor parameter.
 * Without this, Spring cannot resolve which AiProvider
 * bean to inject when multiple implementations exist.
 *
 * Both GeminiProvider and GeminiUnavailableProvider are
 * now named "gemini" via @Component("gemini").
 * Only ONE will be created (conditional beans).
 * @Qualifier tells Spring exactly which name to inject.
 *
 * OPENROUTER:
 * There is no "unavailable" placeholder bean for
 * OpenRouter. OpenRouterProvider simply does not exist
 * without an API key, so it is injected through an
 * ObjectProvider and the field is null when absent.
 */
@Slf4j
@Component
public class ProviderRouter {

    private final OllamaProvider ollamaProvider;
    private final AiProvider geminiProvider;

    // null when OPENROUTER_API_KEY is not set
    private final AiProvider openRouterProvider;

    /**
     * FIX: @Qualifier("gemini") added.
     *
     * WHY THIS FIXES THE CI FAILURE:
     * In CI: GEMINI_API_KEY is empty
     * → GeminiProvider NOT created (ConditionalOnExpression)
     * → GeminiUnavailableProvider IS created (@ConditionalOnMissingBean)
     * → Both are named "gemini" via @Component("gemini")
     * → @Qualifier("gemini") tells Spring to inject it
     * → ProviderRouter creates successfully ✅
     *
     * Locally with API key set:
     * → GeminiProvider IS created
     * → GeminiUnavailableProvider NOT created
     * → @Qualifier("gemini") injects GeminiProvider ✅
     *
     * OpenRouter is optional: the ObjectProvider resolves
     * to null when the "openrouter" bean does not exist.
     */
    public ProviderRouter(
            OllamaProvider ollamaProvider,
            @Qualifier("gemini")
            AiProvider geminiProvider,
            @Qualifier("openrouter")
            ObjectProvider<AiProvider> openRouterProvider) {
        this.ollamaProvider = ollamaProvider;
        this.geminiProvider = geminiProvider;
        this.openRouterProvider =
                openRouterProvider.getIfAvailable();
        log.info(
                "ProviderRouter initialized: "
                        + "primary=ollama fallback={} "
                        + "fallback2={}",
                geminiProvider.getName()
                        + "("
                        + geminiProvider.getModelName()
                        + ")",
                this.openRouterProvider == null
                        ? "none"
                        : this.openRouterProvider.getName()
                        + "("
                        + this.openRouterProvider.getModelName()
                        + ")");
    }

    public Mono<AiProvider> route() {
        return ollamaProvider.isAvailable()
                .flatMap(ollamaUp -> {
                    if (ollamaUp) {
                        log.debug(
                                "Routing to: ollama ({})",
                                ollamaProvider.getModelName());
                        return Mono.just(
                                (AiProvider) ollamaProvider);
                    }

                    log.warn(
                            "Ollama unavailable. "
                                    + "Checking Gemini...");

                    return geminiProvider
                            .isAvailable()
                            .flatMap(geminiUp -> {
                                if (geminiUp) {
                                    log.info(
                                            "Routing to: "
                                                    + "gemini [FALLBACK]");
                                    return Mono.just(
                                            geminiProvider);
                                }

                                log.warn(
                                        "Gemini unavailable. "
                                                + "Checking OpenRouter...");

                                return routeToOpenRouter();
                            });
                });
    }

    public Mono<AiProvider> routeTo(String name) {
        return switch (name.toLowerCase()) {
            case "ollama" ->
                    Mono.just((AiProvider) ollamaProvider);
            case "gemini" ->
                    Mono.just(geminiProvider);
            case "openrouter" ->
                    openRouterOrError();
            default -> Mono.error(
                    new RuntimeException(
                            "Unknown provider: " + name));
        };
    }

    /**
     * Last step of the fallback chain.
     * Fails with the usual "no provider" error when
     * OpenRouter is not configured or not available.
     */
    private Mono<AiProvider> routeToOpenRouter() {
        if (openRouterProvider == null) {
            return noProviderAvailable();
        }

        return openRouterProvider
                .isAvailable()
                .flatMap(openRouterUp -> {
                    if (openRouterUp) {
                        log.info(
                                "Routing to: "
                                        + "openrouter [FALLBACK]");
                        return Mono.just(openRouterProvider);
                    }
                    return noProviderAvailable();
                });
    }

    private Mono<AiProvider> openRouterOrError() {
        if (openRouterProvider == null) {
            return Mono.error(
                    new RuntimeException(
                            "OpenRouter is not configured.\n"
                                    + "Fix: set OPENROUTER_API_KEY"));
        }
        return Mono.just(openRouterProvider);
    }

    private Mono<AiProvider> noProviderAvailable() {
        log.error(
                "All providers "
                        + "unavailable");
        return Mono.error(
                new RuntimeException(
                        "No AI provider "
                                + "available.\n"
                                + "Fix: ollama serve"
                ));
    }
}