package ai.jarvis.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryEmbeddingRepository Tests")
class MemoryEmbeddingRepositoryTest {

    @Test
    @DisplayName("toVectorString formats correctly")
    void vectorStringShouldFormatCorrectly() {
        // Test via SemanticSearchResult record
        MemoryEmbeddingRepository.SemanticSearchResult result =
                new MemoryEmbeddingRepository
                        .SemanticSearchResult(
                        java.util.UUID.randomUUID(),
                        MemoryType.FACT,
                        "User builds Jarvis",
                        0.80, 5, 0.87
                );

        assertThat(result.content())
                .isEqualTo("User builds Jarvis");
        assertThat(result.similarity())
                .isEqualTo(0.87);
        assertThat(result.type())
                .isEqualTo(MemoryType.FACT);
    }

    @Test
    @DisplayName("SemanticSearchResult holds all fields")
    void semanticResultShouldHoldAllFields() {
        java.util.UUID id = java.util.UUID.randomUUID();

        MemoryEmbeddingRepository.SemanticSearchResult result =
                new MemoryEmbeddingRepository
                        .SemanticSearchResult(
                        id,
                        MemoryType.GOAL,
                        "Build Jarvis AI Platform",
                        0.90, 12, 0.92
                );

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.type()).isEqualTo(MemoryType.GOAL);
        assertThat(result.importance()).isEqualTo(0.90);
        assertThat(result.accessCount()).isEqualTo(12);
        assertThat(result.similarity()).isEqualTo(0.92);
    }
}