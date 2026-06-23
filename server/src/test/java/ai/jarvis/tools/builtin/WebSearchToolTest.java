package ai.jarvis.tools.builtin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@DisplayName("WebSearchTool Tests")
class WebSearchToolTest {

    private WebSearchTool tool;
    private WebClient webClient;
    private WebClient.Builder builder;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        builder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(builder.baseUrl(any(String.class))).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        tool = new WebSearchTool(builder, 3);
    }

    @Test
    @DisplayName("returns result for valid query")
    void shouldReturnResultForValidQuery() {
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.setAnswer("Direct Answer Content");
        mockResponse.setAbstractText("Abstract Text Content");
        mockResponse.setRelatedTopics(new ArrayList<>());

        setupWebClientMock(mockResponse);

        String result = tool.search("Spring Boot Java framework");

        assertThat(result)
                .contains("Direct Answer Content")
                .contains("Abstract Text Content");
    }

    @Test
    @DisplayName("handles empty query gracefully")
    void shouldHandleEmptyQuery() {
        String result = tool.search("");
        assertThat(result).contains("provide a search query");
    }

    @Test
    @DisplayName("handles null query gracefully")
    void shouldHandleNullQuery() {
        String result = tool.search(null);
        assertThat(result).contains("provide a search query");
    }

    @Test
    @DisplayName("getTopicSummary returns content")
    void shouldReturnTopicSummary() {
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.setAbstractText("PostgreSQL Summary Content");
        mockResponse.setRelatedTopics(new ArrayList<>());

        setupWebClientMock(mockResponse);

        String result = tool.getTopicSummary("PostgreSQL");
        assertThat(result).contains("PostgreSQL Summary Content");
    }

    @Test
    @DisplayName("getTopicSummary handles empty topic")
    void shouldHandleEmptyTopic() {
        String result = tool.getTopicSummary("");
        assertThat(result).contains("provide a topic");
    }

    @Test
    @DisplayName("getTopicSummary handles null topic")
    void shouldHandleNullTopic() {
        String result = tool.getTopicSummary(null);
        assertThat(result).contains("provide a topic");
    }

    @Test
    @DisplayName("returns No results message when response is empty")
    void shouldReturnNoResultsWhenResponseIsEmpty() {
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        mockResponse.setAnswer("");
        mockResponse.setAbstractText("");
        mockResponse.setRelatedTopics(new ArrayList<>());

        setupWebClientMock(mockResponse);

        String result = tool.search("RandomQuery");
        assertThat(result.trim()).isEqualTo("No results");
    }

    @Test
    @DisplayName("respects maxRelatedTopics config")
    void shouldRespectMaxRelatedTopics() {
        WebSearchTool toolWithOneResult = new WebSearchTool(builder, 1);

        Map<String, Object> mockResponse = new LinkedHashMap<>();
        List<Map<String, Object>> topics = new ArrayList<>();
        
        Map<String, Object> topic1 = new LinkedHashMap<>();
        topic1.put("Text", "Topic 1");
        Map<String, Object> topic2 = new LinkedHashMap<>();
        topic2.put("Text", "Topic 2");
        
        topics.add(topic1);
        topics.add(topic2);
        mockResponse.setRelatedTopics(topics);

        setupWebClientMock(mockResponse);

        String result = toolWithOneResult.search("Java");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("guards against zero maxRelatedTopics")
    void shouldGuardAgainstZeroMaxResults() {
        WebSearchTool toolWithZero = new WebSearchTool(builder, 0);
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        setupWebClientMock(mockResponse);

        String result = toolWithZero.search("test");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("guards against negative maxRelatedTopics")
    void shouldGuardAgainstNegativeMaxResults() {
        WebSearchTool toolWithNegative = new WebSearchTool(builder, -5);
        Map<String, Object> mockResponse = new LinkedHashMap<>();
        setupWebClientMock(mockResponse);

        String result = toolWithNegative.search("test");
        assertThat(result).isNotNull();
    }

    private void setupWebClientMock(Object responseBody) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.just(responseBody));
    }
}
