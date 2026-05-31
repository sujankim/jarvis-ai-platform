package ai.jarvis.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemCommands {

    private final CliStateManager state;
    private final CliHttpClient http;

    @Command(
            name = "status",
            description = "Show Jarvis system status"
    )
    public String status() {

        if (!http.isServerReachable()) {
            return """
                ❌ Cannot connect to Jarvis server.
                   Make sure it is running on port 8080.
                   Run: ./mvnw spring-boot:run
                """;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = http.getPublic(
                    "/actuator/health", Map.class);

            if (health == null) {
                return "❌ Health endpoint returned null";
            }

            // Overall status (ignore readinessState)
            @SuppressWarnings("unchecked")
            Map<String, Object> components =
                    (Map<String, Object>)
                            health.get("components");

            StringBuilder sb = new StringBuilder();
            sb.append(
                    "┌──────────────────────────────────────┐\n");
            sb.append(
                    "│        Jarvis System Status          │\n");
            sb.append(
                    "├──────────────────────────────────────┤\n");

            if (components != null) {
                for (var entry : components.entrySet()) {

                    // Skip internal Spring Boot probes
                    // (liveness/readiness - Spring Shell artifact)
                    String key = entry.getKey();
                    if ("livenessState".equals(key)
                            || "readinessState".equals(key)
                            || "ssl".equals(key)
                            || "ping".equals(key)) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> comp =
                            (Map<String, Object>)
                                    entry.getValue();
                    String cs = (String) comp.get("status");
                    String ce = "UP".equals(cs) ? "✅" : "❌";

                    sb.append(String.format(
                            "│  %-12s %s %-18s│\n",
                            capitalize(key), ce, cs));
                }
            }

            sb.append(
                    "├──────────────────────────────────────┤\n");
            if (state.isLoggedIn()) {
                sb.append(String.format(
                        "│  User: %-30s│\n",
                        state.getUsername()
                                + " (" + state.getRole() + ")"));
            } else {
                sb.append(
                        "│  User: Not logged in                │\n");
            }
            sb.append(
                    "└──────────────────────────────────────┘");

            return sb.toString();

        } catch (Exception e) {
            return "❌ Error reading status: "
                    + e.getMessage();
        }
    }

    @Command(
            name = "version",
            description = "Show version information"
    )
    public String version() {
        return """
                Jarvis AI Platform v0.1.0-SNAPSHOT
                Spring Boot:   4.0.6
                Spring AI:     2.0.0-M8
                Spring Shell:  4.0.2
                Java:          21
                GitHub: github.com/sujankim/jarvis-ai-platform
                """;
    }

    @Command(
            name = "doctor",
            description = "Check all services health"
    )
    public String doctor() {
        StringBuilder sb = new StringBuilder();
        sb.append("Running diagnostics...\n\n");

        // Check Jarvis server
        if (http.isServerReachable()) {
            sb.append("✅ Jarvis server:  Running\n");
        } else {
            sb.append(
                    "❌ Jarvis server:  Not running\n");
            sb.append(
                    "   Fix: ./mvnw spring-boot:run\n");
        }

        // Check Ollama directly
        try {
            java.net.http.HttpClient client =
                    java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req =
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(
                                    "http://localhost:11434"
                                            + "/api/tags"))
                            .GET()
                            .build();
            var response = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers
                            .ofString());
            if (response.statusCode() == 200) {
                sb.append("✅ Ollama:         Running\n");
            } else {
                sb.append(
                        "⚠️  Ollama:         Responded with "
                                + response.statusCode() + "\n");
            }
        } catch (Exception e) {
            sb.append(
                    "❌ Ollama:         Not running\n");
            sb.append("   Fix: ollama serve\n");
        }

        // Check login status
        if (state.isLoggedIn()) {
            sb.append("✅ Auth:           Logged in as "
                    + state.getUsername() + "\n");
        } else {
            sb.append(
                    "⚠️  Auth:          Not logged in\n");
            sb.append("   Fix: Type 'login'\n");
        }

        return sb.toString();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0))
                + s.substring(1);
    }
}