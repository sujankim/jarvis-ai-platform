package ai.jarvis.rag;

import ai.jarvis.rag.extraction.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core document processing service.
 *
 * RESPONSIBILITIES:
 * 1. Select correct TextExtractor for file type
 * 2. Extract clean text from raw content
 * 3. Split text into overlapping chunks
 * 4. Save chunks to DB via R2DBC
 * 5. Update document status
 *
 * DOES NOT:
 * Generate embeddings — that is DocumentEmbeddingService.
 * This separation keeps each service focused.
 *
 * CHUNKING STRATEGY:
 * chunk_size    = 500 tokens (~375 words)
 * chunk_overlap = 50 tokens  (~37 words)
 * Overlap preserves context at boundaries.
 */
@Slf4j
@Service
public class DocumentProcessingService {

    // ── Constants ─────────────────────────────────

    /**
     * Target token count per chunk.
     * 500 tokens ≈ 375 words ≈ ~2000 chars.
     * Leaves room in 8000 token context window
     * for multiple chunks + memory + history.
     */
    private static final int CHUNK_SIZE_TOKENS = 500;

    /**
     * Overlap between consecutive chunks.
     * 50 tokens ≈ 2-3 sentences.
     * Prevents losing context at boundaries.
     */
    private static final int CHUNK_OVERLAP_TOKENS = 50;

    /**
     * Characters per token estimate.
     * English average: ~4 chars per token.
     * Used for fast estimation without tokenizer.
     */
    private static final int CHARS_PER_TOKEN = 4;

    // ── Dependencies ──────────────────────────────

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final List<TextExtractor> extractors;

