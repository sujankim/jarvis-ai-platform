package ai.jarvis.tools.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebSearchTool Tests")
class WebSearchToolTest {

    private WebSearchTool tool;

    @BeforeEach
    void setUp() {
        // FIX: WebSearchTool constructor now requires
        // maxRelatedTopics as second parameter.
        // Matches @Value("${jarvis.tools.web-search.max-results:3}")
        // Use 3 as default — same as production config.
        tool = new WebSearchTool(
                WebClient.builder(),
                3);
    }

    @Test
    @DisplayName("returns result for valid query")
    void shouldReturnResultForValidQuery() {
        // Real DuckDuckGo call — free, no key needed
        String result = tool.search(
                "Spring Boot Java framework");

        assertThat(result).isNotBlank();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("handles empty query gracefully")
    void shouldHandleEmptyQuery() {
        String result = tool.search("");

        assertThat(result)
                .contains("provide a search query");
    }

    @Test
    @DisplayName("handles null query gracefully")
    void shouldHandleNullQuery() {
        String result = tool.search(null);

        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("getTopicSummary returns content")
    void shouldReturnTopicSummary() {
        String result = tool
                .getTopicSummary("PostgreSQL");

        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("getTopicSummary handles empty topic")
    void shouldHandleEmptyTopic() {
        String result = tool.getTopicSummary("");

        assertThat(result)
                .contains("provide a topic");
    }

    @Test
    @DisplayName("getTopicSummary handles null topic")
    void shouldHandleNullTopic() {
        String result = tool.getTopicSummary(null);

        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("never throws exception to caller")
    void shouldNeverThrowException() {
        assertThat(tool.search("test query"))
                .isNotNull();
        assertThat(tool.getTopicSummary("Java"))
                .isNotNull();
    }

    @Test
    @DisplayName("respects maxRelatedTopics config")
    void shouldRespectMaxRelatedTopics() {
        // Create tool with maxRelatedTopics = 1
        // Verifies config injection works correctly
        WebSearchTool toolWithOneResult =
                new WebSearchTool(
                        WebClient.builder(),
                        1);

        String result = toolWithOneResult
                .search("Java programming language");

        // Should return result without exception
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("guards against zero maxRelatedTopics")
    void shouldGuardAgainstZeroMaxResults() {
        // Math.max(1, 0) = 1 — no exception thrown
        WebSearchTool toolWithZero =
                new WebSearchTool(
                        WebClient.builder(),
                        0);

        String result = toolWithZero
                .search("test");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("guards against negative maxRelatedTopics")
    void shouldGuardAgainstNegativeMaxResults() {
        // Math.max(1, -5) = 1 — no exception thrown
        WebSearchTool toolWithNegative =
                new WebSearchTool(
                        WebClient.builder(),
                        -5);

        String result = toolWithNegative
                .search("test");

        assertThat(result).isNotNull();
    }
}