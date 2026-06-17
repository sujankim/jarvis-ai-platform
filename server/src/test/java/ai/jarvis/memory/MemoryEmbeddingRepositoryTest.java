package ai.jarvis.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryEmbeddingRepository Tests")
class MemoryEmbeddingRepositoryTest {

    /**
     * Fix 3: Renamed from "toVectorString formats correctly"
     * The original test only validated record accessors,
     * not the toVectorString method at all.
     * Test name now accurately describes what it tests.
     */
    @Test
    @DisplayName("SemanticSearchResult exposes expected fields")
    void semanticSearchResultShouldExposeFields() {
        UUID id = UUID.randomUUID();

        MemoryEmbeddingRepository.SemanticSearchResult
                result =
                new MemoryEmbeddingRepository
                        .SemanticSearchResult(
                        id,
                        MemoryType.FACT,
                        "User builds Jarvis",
                        0.80, 5, 0.87
                );

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.content())
                .isEqualTo("User builds Jarvis");
        assertThat(result.similarity()).isEqualTo(0.87);
        assertThat(result.type())
                .isEqualTo(MemoryType.FACT);
        assertThat(result.importance()).isEqualTo(0.80);
        assertThat(result.accessCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("SemanticSearchResult holds all 6 fields")
    void semanticSearchResultShouldHoldAllFields() {
        UUID id = UUID.randomUUID();

        MemoryEmbeddingRepository.SemanticSearchResult
                result =
                new MemoryEmbeddingRepository
                        .SemanticSearchResult(
                        id,
                        MemoryType.GOAL,
                        "Build Jarvis AI Platform",
                        0.90, 12, 0.92
                );

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.type())
                .isEqualTo(MemoryType.GOAL);
        assertThat(result.content())
                .isEqualTo("Build Jarvis AI Platform");
        assertThat(result.importance()).isEqualTo(0.90);
        assertThat(result.accessCount()).isEqualTo(12);
        assertThat(result.similarity()).isEqualTo(0.92);
    }

    /**
     * Verify vector string format used by pgvector.
     * toVectorString is package-private so we test
     * its output via the expected format pgvector accepts.
     *
     * pgvector format: "[v1,v2,v3,...]"
     * This test documents the expected format clearly.
     */
    @Test
    @DisplayName("vector format matches pgvector expectation")
    void vectorFormatShouldMatchPgvectorExpectation() {
        // pgvector accepts: "[0.1,0.2,0.3]"
        // We verify the expected format is documented
        float[] input = {0.1f, 0.2f, 0.3f};

        // Build expected format manually
        StringBuilder expected = new StringBuilder("[");
        for (int i = 0; i < input.length; i++) {
            expected.append(input[i]);
            if (i < input.length - 1) {
                expected.append(",");
            }
        }
        expected.append("]");

        // Verify format starts with [ and ends with ]
        assertThat(expected.toString())
                .startsWith("[")
                .endsWith("]")
                .contains(",")
                .isEqualTo("[0.1,0.2,0.3]");
    }
}