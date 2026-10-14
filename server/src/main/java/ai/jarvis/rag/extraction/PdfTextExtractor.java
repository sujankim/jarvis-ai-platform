package ai.jarvis.rag.extraction;

import ai.jarvis.rag.DocumentFileType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor implements TextExtractor {

    /**
     * PDF extraction requires binary PDF content.
     *
     * This method is not used for PDF processing because converting
     * PDF bytes to String before extraction would corrupt the binary data.
     */
    @Override
    public String extract(String rawText) {
        throw new UnsupportedOperationException(
                "PDF extraction requires byte[] content; use extract(byte[])");
    }

    /**
     * Extracts text from a PDF while preserving the page number
     * for each extracted paragraph.
     *
     * @param pdfContent PDF content as bytes
     * @return extracted paragraphs with their page numbers
     */
    public List<PdfParagraph> extractWithPages(byte[] pdfContent) {
        if (pdfContent == null || pdfContent.length == 0) {
            throw new IllegalArgumentException("PDF content cannot be null or empty");
        }

        try (PDDocument document = Loader.loadPDF(pdfContent)) {
            List<PdfParagraph> paragraphs = new ArrayList<>();

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setParagraphEnd("\n\n");

            for (int page = 1; page <= document.getNumberOfPages(); page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(document);

                for (String paragraph : pageText.split("\\R\\s*\\R")) {
                    String cleanParagraph = paragraph.trim();

                    if (!cleanParagraph.isEmpty()) {
                        paragraphs.add(
                                new PdfParagraph(cleanParagraph, page)
                        );
                    }
                }
            }

            return paragraphs;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to extract text from PDF", e);
        }
    }

    /**
     * Existing simple extraction API.
     *
     * @param pdfContent PDF content as bytes
     * @return all extracted text
     */
    public String extract(byte[] pdfContent) {
        return extractWithPages(pdfContent)
                .stream()
                .map(PdfParagraph::text)
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.PDF;
    }

    /**
     * A paragraph extracted from a PDF together with
     * the page it came from.
     */
    public record PdfParagraph(
            String text,
            int pageNumber
    ) {}
}