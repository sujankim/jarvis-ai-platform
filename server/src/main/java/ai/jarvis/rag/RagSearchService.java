package ai.jarvis.rag;

import ai.jarvis.memory.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Semantic search over uploaded documents.
 *
 * FLOW:
 * 1. Embed user query via nomic-embed-text
 * 2. Search document_chunks by cosine similarity
 * 3. Format results for prompt injection
 *
 * REUSES EmbeddingService from Phase 2.
 * Same Ollama model, same 768-dim vectors.
 *
 * CALLED BY: AiOrchestrator (loaded in parallel
 * with session history and long-term memories).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final EmbeddingService embeddingService;
    private final RagSearchRepository ragSearchRepository;

    /**
     * Max chunks to inject into one prompt.
     * 3 chunks × 500 tokens = 1500 tokens max.
     * Leaves room for history + memory + response.
     */
    private static final int MAX_CHUNKS = 3;

    /**
     * Minimum cosine similarity to include a chunk.
     * Below 0.5 = probably not relevant to query.
     */
    private static final double MIN_SIMILARITY = 0.5;

    /**
     * Search documents and format for prompt injection.
     *
     * Returns empty string if:
     * - userId is null
     * - No documents uploaded yet
     * - No chunks above similarity threshold
     * - Any error occurs (graceful degradation)
     *
     * @param userId    whose documents to search
     * @param userQuery the user's current message
     * @return formatted RAG context string for prompt
     */
    public Mono<String> formatForPrompt(
            UUID userId,
            String userQuery) {

        if (userId == null
                || userQuery == null
                || userQuery.isBlank()) {
            return Mono.just("");
        }

        return embeddingService
                .embed(userQuery)
                .flatMap(embedding ->
                        ragSearchRepository
                                .searchSimilar(
                                        userId,
                                        embedding,
                                        MAX_CHUNKS,
                                        MIN_SIMILARITY,
                                        null // all documents
                                )
                                .collectList()
                )
                .map(this::formatResults)
                .onErrorReturn("")
                .defaultIfEmpty("");
    }

    /**
     * Search within a specific document only.
     * Used when user explicitly references a doc.
     *
     * @param userId     document owner
     * @param userQuery  the user's current message
     * @param documentId limit search to this document
     * @return formatted RAG context string
     */
    public Mono<String> formatForPrompt(
            UUID userId,
            String userQuery,
            UUID documentId) {

        if (userId == null
                || userQuery == null
                || userQuery.isBlank()) {
            return Mono.just("");
        }

        return embeddingService
                .embed(userQuery)
                .flatMap(embedding ->
                        ragSearchRepository
                                .searchSimilar(
                                        userId,
                                        embedding,
                                        MAX_CHUNKS,
                                        MIN_SIMILARITY,
                                        documentId
                                )
                                .collectList()
                )
                .map(this::formatResults)
                .onErrorReturn("")
                .defaultIfEmpty("");
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Format search results for prompt injection.
     *
     * Empty list → empty string (no injection).
     * Non-empty → structured context block with
     * source citations for each chunk.
     */
    private String formatResults(
            List<RagSearchResult> results) {

        if (results.isEmpty()) {
            return "";
        }

        log.debug(
                "Formatting {} RAG results for prompt",
                results.size());

        StringBuilder sb = new StringBuilder();
        sb.append(
                "=== RELEVANT DOCUMENT EXCERPTS ===\n");
        sb.append(
                "The following excerpts from your "
                        + "uploaded documents are relevant "
                        + "to the current question.\n\n");

        for (int i = 0;
             i < results.size(); i++) {

            RagSearchResult result = results.get(i);

            log.debug(
                    "RAG chunk: similarity={} source={}",
                    String.format(
                            "%.2f", result.similarity()),
                    result.sourceLabel());

            sb.append("--- Source: ")
                    .append(result.sourceLabel())
                    .append(" ---\n")
                    .append(result.content())
                    .append("\n\n");
        }

        sb.append(
                "=== END DOCUMENT EXCERPTS ===");

        return sb.toString();
    }
}