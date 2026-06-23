package ai.jarvis.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
 * WHAT IT RETURNS:
 * - Instant answers (Wikipedia summaries etc.)
 * - Related topics
 * - Direct answer if available
 *
 * LIMITATION:
 * Not a full web scraper — returns structured
 * instant answers only. Perfect for factual queries.
 *
 * Example AI usage:
 * User: "Who invented Java programming language?"
 * AI calls: search("Java programming language inventor")
 * Returns: "Java was created by James Gosling at
 *           Sun Microsystems, released in 1995..."
 */
@Slf4j
@Component
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
     * Max related topics to include.
     * Too many = token waste.
     */
    private static final int MAX_RELATED_TOPICS = 3;

    private final WebClient webClient;

    public WebSearchTool(
            WebClient.Builder webClientBuilder) {

        this.webClient = webClientBuilder
                .baseUrl(DDGO_API)
                .build();

        log.info("WebSearchTool initialized "
                + "(DuckDuckGo — no API key needed)");
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
                            .queryParam("q",
                                    query.trim())
                            .queryParam("format",
                                    "json")
                            .queryParam("no_html", "1")
                            .queryParam(
                                    "skip_disambig", "1")
                            .build())
                    .retrieve()
                    .bodyToMono(SearchResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null) {
                return "No results found for: "
                        + query;
            }

            return formatSearchResponse(
                    query, response);

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
     * Search for a specific topic and get summary.
     * More focused than general search.
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
                            .queryParam("q",
                                    topic.trim())
                            .queryParam("format",
                                    "json")
                            .queryParam("no_html", "1")
                            .queryParam(
                                    "skip_disambig", "1")
                            .build())
                    .retrieve()
                    .bodyToMono(SearchResponse.class)
                    .timeout(TIMEOUT)
                    .block();

            if (response == null
                    || (response.abstractText() == null
                    || response.abstractText()
                    .isBlank())) {
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

        // Direct answer (best case)
        if (response.answer() != null
                && !response.answer().isBlank()) {
            sb.append("**Direct Answer:** ")
                    .append(response.answer())
                    .append("\n\n");
        }

        // Abstract (Wikipedia summary)
        if (response.abstractText() != null
                && !response.abstractText().isBlank()) {

            String abstractText =
                    response.abstractText();
            if (abstractText.length()
                    > MAX_ABSTRACT_LENGTH) {
                abstractText = abstractText.substring(
                        0, MAX_ABSTRACT_LENGTH)
                        + "...";
            }

            sb.append("**Summary:** ")
                    .append(abstractText)
                    .append("\n");

            if (response.abstractUrl() != null
                    && !response.abstractUrl()
                    .isBlank()) {
                sb.append("Source: ")
                        .append(response.abstractUrl())
                        .append("\n");
            }
        }

        // Related topics
        if (response.relatedTopics() != null
                && !response.relatedTopics().isEmpty()) {

            List<RelatedTopic> topics =
                    response.relatedTopics().stream()
                            .filter(t -> t.text() != null
                                    && !t.text().isBlank())
                            .limit(MAX_RELATED_TOPICS)
                            .toList();

            if (!topics.isEmpty()) {
                sb.append("\n**Related:**\n");
                topics.forEach(t ->
                        sb.append("- ")
                                .append(t.text()
                                        .length() > 100
                                        ? t.text()
                                        .substring(0, 100)
                                          + "..."
                                        : t.text())
                                .append("\n"));
            }
        }

        // Nothing found
        if (sb.isEmpty()) {
            return "No results found for: '"
                    + query + "'. "
                    + "Try rephrasing your question.";
        }

        return sb.toString().trim();
    }

    // ── Response Records ──────────────────────────

    private record SearchResponse(
            String answer,
            String abstractText,
            String abstractUrl,
            String abstractSource,
            List<RelatedTopic> relatedTopics) {}

    private record RelatedTopic(
            String text,
            String firstUrl) {}
}