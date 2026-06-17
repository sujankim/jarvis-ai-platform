package ai.jarvis.ai.provider;

import com.google.genai.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gemini AI provider — cloud fallback.
 *
 * ACTIVATION FIX (CodeRabbit Issue #2):
 * @ConditionalOnProperty allows blank string values —
 * the property EXISTS even when GEMINI_API_KEY is empty.
 * This prevented GeminiUnavailableProvider from creating.
 *
 * @ConditionalOnExpression uses StringUtils.hasText()
 * which returns FALSE for null, empty, AND blank strings.
 * This correctly activates only when key has real content.
 *
 * SECURITY FIX (CodeRabbit Issue #3):
 * Removed debug log that leaked API key prefix/length.
 * Never log credential fragments — use generic message.
 */
@Slf4j
@Component
@ConditionalOnExpression(
        "T(org.springframework.util.StringUtils)"
                + ".hasText('${spring.ai.google.genai"
                + ".api-key:}')"
)
public class GeminiProvider implements AiProvider {

    private final String apiKey;
    private final String modelName;
    private final ChatClient chatClient;

    public GeminiProvider(
            @Value("${spring.ai.google.genai.api-key:}")
            String apiKey,
            @Value("${spring.ai.google.genai.chat.model:"
                    + "gemini-2.0-flash}")
            String modelName) {

        this.apiKey = apiKey;
        this.modelName = modelName;

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

            // FIX: Generic message — never log key content
            // CodeRabbit Issue #3: remove prefix/length log
            log.info(
                    "GeminiProvider initialized: model={}",
                    modelName);

        } else {
            this.chatClient = null;
            log.debug(
                    "GeminiProvider: no API key configured");
        }
    }

    @Override
    public Flux<String> streamChat(Prompt prompt) {
        if (chatClient == null) {
            return Flux.error(new RuntimeException(
                    "Gemini API key not configured. "
                            + "Set GEMINI_API_KEY in environment."));
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