package ai.jarvis.rag;

import ai.jarvis.memory.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagSearchService Tests")
class RagSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private RagSearchRepository ragSearchRepository;

    private RagSearchService service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new RagSearchService(
                embeddingService,
                ragSearchRepository);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("returns empty for null userId")
    void shouldReturnEmptyForNullUserId() {
        StepVerifier
                .create(service.formatForPrompt(
                        null, "test query"))
                .expectNext("")
                .verifyComplete();

        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("returns empty for null query")
    void shouldReturnEmptyForNullQuery() {
        StepVerifier
                .create(service.formatForPrompt(
                        userId, null))
                .expectNext("")
                .verifyComplete();

        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("returns empty for blank query")
    void shouldReturnEmptyForBlankQuery() {
        StepVerifier
                .create(service.formatForPrompt(
                        userId, "   "))
                .expectNext("")
                .verifyComplete();

        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("returns empty when no results found")
    void shouldReturnEmptyWhenNoResults() {
        float[] embedding = new float[768];

        when(embeddingService.embed(anyString()))
                .thenReturn(Mono.just(embedding));
        when(ragSearchRepository.searchSimilar(
                any(), any(), anyInt(),
                anyDouble(), any()))
                .thenReturn(Flux.empty());

        StepVerifier
                .create(service.formatForPrompt(
                        userId, "what is clause 7?"))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    @DisplayName("formats results with source labels")
    void shouldFormatResultsWithSources() {
        float[] embedding = new float[768];
        UUID docId = UUID.randomUUID();

        RagSearchResult result =
                new RagSearchResult(
                        UUID.randomUUID(),
                        docId,
                        "contract.pdf",
                        "Clause 7 states the payment terms...",
                        0,
                        7,
                        0.85
                );

        when(embeddingService.embed(anyString()))
                .thenReturn(Mono.just(embedding));
        when(ragSearchRepository.searchSimilar(
                any(), any(), anyInt(),
                anyDouble(), any()))
                .thenReturn(Flux.just(result));

        StepVerifier
                .create(service.formatForPrompt(
                        userId, "what is clause 7?"))
                .expectNextMatches(formatted ->
                        formatted.contains(
                                "contract.pdf")
                                && formatted.contains(
                                "Clause 7 states")
                                && formatted.contains(
                                "RELEVANT DOCUMENT")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("returns empty on embedding error")
    void shouldReturnEmptyOnEmbeddingError() {
        when(embeddingService.embed(anyString()))
                .thenReturn(Mono.error(
                        new RuntimeException(
                                "Ollama down")));

        StepVerifier
                .create(service.formatForPrompt(
                        userId, "test query"))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    @DisplayName("sourceLabel includes page number")
    void sourceLabelShouldIncludePageNumber() {
        RagSearchResult withPage =
                new RagSearchResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "report.pdf",
                        "content",
                        2, 5, 0.9
                );
        assertThat(withPage.sourceLabel())
                .isEqualTo("report.pdf (page 5)");
    }

    @Test
    @DisplayName("sourceLabel uses chunk index without page")
    void sourceLabelShouldUseChunkWithoutPage() {
        RagSearchResult noPage =
                new RagSearchResult(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "notes.txt",
                        "content",
                        3, null, 0.7
                );
        assertThat(noPage.sourceLabel())
                .isEqualTo("notes.txt (chunk 4)");
    }

    // Import needed for assertThat in this class
    private static <T> org.assertj.core.api.AbstractAssert<?, T>
    assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}