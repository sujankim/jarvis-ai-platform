package ai.jarvis.rag;

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
 * JDBC repository for RAG semantic search.
 *
 * WHY JDBC NOT R2DBC:
 * R2DBC cannot handle PostgreSQL vector type.
 * We use the search_chunks_by_embedding() function
 * created in V14__create_document_chunks.sql.
 *
 * Same pattern as MemoryEmbeddingRepository (Phase 2).
 * All operations run on boundedElastic thread pool.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RagSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Search document chunks by semantic similarity.
     *
     * Uses search_chunks_by_embedding() SQL function
     * from V14 migration. Returns chunks ordered by
     * cosine similarity (most relevant first).
     *
     * @param userId        search only this user's docs
     * @param queryEmbedding embedding of the user query
     * @param limit          max chunks to return
     * @param minSimilarity  minimum cosine similarity
     * @param documentId     optional: filter to one doc
     */
    public Flux<RagSearchResult> searchSimilar(
            UUID userId,
            float[] queryEmbedding,
            int limit,
            double minSimilarity,
            UUID documentId) {

        return Mono.fromCallable(() -> {
                    String vectorStr =
                            toVectorString(queryEmbedding);

                    return jdbcTemplate.query(
                            "SELECT c.id, c.document_id, "
                                    + "d.filename, "
                                    + "c.content, "
                                    + "c.chunk_index, "
                                    + "c.page_number, "
                                    + "1 - (c.embedding <=> "
                                    + "?::vector) AS similarity "
                                    + "FROM document_chunks c "
                                    + "JOIN documents d "
                                    + "ON c.document_id = d.id "
                                    + "WHERE c.user_id = ?::uuid "
                                    + "AND c.embedding IS NOT NULL "
                                    + "AND d.status = 'READY' "
                                    + "AND 1 - (c.embedding <=> "
                                    + "?::vector) >= ? "
                                    + (documentId != null
                                    ? "AND c.document_id "
                                      + "= ?::uuid " : "")
                                    + "ORDER BY c.embedding "
                                    + "<=> ?::vector ASC "
                                    + "LIMIT ?",
                            (rs, rowNum) -> mapRow(rs),
                            vectorStr,
                            userId.toString(),
                            vectorStr,
                            minSimilarity,
                            documentId != null
                                    ? documentId.toString()
                                    : null,
                            vectorStr,
                            limit
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(error -> {
                    log.warn(
                            "RAG search failed "
                                    + "for user={}: {}",
                            userId,
                            error.getMessage());
                    return Flux.empty();
                });
    }

    // ── Private Helpers ───────────────────────────

    private RagSearchResult mapRow(ResultSet rs)
            throws java.sql.SQLException {

        return new RagSearchResult(
                UUID.fromString(
                        rs.getString("id")),
                UUID.fromString(
                        rs.getString("document_id")),
                rs.getString("filename"),
                rs.getString("content"),
                rs.getInt("chunk_index"),
                rs.getObject("page_number") != null
                        ? rs.getInt("page_number")
                        : null,
                rs.getDouble("similarity")
        );
    }

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