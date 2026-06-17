package ai.jarvis.rag;

import ai.jarvis.memory.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates and stores embeddings for document chunks.
 *
 * CALLED BY:
 * DocumentProcessingService after chunks are saved.
 * Runs ASYNCHRONOUSLY — document is READY before
 * embeddings complete.
 *
 * RESILIENCE:
 * If one chunk embedding fails → log warning, continue.
 * Document stays READY — text search still works.
 * Missing embeddings = chunk skipped in semantic search.
 *
 * REUSES EmbeddingService from Phase 2:
 * Same nomic-embed-text model.
 * Same 768-dimension output.
 * Same boundedElastic thread scheduling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkEmbeddingRepository
            chunkEmbeddingRepository;

    /**
     * Generate and store embeddings for all chunks
     * of a document.
     *
     * FLOW per chunk:
     * 1. Load chunk from DB
     * 2. Generate embedding via Ollama
     * 3. Store embedding via JDBC
     *
     * WHY concatMap not flatMap:
     * concatMap = sequential processing.
     * Prevents Ollama from being overwhelmed
     * with too many concurrent embedding requests.
     *
     * @param documentId which document's chunks to embed
     * @param userId     owner (for security)
     * @return Mono<Void> completes when all done
     */
    public Mono<Void> embedAllChunks(
            UUID documentId,
            UUID userId) {

        AtomicInteger successCount =
                new AtomicInteger(0);
        AtomicInteger failCount =
                new AtomicInteger(0);

        log.info(
                "Starting embedding: document={}",
                documentId);

        return chunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(
                        documentId)
                .concatMap(chunk ->
                        embedSingleChunk(chunk)
                                .doOnSuccess(v ->
                                        successCount
                                                .incrementAndGet())
                                .onErrorResume(error -> {
                                    failCount
                                            .incrementAndGet();
                                    log.warn(
                                            "Chunk embed failed "
                                                    + "chunk={}: {}",
                                            chunk.id(),
                                            error.getMessage());
                                    // Continue with next chunk
                                    return Mono.empty();
                                })
                )
                .then()
                .doOnSuccess(v ->
                        log.info(
                                "Embedding complete: "
                                        + "document={} "
                                        + "success={} failed={}",
                                documentId,
                                successCount.get(),
                                failCount.get()))
                .onErrorResume(error -> {
                    log.error(
                            "Embedding pipeline failed: "
                                    + "document={} error={}",
                            documentId,
                            error.getMessage());
                    // Never propagate — embedding is
                    // best-effort, document stays READY
                    return Mono.empty();
                });
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Generate and store embedding for one chunk.
     * Runs on boundedElastic (Ollama call is blocking).
     *
     * @param chunk the chunk to embed
     * @return Mono<Void> completes when stored
     */
    private Mono<Void> embedSingleChunk(
            DocumentChunk chunk) {

        return embeddingService
                .embed(chunk.content())
                .flatMap(embedding ->
                        chunkEmbeddingRepository
                                .storeEmbedding(
                                        chunk.id(),
                                        embedding))
                .doOnSuccess(v ->
                        log.debug(
                                "Embedded chunk: "
                                        + "id={} index={}",
                                chunk.id(),
                                chunk.chunkIndex()))
                .subscribeOn(
                        Schedulers.boundedElastic());
    }
}