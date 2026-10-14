package ai.jarvis.rag;

import ai.jarvis.rag.extraction.MarkdownExtractor;
import ai.jarvis.rag.extraction.PlainTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ai.jarvis.rag.extraction.PdfTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TextExtractor Tests")
class TextExtractorTest {

    private final PlainTextExtractor plainExtractor =
            new PlainTextExtractor();
    private final MarkdownExtractor markdownExtractor =
            new MarkdownExtractor();

    private final PdfTextExtractor pdfExtractor =
            new PdfTextExtractor();

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

    @Test
    @DisplayName("PDF: rejects invalid input gracefully")
    void pdfShouldHandleInvalidInput() {
        assertThatThrownBy(() -> pdfExtractor.extract(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to extract text from PDF");
    }

    @Test
    @DisplayName("PDF: extracts text from valid PDF")
    void pdfShouldExtractText() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, document.getPage(0))) {
                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Hello PDF");
                contentStream.endText();
            }

            document.save(output);
        }

        String result = pdfExtractor.extract(output.toByteArray());

        assertThat(result).contains("Hello PDF");
    }
    @Test
    @DisplayName("PDF: extracts two paragraphs from the same page")
    void pdfShouldExtractTwoParagraphsFromSamePage() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, document.getPage(0))) {

                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);

                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("First paragraph of the document.");
                contentStream.newLineAtOffset(0, -60);
                contentStream.showText("Second paragraph of the document.");

                contentStream.endText();
            }

            document.save(output);
        }

        var paragraphs = pdfExtractor.extractWithPages(output.toByteArray());

        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(0).text())
                .contains("First paragraph");
        assertThat(paragraphs.get(1).text())
                .contains("Second paragraph");
        assertThat(paragraphs.get(0).pageNumber())
                .isEqualTo(1);
        assertThat(paragraphs.get(1).pageNumber())
                .isEqualTo(1);
    }
    @Test
    @DisplayName("PDF: supports PDF type")
    void pdfShouldSupportPdf() {
        assertThat(pdfExtractor.supports(DocumentFileType.PDF))
                .isTrue();

        assertThat(pdfExtractor.supports(DocumentFileType.TXT))
                .isFalse();

        assertThat(pdfExtractor.supports(DocumentFileType.MARKDOWN))
                .isFalse();
    }
    @Test
    @DisplayName("PDF: tracks page number for extracted paragraphs")
    void pdfShouldTrackPageNumbers() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {

            // Page 1
            document.addPage(new PDPage());

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, document.getPage(0))) {
                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("This text belongs to page one.");
                contentStream.endText();
            }

            // Page 2
            document.addPage(new PDPage());

            try (PDPageContentStream contentStream =
                         new PDPageContentStream(document, document.getPage(1))) {
                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("This text belongs to page two.");
                contentStream.endText();
            }

            document.save(output);
        }

        var paragraphs = pdfExtractor.extractWithPages(output.toByteArray());

        assertThat(paragraphs).hasSize(2);

        assertThat(paragraphs.get(0).text())
                .contains("page one");
        assertThat(paragraphs.get(0).pageNumber())
                .isEqualTo(1);

        assertThat(paragraphs.get(1).text())
                .contains("page two");
        assertThat(paragraphs.get(1).pageNumber())
                .isEqualTo(2);
    }
}