    /**
     * Constructor injection.
     * Spring injects ALL TextExtractor beans as list.
     * Adding a new extractor = just add @Component.
     */
    public DocumentProcessingService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            R2dbcEntityTemplate r2dbcEntityTemplate,
            List<TextExtractor> extractors) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
        this.extractors = extractors;

        log.info(
                "DocumentProcessingService initialized "
                        + "with {} extractors",
                extractors.size());
    }

    // ── Public API ────────────────────────────────

    /**
     * Process a document: extract → chunk → save.
     *
     * FLOW:
     * 1. Mark document as PROCESSING
     * 2. Extract text via TextExtractor
     * 3. Split into overlapping chunks
     * 4. Save all chunks to DB
     * 5. Mark document as READY
     *
     * On error: mark document as FAILED.
     *
     * @param documentId  which document to process
     * @param userId      owner (for chunk ownership)
     * @param rawText     raw file content
     * @param fileType    determines which extractor
     * @return processed Document in READY or FAILED status
     */
    public Mono<Document> processDocument(
            UUID documentId,
            UUID userId,
            String rawText,
            DocumentFileType fileType) {

        log.info(
                "Processing document: id={} type={} "
                        + "chars={}",
                documentId,
                fileType,
                rawText != null
                        ? rawText.length() : 0);

        return documentRepository
                .findByIdAndUserId(documentId, userId)
                .switchIfEmpty(Mono.error(
                        new RuntimeException(
                                "Document not found: "
                                        + documentId)))
                // Step 1: Mark as PROCESSING
                .flatMap(doc ->
                        r2dbcEntityTemplate
                                .update(doc.withProcessing())
                )
                // Step 2 & 3: Extract + chunk
                .flatMap(doc -> {
                    try {
                        String cleanText =
                                extractText(rawText, fileType);
                        List<String> chunks =
                                splitIntoChunks(cleanText);

                        log.info(
                                "Split into {} chunks: id={}",
                                chunks.size(), documentId);

                        // Step 4: Save all chunks
                        return saveChunks(
                                documentId,
                                userId,
                                chunks,
                                fileType)
                                .thenReturn(
                                        new ChunkResult(
                                                doc, chunks.size()));

                    } catch (Exception e) {
                        log.error(
                                "Extraction failed: "
                                        + "id={} error={}",
                                documentId,
                                e.getMessage());
                        return Mono.error(e);
                    }
                })
                // Step 5: Mark as READY
                .flatMap(result ->
                        r2dbcEntityTemplate
                                .update(result.document()
                                        .withReady(
                                                result.chunkCount()))
                                .doOnSuccess(d ->
                                        log.info(
                                                "Document READY: "
                                                        + "id={} chunks={}",
                                                documentId,
                                                result.chunkCount()))
                )
                // On any error: mark as FAILED
                .onErrorResume(error -> {
                    log.error(
                            "Document processing FAILED: "
                                    + "id={} error={}",
                            documentId,
                            error.getMessage());

                    return documentRepository
                            .findByIdAndUserId(
                                    documentId, userId)
                            .flatMap(doc ->
                                    r2dbcEntityTemplate
                                            .update(doc.withFailed(
                                                    error.getMessage()))
                            );
                });
    }

    // ── Package-visible for testing ────────────────

    /**
     * Extract text using the correct extractor.
     * Falls back to raw text if no extractor found.
     */
    String extractText(
            String rawText,
            DocumentFileType fileType) {

        return extractors.stream()
                .filter(e -> e.supports(fileType))
                .findFirst()
                .map(e -> e.extract(rawText))
                .orElseGet(() -> {
                    log.warn(
                            "No extractor for type={}, "
                                    + "using raw text",
                            fileType);
                    return rawText != null
                            ? rawText.trim() : "";
                });
    }

    /**
     * Split text into overlapping chunks.
     *
     * ALGORITHM:
     * 1. Split into words
     * 2. Slide window of CHUNK_SIZE words
     * 3. Each window overlaps by CHUNK_OVERLAP words
     * 4. Skip chunks that are too short (< 10 words)
     *
     * WHY WORD-BASED NOT CHAR-BASED:
     * Words map better to tokens than characters.
     * Chunks split at word boundaries = cleaner text.
     *
     * @param text clean extracted text
     * @return list of chunk strings
     */
    List<String> splitIntoChunks(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Convert token targets to word counts
        // 1 token ≈ 0.75 words (English average)
        int wordsPerChunk =
                (int) (CHUNK_SIZE_TOKENS * 0.75);
        int overlapWords =
                (int) (CHUNK_OVERLAP_TOKENS * 0.75);

        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < words.length) {
            int end = Math.min(
                    start + wordsPerChunk,
                    words.length);

            // Build chunk from words[start..end]
            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    chunk.append(" ");
                }
                chunk.append(words[i]);
            }

            String chunkText = chunk.toString().trim();

            // Skip chunks that are too short
            // (< 10 words = not meaningful)
            if (chunkText.split("\\s+").length >= 10) {
                chunks.add(chunkText);
            }

            // Move forward by chunk size minus overlap
            start += wordsPerChunk - overlapWords;

            // Safety: prevent infinite loop
            if (wordsPerChunk <= overlapWords) {
                break;
            }
        }

        return chunks;
    }

    /**
     * Estimate token count for a text string.
     * Simple estimation: characters / 4.
     * Good enough for chunk size management.
     *
     * @param text the text to estimate
     * @return estimated token count
     */
    int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1,
                text.length() / CHARS_PER_TOKEN);
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Save all chunks to DB using R2DBC.
     * Sequential saves to preserve chunk ordering.
     * concatMap = sequential (not parallel).
     */
    private Mono<Void> saveChunks(
            UUID documentId,
            UUID userId,
            List<String> chunks,
            DocumentFileType fileType) {

        return Flux.range(0, chunks.size())
                .concatMap(index -> {
                    String content = chunks.get(index);
                    int tokens = estimateTokens(content);

                    DocumentChunk chunk =
                            DocumentChunk.create(
                                    documentId,
                                    userId,
                                    content,
                                    index,
                                    null, // page unknown for now
                                    tokens
                            );

                    return r2dbcEntityTemplate
                            .insert(chunk)
                            .doOnSuccess(saved ->
                                    log.debug(
                                            "Saved chunk {}/{}: "
                                                    + "id={}",
                                            index + 1,
                                            chunks.size(),
                                            saved.id()))
                            .then();
                })
                .then();
    }

    // ── Private Records ───────────────────────────

    private record ChunkResult(
            Document document,
            int chunkCount) {}
}