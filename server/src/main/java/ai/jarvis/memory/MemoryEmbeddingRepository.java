package ai.jarvis.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.ResultSet;
import java.util.UUID;

/**
 * JDBC-based repository for pgvector operations.
 *
 * WHY JDBC NOT R2DBC:
 * R2DBC does not support PostgreSQL vector type natively.
 * JDBC can handle vector(768) via string formatting.
 * We use JdbcTemplate (from JdbcConfig) for all
 * vector read/write operations.
 *
 * ALL METHODS run on boundedElastic thread pool
 * because JDBC is blocking.
 * Wrapped in Mono.fromCallable for reactor integration.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MemoryEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Store embedding for an existing memory.
     *
     * CALLED BY: MemoryService.saveWithEmbedding()
     * after the memory record is already saved.
     *
     * FIX: Check affected row count.
     * If 0 rows updated = memory was deleted between
     * save() and storeEmbedding() (race condition).
     * Log warning instead of silently losing embedding.
     *
     * HOW VECTOR IS STORED:
     * pgvector accepts vectors as string: "[0.1,0.2,...]"
     * We format float[] to this string and use ::vector cast.
     *
     * @param memoryId  UUID of the memory to update
     * @param embedding float array from EmbeddingService
     */
    public Mono<Void> storeEmbedding(
            UUID memoryId,
            float[] embedding) {

        return Mono.fromCallable(() -> {
                    String vectorStr = toVectorString(embedding);

                    // Fix 1: capture row count to detect silent failures
                    int updated = jdbcTemplate.update(
                            "UPDATE memories "
                                    + "SET embedding = ?::vector, "
                                    + "    updated_at = NOW() "
                                    + "WHERE id = ?::uuid",
                            vectorStr,
                            memoryId.toString()
                    );

                    // Fix 1: warn when 0 rows affected
                    // (memory deleted between save and embed)
                    if (updated == 0) {
                        log.warn(
                                "Embedding not stored "
                                        + "(memory not found): memory={}",
                                memoryId);
                    } else {
                        log.debug(
                                "Stored embedding for memory={}",
                                memoryId);
                    }

                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(error -> {
                    log.warn(
                            "Failed to store embedding "
                                    + "for memory={}: {}",
                            memoryId,
                            error.getMessage());
                    // Never fail memory creation
                    // Embedding storage is best-effort
                    return Mono.empty();
                });
    }

    /**
     * Search memories by semantic similarity.
     *
     * USES: search_memories_by_embedding() SQL function
     * created in V11__add_embeddings_to_memories.sql
     *
     * RETURNS: memories ordered by cosine similarity.
     * Only returns memories above minSimilarity threshold.
     * Only returns memories that HAVE an embedding stored.
     *
     * @param userId         search only this user's memories
     * @param queryEmbedding embedding of the search query
     * @param limit          max results to return
     * @param minSimilarity  minimum cosine similarity (0.0-1.0)
     */
    public Flux<SemanticSearchResult> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int limit,
            double minSimilarity) {

        return Mono.fromCallable(() -> {
                    String vectorStr =
                            toVectorString(queryEmbedding);

                    return jdbcTemplate.query(
                            "SELECT * FROM "
                                    + "search_memories_by_embedding("
                                    + "?::uuid, ?::vector, ?, ?"
                                    + ")",
                            (rs, rowNum) -> mapRow(rs),
                            userId.toString(),
                            vectorStr,
                            limit,
                            minSimilarity
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(error -> {
                    log.warn(
                            "Semantic search failed for "
                                    + "user={}: {}",
                            userId,
                            error.getMessage());
                    return Flux.empty();
                });
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Convert float[] to pgvector string format.
     * pgvector accepts: "[0.1,0.2,0.3,...]"
     *
     * @param embedding float array to convert
     * @return vector string representation
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

    /**
     * Map SQL result row to SemanticSearchResult.
     * Matches columns returned by search function in V11.
     *
     * @param rs SQL ResultSet positioned at current row
     * @return SemanticSearchResult with all fields populated
     */
    private SemanticSearchResult mapRow(ResultSet rs)
            throws java.sql.SQLException {
        return new SemanticSearchResult(
                UUID.fromString(rs.getString("id")),
                MemoryType.valueOf(
                        rs.getString("type")),
                rs.getString("content"),
                rs.getDouble("importance"),
                rs.getInt("access_count"),
                rs.getDouble("similarity")
        );
    }

    // ── Result Record ─────────────────────────────

    /**
     * Result of a semantic similarity search.
     * Includes similarity score for logging/debugging.
     *
     * similarity: cosine similarity score (0.0-1.0)
     * 1.0 = identical meaning, 0.0 = completely unrelated
     */
    public record SemanticSearchResult(
            UUID id,
            MemoryType type,
            String content,
            double importance,
            int accessCount,
            double similarity) {}
}