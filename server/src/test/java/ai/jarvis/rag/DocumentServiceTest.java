package ai.jarvis.rag;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@DisplayName("DocumentService Tests")
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("getDocumentByIdAndUserId() should throw excpetion if user was not found")
    void shouldThrowExceptionIfUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(documentRepository.findByIdAndUserId(documentId, userId)).thenReturn(Mono.empty());

        Assertions.assertThatThrownBy(() -> documentService.getDocumentByIdAndUserId(documentId, userId))
                .isExactlyInstanceOf(ResponseStatusException.class);
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

        Mono<DocumentStatusResponse> result = documentService.getDocumentByIdAndUserId(documentId, userId);

        assertThat(result).isNotNull();
        verify(documentRepository).findByIdAndUserId(documentId, userId);
    }
}
