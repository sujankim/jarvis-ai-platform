package ai.jarvis.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component("ollama")
@RequiredArgsConstructor
public class OllamaHealthIndicator
        implements ReactiveHealthIndicator {

    private final WebClient.Builder webClientBuilder;

    private static final String OLLAMA_URL =
            "http://localhost:11434";
    private static final Duration TIMEOUT =
            Duration.ofSeconds(3);

    @Override
    public Mono<Health> health() {
        return webClientBuilder
                .baseUrl(OLLAMA_URL)
                .build()
                .get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(OllamaTagsResponse.class)
                .timeout(TIMEOUT)
                .map(response -> {
                    int modelCount = response.models() != null
                            ? response.models().size() : 0;
                    return Health.up()
                            .withDetail("url", OLLAMA_URL)
                            .withDetail("models", modelCount)
                            .build();
                })
                .onErrorResume(error -> {
                    log.warn("Ollama health check failed: {}",
                            error.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("url", OLLAMA_URL)
                            .withDetail("error",
                                    error.getMessage())
                            .withDetail("fix",
                                    "Run: ollama serve")
                            .build());
                });
    }

    // Simple response record for /api/tags
    private record OllamaTagsResponse(
            java.util.List<Object> models) {}
}