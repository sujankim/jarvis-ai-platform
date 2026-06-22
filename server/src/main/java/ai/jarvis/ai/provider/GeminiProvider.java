package ai.jarvis.ai.provider;

import ai.jarvis.tools.ToolRegistry;
import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gemini AI provider — cloud fallback.
 *
 * PHASE 4 UPDATE:
 * Now accepts ToolRegistry for tool-aware streaming.
 */
@Slf4j
@Component("gemini")
@ConditionalOnExpression(
        "T(org.springframework.util.StringUtils)"
                + ".hasText('${spring.ai.google.genai"
                + ".api-key:}')"
)
public class GeminiProvider implements AiProvider {

    private final String apiKey;
    private final String modelName;
    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public GeminiProvider(
            @Value("${spring.ai.google.genai.api-key:}")
            String apiKey,
            @Value("${spring.ai.google.genai.chat.model:"
                    + "gemini-2.0-flash}")
            String modelName,
            ToolRegistry toolRegistry) {

        this.apiKey = apiKey;
        this.modelName = modelName;
        this.toolRegistry = toolRegistry;

        if (apiKey != null && !apiKey.isBlank()) {

            Client genAiClient = Client.builder()
                    .apiKey(apiKey)
                    .build();

            GoogleGenAiChatModel chatModel =
                    GoogleGenAiChatModel.builder()
                            .genAiClient(genAiClient)
                            .defaultOptions(
                                    GoogleGenAiChatOptions
                                            .builder()
                                            .model(modelName)
                                            .temperature(0.7)
                                            .maxOutputTokens(2048)
                                            .build()
                            )
                            .build();

            this.chatClient = ChatClient
                    .builder(chatModel)
                    .build();

            log.info(
                    "GeminiProvider initialized: "
                            + "model={} tools={}",
                    modelName,
                    toolRegistry.count());

        } else {
            this.chatClient = null;
            log.debug(
                    "GeminiProvider: no API key");
        }
    }

    @Override
    public Flux<String> streamChat(Prompt prompt) {
        if (chatClient == null) {
            return Flux.error(new RuntimeException(
                    "Gemini API key not configured."));
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
        boolean hasKey = apiKey != null
                && !apiKey.isBlank()
                && chatClient != null;
        return Mono.just(hasKey);
    }

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}