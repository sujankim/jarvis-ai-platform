package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
                        HttpStatusCode::isError,
                        (request, response) -> {
                            log.debug(
                                    "HTTP {} from {}",
                                    response.getStatusCode(),
                                    request.getURI());
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

    // ── POST (generic) ───────────────────────────

    public <T> T post(
            String uri,
            Object body,
            Class<T> responseType) {
        return restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
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
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    // ── POST returning raw Map ────────────────────
    // Use this when record deserialization fails
    // Jackson 3 + records can be tricky with RestClient

    @SuppressWarnings("unchecked")
    public Map<String, Object> postForMap(
            String uri,
            Object body) {
        return restClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    // ── Health check ─────────────────────────────

    public boolean isServerReachable() {
        try {
            getPublic("/actuator/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Streaming (for chat) ──────────────────────

    public record StreamStats(
            int tokens,
            long durationMs
    ) {}

    public void streamChat(
            String token,
            Object body,
            java.util.function.Consumer<String> onSession,
            java.util.function.Consumer<String> onToken,
            java.util.function.Consumer<StreamStats> onDone,
            java.util.function.Consumer<String> onError) {

        try {
            long startedAtNanos = System.nanoTime();
            AtomicInteger tokenCount = new AtomicInteger();
            var webClient = org.springframework.web
                    .reactive.function.client
                    .WebClient.builder()
                    .baseUrl(BASE_URL)
                    .build();

            webClient
                    .post()
                    .uri("/api/v1/chat/stream")
                    .header("Authorization",
                            "Bearer " + token)
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
                        String eventType = event.event();
                        if ("session".equals(eventType)
                                && event.data() != null) {
                            onSession.accept(event.data().trim());
                        } else if ("token".equals(eventType)
                                && event.data() != null) {
                            String tokenText =
                                    parseJsonToken(event.data());
                            if (tokenText != null) {
                                tokenCount.incrementAndGet();
                                onToken.accept(tokenText);
                            }
                        } else if ("done".equals(eventType)) {
                            long durationMs = TimeUnit.NANOSECONDS
                                    .toMillis(System.nanoTime()
                                            - startedAtNanos);
                            onDone.accept(new StreamStats(
                                    tokenCount.get(),
                                    durationMs));
                        }
                    })
                    .doOnError(err ->
                            onError.accept(err.getMessage()))
                    .blockLast();

        } catch (Exception e) {
            onError.accept(e.getMessage());
        }
    }

    private String parseJsonToken(String json) {
        try {
            int start = json.indexOf("\"t\":\"");
            if (start == -1) return json;
            start += 5;
            int end = json.lastIndexOf("\"");
            if (end <= start) return json;
            return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        } catch (Exception e) {
            return json;
        }
    }
}
