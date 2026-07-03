package ai.jarvis.rag;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("DocumentService Tests")
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("getDocumentByIdAndUserId() should throw exception if document was not found")
    void shouldThrowExceptionIfDocumentNotFound() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(documentRepository.findByIdAndUserId(documentId, userId)).thenReturn(Mono.empty());

        StepVerifier.create(documentService.getDocumentByIdAndUserId(documentId, userId))
                .expectErrorSatisfies(ex -> Assertions.assertThat(ex)
                        .isInstanceOf(ResponseStatusException.class)
                        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND))
                .verify();

        verify(documentRepository).findByIdAndUserId(documentId, userId);
    }

    @Test
    @DisplayName("getDocumentByIdAndUserId() returns document")
    void shouldReturnDocument() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        Document document = Document.create(
                userId,
                "test.txt",
                DocumentFileType.TXT,
                1024L,
                "This is a test document"
        );

        when(documentRepository.findByIdAndUserId(documentId, userId)).thenReturn(Mono.just(document));

        StepVerifier.create(documentService.getDocumentByIdAndUserId(documentId, userId))
                .expectNextMatches(response ->
                        response.filename().equals("test.txt")
                                && response.id().equals(documentId))
                .verifyComplete();

        verify(documentRepository).findByIdAndUserId(documentId, userId);
    }
}
