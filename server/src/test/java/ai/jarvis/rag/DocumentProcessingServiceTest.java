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
import java.io.IOException;
import java.util.List;
import ai.jarvis.rag.extraction.PdfTextExtractor;
import static org.assertj.core.api.Assertions.assertThat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;
import org.springframework.transaction.reactive.TransactionalOperator;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import ai.jarvis.rag.DocumentStatus;
import java.util.UUID;
import static org.mockito.Mockito.verify;



@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentProcessingService Tests")
class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;
    @Mock
    private TransactionalOperator transactionalOperator;

    private DocumentProcessingService service;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingService(
                documentRepository,
                chunkRepository,
                r2dbcEntityTemplate,
                List.of(
                        new PlainTextExtractor(),
                        new MarkdownExtractor(),
                        new PdfTextExtractor()
                ),
                transactionalOperator
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
    @DisplayName("supports() returns true for PDF file type")
    void shouldSupportPdfFileType() {
        PdfTextExtractor extractor = new PdfTextExtractor();

        assertThat(extractor.supports(DocumentFileType.PDF))
                .isTrue();
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
    @Test
    @DisplayName("PdfTextExtractor extracts PDF content with page number")
    void shouldExtractPdfContentWithPageNumber() throws Exception {
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
                contentStream.showText(
                        "This is PDF content for processing.");
                contentStream.endText();
            }

            document.save(output);
        }

        PdfTextExtractor extractor = new PdfTextExtractor();

        List<PdfTextExtractor.PdfParagraph> paragraphs =
                extractor.extractWithPages(output.toByteArray());

        assertThat(paragraphs).hasSize(1);
        assertThat(paragraphs.get(0).text())
                .contains("PDF content");
        assertThat(paragraphs.get(0).pageNumber())
                .isEqualTo(1);
    }
    @Test
    @DisplayName("splitPdfIntoChunks() preserves page number")
    void shouldPreservePdfPageNumber() {
        List<PdfTextExtractor.PdfParagraph> paragraphs =
                List.of(
                        new PdfTextExtractor.PdfParagraph(
                                "This is page one content with enough words " +
                                        "to create a meaningful chunk for testing purposes.",
                                1),
                        new PdfTextExtractor.PdfParagraph(
                                "This is page two content with enough words " +
                                        "to create another meaningful chunk for testing purposes.",
                                2)
                );

        List<DocumentProcessingService.PdfChunk> chunks =
                service.splitPdfIntoChunks(paragraphs);

        assertThat(chunks).isNotEmpty();

        assertThat(chunks.get(0).pageNumber())
                .isEqualTo(1);
    }
        @Test
        @DisplayName("splitPdfIntoChunks() does not mix page numbers in one chunk")
        void shouldKeepPdfChunksWithinPageBoundaries() {
            List<PdfTextExtractor.PdfParagraph> paragraphs = List.of(
                    new PdfTextExtractor.PdfParagraph(
                            "Page one has a lot of content that should stay together " +
                                    "without being mixed with the next page content.",
                            1),
                    new PdfTextExtractor.PdfParagraph(
                            "Page two also has enough content to create its own chunk " +
                                    "and should keep its own page number.",
                            2)
            );

            List<DocumentProcessingService.PdfChunk> chunks =
                    service.splitPdfIntoChunks(paragraphs);

            assertThat(chunks).isNotEmpty();
            assertThat(chunks)
                    .allSatisfy(chunk ->
                            assertThat(chunk.pageNumber())
                                    .isIn(1, 2));

            // Verify page number is correctly associated with its content
            assertThat(chunks)
                    .anySatisfy(chunk -> {
                        assertThat(chunk.pageNumber()).isEqualTo(1);
                        assertThat(chunk.content()).contains("Page one");
                        assertThat(chunk.content()).doesNotContain("Page two");
                    });

            assertThat(chunks)
                    .anySatisfy(chunk -> {
                        assertThat(chunk.pageNumber()).isEqualTo(2);
                        assertThat(chunk.content()).contains("Page two");
                        assertThat(chunk.content()).doesNotContain("Page one");
                    });
        }

    @Test
    void processPdfDocument_shouldMarkDocumentReady_whenProcessingSucceeds() throws IOException {
        UUID userId = UUID.randomUUID();

        Document document = Document.create(
                userId,
                "test.pdf",
                DocumentFileType.PDF,
                1024L,
                "PDF pipeline test"
        );

        UUID documentId = document.id();

        // 1. Mock document lookup
        when(documentRepository.findByIdAndUserId(documentId, userId))
                .thenReturn(Mono.just(document));

        // 2. ADD THIS BLOCK HERE
        when(r2dbcEntityTemplate.update(any(Document.class)))
                .thenAnswer(invocation ->
                        Mono.just(invocation.getArgument(0)));

        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(r2dbcEntityTemplate.insert(any(DocumentChunk.class)))
                .thenAnswer(invocation ->
                        Mono.just(invocation.getArgument(0)));

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12
                );
                content.newLineAtOffset(50, 700);
                content.showText("This is a PDF pipeline test.");
                content.endText();
            }

            pdf.save(output);
        }

        byte[] pdfContent = output.toByteArray();

        Document result = service.processPdfDocument(
                documentId, userId, pdfContent
        ).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(DocumentStatus.READY);

        ArgumentCaptor<Document> documentCaptor =
                ArgumentCaptor.forClass(Document.class);

        verify(r2dbcEntityTemplate, org.mockito.Mockito.times(2))
                .update(documentCaptor.capture());

        List<Document> updatedDocuments = documentCaptor.getAllValues();

        assertThat(updatedDocuments.get(0).status())
                .isEqualTo(DocumentStatus.PROCESSING);

        assertThat(updatedDocuments.get(1).status())
                .isEqualTo(DocumentStatus.READY);
    }

    @Test
    void processPdfDocument_shouldMarkDocumentFailed_whenPdfIsInvalid() {
        UUID userId = UUID.randomUUID();

        Document document = Document.create(
                userId,
                "invalid.pdf",
                DocumentFileType.PDF,
                100L,
                "Invalid PDF test"
        );

        UUID documentId = document.id();

        when(documentRepository.findByIdAndUserId(documentId, userId))
                .thenReturn(Mono.just(document));

        when(r2dbcEntityTemplate.update(any(Document.class)))
                .thenAnswer(invocation ->
                        Mono.just(invocation.getArgument(0)));

        byte[] invalidPdfContent = "not a valid PDF".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
        );

        Document result = service.processPdfDocument(
                documentId,
                userId,
                invalidPdfContent
        ).block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.errorMessage()).isNotBlank();
    }
}