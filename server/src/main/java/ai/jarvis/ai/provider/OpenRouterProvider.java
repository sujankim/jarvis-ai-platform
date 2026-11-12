package ai.jarvis.ai.provider;

import ai.jarvis.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OpenRouter AI provider — second cloud fallback.
 *
 * OpenRouter exposes an OpenAI-compatible API, so this
 * reuses Spring AI's OpenAI chat model pointed at the
 * OpenRouter base URL.
 *
 * Only created when OPENROUTER_API_KEY is set.
 *
 * Accepts ToolRegistry for tool-aware streaming
 * (same pattern as OllamaProvider and GeminiProvider).
 */
@Slf4j
@Component("openrouter")
@ConditionalOnExpression(
        "T(org.springframework.util.StringUtils)"
                + ".hasText('${OPENROUTER_API_KEY:}')"
)
public class OpenRouterProvider implements AiProvider {

    static final String DEFAULT_BASE_URL =
            "https://openrouter.ai/api/v1";

    // Free-models router: picks a free model that supports
    // what the request needs (e.g. tool calling).
    static final String DEFAULT_MODEL = "openrouter/free";

    private final String modelName;
    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public OpenRouterProvider(
            @Value("${OPENROUTER_API_KEY:}")
            String apiKey,
            @Value("${OPENROUTER_BASE_URL:"
                    + DEFAULT_BASE_URL + "}")
            String baseUrl,
            @Value("${OPENROUTER_MODEL:"
                    + DEFAULT_MODEL + "}")
            String modelName,
            ToolRegistry toolRegistry) {

        // An empty value in .env (OPENROUTER_MODEL=) beats
        // the placeholder default, so fall back explicitly.
        this.modelName = StringUtils.hasText(modelName)
                ? modelName
                : DEFAULT_MODEL;
        this.toolRegistry = toolRegistry;

        if (StringUtils.hasText(apiKey)) {

            OpenAiChatModel chatModel =
                    OpenAiChatModel.builder()
                            .options(
                                    OpenAiChatOptions
                                            .builder()
                                            .apiKey(apiKey)
                                            .baseUrl(
                                                    StringUtils.hasText(baseUrl)
                                                            ? baseUrl
                                                            : DEFAULT_BASE_URL)
                                            .model(this.modelName)
                                            .build()
                            )
                            .build();

            this.chatClient = ChatClient
                    .builder(chatModel)
                    .build();

            log.info(
                    "OpenRouterProvider initialized: "
                            + "model={} tools={}",
                    this.modelName,
                    toolRegistry.count());

        } else {
            this.chatClient = null;
            log.debug(
                    "OpenRouterProvider: no API key");
        }
    }

    @Override
    public Flux<String> streamChat(Prompt prompt) {
        if (chatClient == null) {
            return Flux.error(new RuntimeException(
                    "OpenRouter API key not configured."));
        }

        // PHASE 4: Register tools if available
        if (toolRegistry.hasTools()) {
            return chatClient
                    .prompt(prompt)
                    .tools(toolRegistry.asArray())
                    .stream()
                    .content();
        }

        return chatClient
                .prompt(prompt)
                .stream()
                .content();
    }

    @Override
    public Mono<Boolean> isAvailable() {
        // No network call: available means "a key is set".
        return Mono.just(chatClient != null);
    }

    @Override
    public String getName() {
        return "openrouter";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}