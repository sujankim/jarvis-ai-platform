package ai.jarvis.rag.extraction;

import ai.jarvis.rag.DocumentFileType;

/**
 * Contract for document text extractors.
 *
 * STRATEGY PATTERN:
 * DocumentProcessingService selects the correct
 * extractor based on DocumentFileType.
 * New file types = new implementation, zero changes
 * to existing code (Open/Closed Principle).
 *
 * IMPLEMENTATIONS:
 * PlainTextExtractor → TXT files (Core)
 * MarkdownExtractor  → MARKDOWN files (Core)
 * PdfTextExtractor   → PDF files (Contributor)
 */
public interface TextExtractor {

    /**
     * Extract plain text from raw file content.
     *
     * @param rawText raw file content as string
     * @return clean plain text ready for chunking
     */
    String extract(String rawText);

    /**
     * Check if this extractor handles a file type.
     *
     * @param fileType the document file type
     * @return true if this extractor handles it
     */
    boolean supports(DocumentFileType fileType);
}