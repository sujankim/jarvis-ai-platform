package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class CliHttpClient {

    private static final String BASE_URL =
            "http://localhost:8080";

    private final RestClient restClient;

    public CliHttpClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultStatusHandler(
                        // Handle ALL status codes ourselves
                        // Don't throw exception on 4xx/5xx
                        HttpStatusCode::isError,
                        (request, response) -> {
                            // Log but don't throw
                            log.debug(
                                    "HTTP {} from {}",
                                    response.getStatusCode(),
                                    request.getURI()
                            );
                        }
                )
                .build();
    }

    // ── GET ───────────────────────────────────────

    public <T> T get(
            String uri,
            String token,
            Class<T> responseType) {
        return restClient
                .get()
                .uri(uri)
                .header("Authorization",
                        "Bearer " + token)
                .retrieve()
                .body(responseType);
    }

    public <T> T getPublic(
            String uri,
            Class<T> responseType) {
        return restClient
                .get()
                .uri(uri)
                .retrieve()
                .body(responseType);
    }

    // ── POST ──────────────────────────────────────

    public <T> T post(
            String uri,
            Object body,
            Class<T> responseType) {
        return restClient
                .post()
                .uri(uri)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    public <T> T postWithAuth(
            String uri,
            String token,
            Object body,
            Class<T> responseType) {
        return restClient
                .post()
                .uri(uri)
                .header("Authorization",
                        "Bearer " + token)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    // ── Health check ─────────────────────────────

    public boolean isServerReachable() {
        try {
            // Just check we get ANY response
            // Don't care if it's UP or OUT_OF_SERVICE
            getPublic("/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Streaming (for chat) ──────────────────────

    public void streamChat(
            String token,
            Object body,
            java.util.function.Consumer<String> onToken,
            Runnable onDone,
            java.util.function.Consumer<String> onError) {

        try {
            var webClient = org.springframework.web.reactive
                    .function.client.WebClient.builder()
                    .baseUrl(BASE_URL)
                    .build();

            webClient
                    .post()
                    .uri("/api/v1/chat/stream")
                    .header("Authorization", "Bearer " + token)
                    .contentType(
                            org.springframework.http.MediaType
                                    .APPLICATION_JSON)
                    .accept(
                            org.springframework.http.MediaType
                                    .TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(
                            new org.springframework.core
                                    .ParameterizedTypeReference<
                                    org.springframework.http.codec
                                            .ServerSentEvent<String>>() {})
                    .doOnNext(event -> {
                        if ("token".equals(event.event())
                                && event.data() != null) {
                            // ── Parse JSON token to get
                            //    the real text with spaces
                            String tokenText =
                                    parseJsonToken(event.data());
                            if (tokenText != null) {
                                onToken.accept(tokenText);
                            }
                        } else if ("done"
                                .equals(event.event())) {
                            onDone.run();
                        }
                    })
                    .doOnError(err ->
                            onError.accept(err.getMessage()))
                    .blockLast();

        } catch (Exception e) {
            onError.accept(e.getMessage());
        }
    }

    /**
     * Parse {"t":"token text"} → "token text"
     * Preserves spaces that SSE codec strips.
     */
    private String parseJsonToken(String json) {
        try {
            // Simple parse: {"t":" nice"}
            // Find value between "t":" and last "
            int start = json.indexOf("\"t\":\"");
            if (start == -1) return json; // fallback
            start += 5; // skip "t":"
            int end = json.lastIndexOf("\"");
            if (end <= start) return json;
            return json.substring(start, end)
                    // Unescape JSON
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        } catch (Exception e) {
            return json; // fallback to raw
        }
    }
}