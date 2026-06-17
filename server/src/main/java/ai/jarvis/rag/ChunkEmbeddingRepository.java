package ai.jarvis.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * JDBC repository for document chunk embeddings.
 *
 * WHY JDBC NOT R2DBC:
 * R2DBC does not support PostgreSQL vector type.
 * JDBC handles vector(768) via string casting.
 * Exact same pattern as MemoryEmbeddingRepository.
 *
 * ALL METHODS run on boundedElastic thread pool
 * because JDBC is blocking.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChunkEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Store embedding for a document chunk.
     *
     * Called by DocumentEmbeddingService after
     * generating embedding from nomic-embed-text.
     *
     * @param chunkId   UUID of the chunk to update
     * @param embedding float[] from EmbeddingService
     */
    public Mono<Void> storeEmbedding(
            UUID chunkId,
            float[] embedding) {

        return Mono.fromCallable(() -> {
                    String vectorStr =
                            toVectorString(embedding);

                    int updated = jdbcTemplate.update(
                            "UPDATE document_chunks "
                                    + "SET embedding = ?::vector "
                                    + "WHERE id = ?::uuid",
                            vectorStr,
                            chunkId.toString()
                    );

                    if (updated == 0) {
                        log.warn(
                                "Embedding not stored "
                                        + "(chunk not found): {}",
                                chunkId);
                    } else {
                        log.debug(
                                "Stored embedding "
                                        + "for chunk={}",
                                chunkId);
                    }

                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(error -> {
                    log.warn(
                            "Failed to store embedding "
                                    + "for chunk={}: {}",
                            chunkId,
                            error.getMessage());
                    // Never fail — embedding is best-effort
                    return Mono.empty();
                });
    }

    /**
     * Convert float[] to pgvector string format.
     * Format: "[0.1,0.2,0.3,...]"
     *
     * @param embedding float array to convert
     * @return pgvector-compatible string
     */
    private String toVectorString(float[] embedding) {
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