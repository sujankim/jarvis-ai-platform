package ai.jarvis.rag;

import java.util.UUID;

/**
 * Result of a RAG semantic search.
 *
 * Contains everything needed to:
 * 1. Inject content into AI prompt
 * 2. Show source citation to user
 * 3. Debug search quality (similarity score)
 *
 * similarity: cosine similarity (0.0 - 1.0)
 * 1.0 = identical meaning
 * 0.5 = somewhat related (our min threshold)
 * 0.0 = completely unrelated
 */
public record RagSearchResult(

        UUID chunkId,

        UUID documentId,

        String filename,

        String content,

        int chunkIndex,

        Integer pageNumber,

        double similarity

) {
    /**
     * Format for display in CLI or API.
     * Shows filename + page for source citation.
     */
    public String sourceLabel() {
        if (pageNumber != null && pageNumber > 0) {
            return filename + " (page " + pageNumber + ")";
        }
        return filename + " (chunk " + (chunkIndex + 1) + ")";
    }
}