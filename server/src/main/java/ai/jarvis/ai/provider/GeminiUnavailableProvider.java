package ai.jarvis.ai.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fallback when Gemini is NOT configured.
 * Created when GeminiProvider bean does NOT exist.
 *
 * WHY: ProviderRouter needs exactly one gemini AiProvider.
 * This ensures ProviderRouter always starts successfully
 * even when GEMINI_API_KEY is not set.
 */
@Slf4j
@Component("gemini")
@ConditionalOnExpression(
        "!T(org.springframework.util.StringUtils)"
                + ".hasText('${spring.ai.google.genai"
                + ".api-key:}')"
)
public class GeminiUnavailableProvider
        implements AiProvider {

    public GeminiUnavailableProvider() {
        log.info(
                "Gemini not configured. "
                        + "Set GEMINI_API_KEY in .env "
                        + "to enable cloud fallback.");
    }

    @Override
    public Flux<String> streamChat(Prompt prompt) {
        return Flux.error(new RuntimeException(
                "Gemini not configured. "
                        + "Add GEMINI_API_KEY to .env"));
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return Mono.just(false);
    }

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    public String getModelName() {
        return "not-configured";
    }
}
