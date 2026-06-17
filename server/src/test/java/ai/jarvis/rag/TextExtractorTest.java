package ai.jarvis.rag;

import ai.jarvis.rag.extraction.MarkdownExtractor;
import ai.jarvis.rag.extraction.PlainTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TextExtractor Tests")
class TextExtractorTest {

    private final PlainTextExtractor plainExtractor =
            new PlainTextExtractor();
    private final MarkdownExtractor markdownExtractor =
            new MarkdownExtractor();

    // ── PlainTextExtractor ────────────────────────

    @Test
    @DisplayName("PlainText: normalizes CRLF to LF")
    void plainShouldNormalizeCRLF() {
        String result = plainExtractor
                .extract("line1\r\nline2\r\nline3");
        assertThat(result)
                .isEqualTo("line1\nline2\nline3");
    }

    @Test
    @DisplayName("PlainText: collapses multiple blank lines")
    void plainShouldCollapseBlankLines() {
        String result = plainExtractor
                .extract("para1\n\n\n\n\npara2");
        assertThat(result)
                .isEqualTo("para1\n\npara2");
    }

    @Test
    @DisplayName("PlainText: handles null input")
    void plainShouldHandleNull() {
        assertThat(plainExtractor.extract(null))
                .isEqualTo("");
    }

    @Test
    @DisplayName("PlainText: supports TXT type")
    void plainShouldSupportTxt() {
        assertThat(plainExtractor
                .supports(DocumentFileType.TXT))
                .isTrue();
        assertThat(plainExtractor
                .supports(DocumentFileType.PDF))
                .isFalse();
        assertThat(plainExtractor
                .supports(DocumentFileType.MARKDOWN))
                .isFalse();
    }

    // ── MarkdownExtractor ─────────────────────────

    @Test
    @DisplayName("Markdown: strips headers")
    void markdownShouldStripHeaders() {
        String result = markdownExtractor
                .extract("## Introduction\n\nSome text");
        assertThat(result)
                .contains("Introduction")
                .doesNotContain("##");
    }

    @Test
    @DisplayName("Markdown: strips bold syntax")
    void markdownShouldStripBold() {
        String result = markdownExtractor
                .extract("This is **important** text");
        assertThat(result)
                .contains("important")
                .doesNotContain("**");
    }

    @Test
    @DisplayName("Markdown: converts links to text")
    void markdownShouldConvertLinks() {
        String result = markdownExtractor
                .extract("[Click here](https://example.com)");
        assertThat(result)
                .contains("Click here")
                .doesNotContain("https://example.com")
                .doesNotContain("[")
                .doesNotContain("]");
    }

    @Test
    @DisplayName("Markdown: strips code blocks")
    void markdownShouldStripCodeFences() {
        String result = markdownExtractor
                .extract(
                        "```java\n"
                                + "System.out.println(\"hi\");\n"
                                + "```");
        assertThat(result)
                .contains("System.out.println")
                .doesNotContain("```");
    }

    @Test
    @DisplayName("Markdown: handles null input")
    void markdownShouldHandleNull() {
        assertThat(markdownExtractor.extract(null))
                .isEqualTo("");
    }

    @Test
    @DisplayName("Markdown: supports MARKDOWN type")
    void markdownShouldSupportMarkdown() {
        assertThat(markdownExtractor
                .supports(DocumentFileType.MARKDOWN))
                .isTrue();
        assertThat(markdownExtractor
                .supports(DocumentFileType.TXT))
                .isFalse();
    }
}