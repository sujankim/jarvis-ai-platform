package ai.jarvis.tools.builtin;

import ai.jarvis.tools.JarvisTool;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Tool for web search using DuckDuckGo.
 *
 * API: DuckDuckGo Instant Answer API
 * FREE: No API key needed, no rate limits
 * Privacy: No tracking, fits Jarvis philosophy
 *
 * FIXES (CodeRabbit):
 * 1. @ConditionalOnProperty — respects
 *    jarvis.tools.web-search.enabled=false in config.
 *    Previously always registered regardless of config.
 *
 * 2. maxRelatedTopics injected from config
 *    jarvis.tools.web-search.max-results.
 *    Previously hardcoded — config value was ignored.
 *
 * 3. @JsonProperty on SearchResponse + RelatedTopic
 *    DuckDuckGo API returns PascalCase field names:
 *    Answer, AbstractText, AbstractURL, RelatedTopics
 *    Text, FirstURL.
 *    Without @JsonProperty all fields deserialize as null.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "jarvis.tools.web-search",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WebSearchTool implements JarvisTool {

    private static final String DDGO_API =
            "https://api.duckduckgo.com";

    private static final Duration TIMEOUT =
            Duration.ofSeconds(5);

    /**
     * Max characters to include from abstract.
     * Prevents overly long context injection.
     */
    private static final int MAX_ABSTRACT_LENGTH = 500;

    /**
     * FIX Issue 2: Injected from config instead of hardcoded.
     * Reads from: jarvis.tools.web-search.max-results
     * Default: 3 if not configured.
     * Minimum: 1 (Math.max guard prevents 0 or negative).
     */
    private final int maxRelatedTopics;

    private final WebClient webClient;

    public WebSearchTool(
            WebClient.Builder webClientBuilder,
            @Value("${jarvis.tools.web-search.max-results:3}")
            int maxRelatedTopics) {

        // FIX Issue 2: guard against 0 or negative config
        this.maxRelatedTopics = Math.max(1, maxRelatedTopics);

        this.webClient = webClientBuilder
                .baseUrl(DDGO_API)
                .build();

        log.info(
                "WebSearchTool initialized "
                        + "(DuckDuckGo — no API key needed) "
                        + "maxResults={}",
                this.maxRelatedTopics);
    }

    /**
     * Search the web for information.
     * Uses DuckDuckGo Instant Answer API.
     *
     * @param query search query
     * @return search results as formatted string
     */
    @Tool(description =
            "Search the web for current information, "
                    + "facts, or answers to questions. "
                    + "Use when you need information "
                    + "that may be beyond your training "
                    + "data or when user asks for "
                    + "current/recent information. "
                    + "Returns instant answers and "
                    + "relevant summaries.")
    public String search(
            @ToolParam(
                    description =
                            "Search query. "
                                    + "Be specific for better results. "
                                    + "Examples: "
                                    + "'Java programming language creator', "
                                    + "'Spring Boot 4 features', "
                                    + "'Nepal capital city'")
            String query) {

        if (query == null || query.isBlank()) {
            return "Please provide a search query.";
        }

        log.debug("WebSearchTool.search: {}", query);

        try {
            SearchResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("q", query.trim())
                            .queryParam("format", "json")
                            .queryParam("no_html", "1")
                            .queryParam("skip_disambig", "1")
                            .build())
                    .retrieve()
                    .bodyToMono(SearchResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                return "No results found for: " + query;
            }

            return formatSearchResponse(query, response);

        } catch (Exception e) {
            log.warn(
                    "WebSearchTool failed for '{}': {}",
                    query, e.getMessage());

            return "Search temporarily unavailable. "
                    + "Please try again or rephrase "
                    + "your question.";
        }
    }

    /**
     * Get a summary/definition of a specific topic.
     *
     * @param topic topic to get summary for
     * @return topic summary string
     */
    @Tool(description =
            "Get a summary/definition of a specific "
                    + "topic, concept, person, or place. "
                    + "Better than general search for "
                    + "'what is X' or 'who is Y' questions. "
                    + "Returns Wikipedia-style summaries.")
    public String getTopicSummary(
            @ToolParam(
                    description =
                            "Topic to summarize. "
                                    + "Examples: 'Spring Boot', "
                                    + "'Mount Everest', "
                                    + "'Alan Turing', "
                                    + "'PostgreSQL'")
            String topic) {

        if (topic == null || topic.isBlank()) {
            return "Please provide a topic.";
        }

        log.debug(
                "WebSearchTool.getTopicSummary: {}",
                topic);

        try {
            SearchResponse response = webClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("q", topic.trim())
                            .queryParam("format", "json")
                            .queryParam("no_html", "1")
                            .queryParam("skip_disambig", "1")
                            .build())
                    .retrieve()
                    .bodyToMono(SearchResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null
                    || response.abstractText() == null
                    || response.abstractText().isBlank()) {
                return "No summary found for '"
                        + topic + "'. "
                        + "Try rephrasing or use "
                        + "the search tool instead.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("**").append(topic).append("**\n");

            String abstractText = response.abstractText();
            if (abstractText.length()
                    > MAX_ABSTRACT_LENGTH) {
                abstractText = abstractText.substring(
                        0, MAX_ABSTRACT_LENGTH) + "...";
            }
            sb.append(abstractText);

            if (response.abstractUrl() != null
                    && !response.abstractUrl().isBlank()) {
                sb.append("\n\nSource: ")
                        .append(response.abstractUrl());
            }

            return sb.toString();

        } catch (Exception e) {
            log.warn(
                    "WebSearchTool.getTopicSummary "
                            + "failed for '{}': {}",
                    topic, e.getMessage());
            return "Could not retrieve summary "
                    + "for: " + topic;
        }
    }

    // ── Private Helpers ───────────────────────────

    private String formatSearchResponse(
            String query,
            SearchResponse response) {

        StringBuilder sb = new StringBuilder();

        if (response.answer() != null
                && !response.answer().isBlank()) {
            sb.append("**Direct Answer:** ")
                    .append(response.answer())
                    .append("\n\n");
        }

        if (response.abstractText() != null
                && !response.abstractText().isBlank()) {

            String abstractText = response.abstractText();
            if (abstractText.length()
                    > MAX_ABSTRACT_LENGTH) {
                abstractText = abstractText.substring(
                        0, MAX_ABSTRACT_LENGTH) + "...";
            }

            sb.append("**Summary:** ")
                    .append(abstractText)
                    .append("\n");

            if (response.abstractUrl() != null
                    && !response.abstractUrl().isBlank()) {
                sb.append("Source: ")
                        .append(response.abstractUrl())
                        .append("\n");
            }
        }

        if (response.relatedTopics() != null
                && !response.relatedTopics().isEmpty()) {

            List<RelatedTopic> topics =
                    response.relatedTopics().stream()
                            .filter(t -> t.text() != null
                                    && !t.text().isBlank())
                            // FIX Issue 2: use injected config
                            .limit(maxRelatedTopics)
                            .toList();

            if (!topics.isEmpty()) {
                sb.append("\n**Related:**\n");
                topics.forEach(t ->
                        sb.append("- ")
                                .append(t.text().length() > 100
                                        ? t.text().substring(0, 100) + "..."
                                        : t.text())
                                .append("\n"));
            }
        }

        if (sb.isEmpty()) {
            return "No results found for: '"
                    + query + "'. "
                    + "Try rephrasing your question.";
        }

        return sb.toString().trim();
    }

    // ── Response Records ──────────────────────────

    /**
     * FIX Issue 3: @JsonProperty for DuckDuckGo PascalCase.
     * DuckDuckGo Instant Answer API returns PascalCase:
     * Answer, AbstractText, AbstractURL, RelatedTopics.
     * Jackson default LOWER_CAMEL_CASE cannot map these.
     * Without @JsonProperty all fields return null.
     * AbstractURL also has consecutive capitals (URL)
     * which Jackson maps to abstractURL not abstractUrl.
     */
    private record SearchResponse(
            @JsonProperty("Answer")
            String answer,
            @JsonProperty("AbstractText")
            String abstractText,
            @JsonProperty("AbstractURL")
            String abstractUrl,
            @JsonProperty("AbstractSource")
            String abstractSource,
            @JsonProperty("RelatedTopics")
            List<RelatedTopic> relatedTopics) {}

    /**
     * @JsonProperty for RelatedTopic fields.
     * DuckDuckGo returns Text and FirstURL (PascalCase).
     * FirstURL has consecutive capitals causing extra issue.
     */
    private record RelatedTopic(
            @JsonProperty("Text")
            String text,
            @JsonProperty("FirstURL")
            String firstUrl) {}
}