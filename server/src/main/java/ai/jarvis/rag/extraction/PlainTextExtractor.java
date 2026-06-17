package ai.jarvis.rag.extraction;

import ai.jarvis.rag.DocumentFileType;
import org.springframework.stereotype.Component;

/**
 * Text extractor for plain .txt files.
 *
 * Plain text needs no transformation —
 * return the content with normalized whitespace.
 *
 * NORMALIZATIONS APPLIED:
 * 1. Windows line endings → Unix (CRLF → LF)
 * 2. Excessive blank lines → single blank line
 * 3. Leading/trailing whitespace trimmed
 *
 * WHY NORMALIZE:
 * Consistent text = better chunking boundaries.
 * Removes noise that wastes tokens.
 */
@Component
public class PlainTextExtractor
        implements TextExtractor {

    @Override
    public String extract(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        return rawText
                // Normalize Windows line endings
                .replace("\r\n", "\n")
                // Normalize old Mac line endings
                .replace("\r", "\n")
                // Collapse 3+ blank lines to 2
                .replaceAll("\n{3,}", "\n\n")
                // Trim
                .trim();
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.TXT;
    }
}