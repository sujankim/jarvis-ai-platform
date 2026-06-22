package ai.jarvis.ai.provider;

import ai.jarvis.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Ollama AI provider — local primary provider.
 *
 * PHASE 4 UPDATE:
 * Now accepts ToolRegistry for tool-aware streaming.
 * When tools are registered, ChatClient includes them
 * in the request so the model can call them.
 */
@Slf4j
@Component
public class OllamaProvider implements AiProvider {

    private final ChatClient chatClient;
    private final WebClient webClient;
    private final String modelName;
    private final String baseUrl;
    private final ToolRegistry toolRegistry;

    public OllamaProvider(
            OllamaChatModel ollamaChatModel,
            WebClient.Builder webClientBuilder,
            ToolRegistry toolRegistry,
            @Value("${spring.ai.ollama.chat.model:"
                    + "llama3.1:8b}") String modelName,
            @Value("${spring.ai.ollama.base-url:"
                    + "http://localhost:11434}")
            String baseUrl) {

        this.toolRegistry = toolRegistry;
        this.modelName = modelName;
        this.baseUrl = baseUrl;

        this.chatClient = ChatClient
                .builder(ollamaChatModel)
                .build();

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();

        log.info(
                "OllamaProvider initialized: "
                        + "model={} url={} tools={}",
                modelName, baseUrl,
                toolRegistry.count());
    }

    @Override
    public Flux<String> streamChat(Prompt prompt) {
        // PHASE 4: Register tools if available
        if (toolRegistry.hasTools()) {
            return chatClient
                    .prompt(prompt)
                    .tools(toolRegistry.asArray())
                    .stream()
                    .content()
                    .filter(token ->
                            token != null
                                    && !token.isEmpty());
        }

        // No tools — standard streaming
        return chatClient
                .prompt(prompt)
                .stream()
                .content()
                .filter(token ->
                        token != null
                                && !token.isEmpty());
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return webClient
                .get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(response -> true)
                .onErrorReturn(false)
                .doOnNext(available -> {
                    if (!available) {
                        log.warn(
                                "Ollama unavailable at {}",
                                baseUrl);
                    }
                });
    }

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}