package ai.jarvis.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generate embedding for a single text.
     * Ollama call is blocking → boundedElastic thread.
     */
    public Mono<float[]> embed(String text) {
        // validate input before processing
        if (text == null || text.isEmpty()) {
            log.debug("Skipping embedding: null or empty text");
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
                    log.debug("Embedding: {}...",
                            text.substring(0,
                                    Math.min(50, text.length())));

                    EmbeddingRequest request =
                            new EmbeddingRequest(
                                    List.of(text), null);

                    float[] vector = embeddingModel
                            .call(request)
                            .getResults()
                            .stream()
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Embedding model returned "
                                                    + "no results for text: "
                                                    + text.substring(0,
                                                    Math.min(30, text.length()))
                                    ))
                            .getOutput();

                    log.debug("Generated {} dimensions",
                            vector.length);

                    return vector;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.error("Embedding failed: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Generate embeddings for multiple texts at once.
     */
    public Mono<List<float[]>> embedAll(List<String> texts) {
        // Fix: validate input
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(() -> {
                    EmbeddingRequest request =
                            new EmbeddingRequest(texts, null);

                    return embeddingModel
                            .call(request)
                            .getResults()
                            .stream()
                            .map(e -> e.getOutput())
                            .toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.error("Batch embedding failed: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Convert float[] to pgvector string format.
     * Format: "[0.1,0.2,0.3,...]"
     */
    public String toVectorString(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) {
                sb.append(",");
            }
        }
        return sb.append("]").toString();
    }
}