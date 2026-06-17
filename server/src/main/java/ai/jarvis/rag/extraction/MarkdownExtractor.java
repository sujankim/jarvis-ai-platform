package ai.jarvis.rag.extraction;

import ai.jarvis.rag.DocumentFileType;
import org.springframework.stereotype.Component;

/**
 * Text extractor for Markdown (.md) files.
 *
 * Strips Markdown syntax so the AI receives
 * clean prose, not formatting characters.
 *
 * WHAT IS STRIPPED:
 * - Headers:       ## Title    → Title
 * - Bold:          **text**    → text
 * - Italic:        *text*      → text
 * - Code blocks:   ```code```  → code
 * - Inline code:   `code`      → code
 * - Links:         [text](url) → text
 * - Images:        ![alt](url) → alt
 * - Blockquotes:   > text      → text
 * - Horizontal rules: ---      → (removed)
 *
 * WHY STRIP:
 * AI understands prose better than Markdown syntax.
 * Raw ** and ## waste tokens in context window.
 */
@Component
public class MarkdownExtractor
        implements TextExtractor {

    @Override
    public String extract(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        return rawText
                // Normalize line endings
                .replace("\r\n", "\n")
                .replace("\r", "\n")

                // Remove fenced code blocks (```...```)
                // Keep code content, remove fences
                .replaceAll(
                        "```[a-zA-Z]*\\n",
                        "")
                .replaceAll(
                        "```",
                        "")

                // Remove headers (#, ##, ###, etc.)
                .replaceAll(
                        "(?m)^#{1,6}\\s+",
                        "")

                // Remove bold (**text** or __text__)
                .replaceAll(
                        "\\*\\*(.+?)\\*\\*",
                        "$1")
                .replaceAll(
                        "__(.+?)__",
                        "$1")

                // Remove italic (*text* or _text_)
                .replaceAll(
                        "\\*(.+?)\\*",
                        "$1")
                .replaceAll(
                        "_(.+?)_",
                        "$1")

                // Remove inline code (`code`)
                .replaceAll(
                        "`(.+?)`",
                        "$1")

                // Convert links [text](url) → text
                .replaceAll(
                        "\\[([^\\]]+)\\]"
                                + "\\([^)]*\\)",
                        "$1")

                // Convert images ![alt](url) → alt
                .replaceAll(
                        "!\\[([^\\]]*)\\]"
                                + "\\([^)]*\\)",
                        "$1")

                // Remove blockquote markers
                .replaceAll(
                        "(?m)^>\\s*",
                        "")

                // Remove horizontal rules
                .replaceAll(
                        "(?m)^[-*_]{3,}\\s*$",
                        "")

                // Remove HTML tags if any
                .replaceAll(
                        "<[^>]+>",
                        "")

                // Collapse 3+ blank lines → 2
                .replaceAll(
                        "\n{3,}",
                        "\n\n")

                .trim();
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.MARKDOWN;
    }
}