package ai.jarvis.tools;

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
        tool = new WebSearchTool(
                WebClient.builder());
    }

    @Test
    @DisplayName("returns result for valid query")
    void shouldReturnResultForValidQuery() {
        // This makes a real DuckDuckGo call
        // DuckDuckGo is free + no key needed
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

        // Should return something meaningful
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
    @DisplayName("never throws exception to caller")
    void shouldNeverThrowException() {
        // All these should return strings, not throw
        assertThat(tool.search("test query"))
                .isNotNull();
        assertThat(tool.getTopicSummary("Java"))
                .isNotNull();
    }
}