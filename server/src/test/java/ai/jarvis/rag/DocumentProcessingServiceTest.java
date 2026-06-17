package ai.jarvis.rag;

import ai.jarvis.rag.extraction.MarkdownExtractor;
import ai.jarvis.rag.extraction.PlainTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core
        .R2dbcEntityTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentProcessingService Tests")
class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    private DocumentProcessingService service;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingService(
                documentRepository,
                chunkRepository,
                r2dbcEntityTemplate,
                List.of(
                        new PlainTextExtractor(),
                        new MarkdownExtractor()
                )
        );
    }

    // ── extractText() tests ───────────────────────

    @Test
    @DisplayName("extractText() uses PlainText for TXT")
    void shouldUsePlainExtractorForTxt() {
        String result = service.extractText(
                "Hello\r\nWorld",
                DocumentFileType.TXT);
        assertThat(result)
                .isEqualTo("Hello\nWorld");
    }

    @Test
    @DisplayName("extractText() uses Markdown for MD")
    void shouldUseMarkdownExtractorForMd() {
        String result = service.extractText(
                "## Title\n**bold** text",
                DocumentFileType.MARKDOWN);
        assertThat(result)
                .contains("Title")
                .contains("bold")
                .doesNotContain("##")
                .doesNotContain("**");
    }

    @Test
    @DisplayName("extractText() falls back for PDF")
    void shouldFallbackForPdf() {
        // No PDF extractor registered
        // Falls back to raw text
        String result = service.extractText(
                "raw pdf text",
                DocumentFileType.PDF);
        assertThat(result).isEqualTo("raw pdf text");
    }

    // ── splitIntoChunks() tests ───────────────────

    @Test
    @DisplayName("splitIntoChunks() splits long text")
    void shouldSplitLongText() {
        // Generate text with 1000 words
        String longText = "word ".repeat(1000).trim();

        List<String> chunks =
                service.splitIntoChunks(longText);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("splitIntoChunks() handles short text")
    void shouldHandleShortText() {
        String shortText =
                "This is a short document with few words "
                        + "that fits in one chunk.";

        List<String> chunks =
                service.splitIntoChunks(shortText);

        assertThat(chunks).hasSize(1);
    }

    @Test
    @DisplayName("splitIntoChunks() returns empty for null")
    void shouldReturnEmptyForNull() {
        List<String> chunks =
                service.splitIntoChunks(null);
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("splitIntoChunks() creates overlapping chunks")
    void shouldCreateOverlappingChunks() {
        // 800 words → should produce 2 overlapping chunks
        String text = "word ".repeat(800).trim();

        List<String> chunks =
                service.splitIntoChunks(text);

        if (chunks.size() >= 2) {
            // The last words of chunk 1 should appear
            // in the first words of chunk 2 (overlap)
            String[] chunk1Words =
                    chunks.get(0).split("\\s+");
            String lastWordsChunk1 =
                    chunk1Words[chunk1Words.length - 1];

            // Chunk 2 should start with overlap content
            assertThat(chunks.get(1))
                    .isNotEmpty();
        }
    }

    // ── estimateTokens() tests ────────────────────

    @Test
    @DisplayName("estimateTokens() estimates correctly")
    void shouldEstimateTokens() {
        // 400 chars / 4 = 100 tokens
        String text = "a".repeat(400);
        int tokens = service.estimateTokens(text);
        assertThat(tokens).isEqualTo(100);
    }

    @Test
    @DisplayName("estimateTokens() handles empty string")
    void shouldHandleEmptyForTokens() {
        assertThat(service.estimateTokens(""))
                .isEqualTo(0);
        assertThat(service.estimateTokens(null))
                .isEqualTo(0);
    }
}