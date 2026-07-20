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
            return "Cannot connect to Jarvis server.\n"
                    + "Make sure it is running on port 8080.\n"
                    + "Run: ./mvnw spring-boot:run";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> health = http.getPublic(
                    "/actuator/health", Map.class);

            if (health == null) {
                return "Health endpoint returned null";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> components =
                    (Map<String, Object>)
                            health.get("components");

            StringBuilder sb = new StringBuilder();
            sb.append("\n+----------------------------------+\n");
            sb.append(  "|     Jarvis System Status         |\n");
            sb.append(  "+----------------------------------+\n");

            if (components != null) {
                for (var entry : components.entrySet()) {
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
                    String ce = "UP".equals(cs)
                            ? "[OK]" : "[!!]";
                    sb.append(String.format(
                            "| %-10s %s %-16s|\n",
                            capitalize(key), ce, cs));
                }
            }

            sb.append("+----------------------------------+\n");
            if (state.isLoggedIn()) {
                sb.append(String.format(
                        "| User: %-26s|\n",
                        state.getUsername()
                                + " (" + state.getRole() + ")"));
            } else {
                sb.append(
                        "| User: Not logged in              |\n");
            }
            sb.append("+----------------------------------+");
            return sb.toString();

        } catch (Exception e) {
            return "Error reading status: "
                    + e.getMessage();
        }
    }

    @Command(
            name = "jarvis-version",
            description = "Show Jarvis version information"
    )
    public String jarvisVersion() {
        return "\n"
                + "Jarvis AI Platform\n"
                + "------------------\n"
                + "Version:       v0.1.0\n"
                + "Spring Boot:   4.0.6\n"
                + "Spring AI:     2.0\n"
                + "Spring Shell:  4.0\n"
                + "Java:          21\n"
                + "GitHub:        "
                + "github.com/sujankim/jarvis-ai-platform\n";
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
            sb.append("[OK] Jarvis server:  Running\n");
        } else {
            sb.append("[!!] Jarvis server:  Not running\n");
            sb.append("     Fix: ./mvnw spring-boot:run\n");
        }

        // Check Ollama
        try {
            java.net.http.HttpClient client =
                    java.net.http.HttpClient
                            .newHttpClient();
            java.net.http.HttpRequest req =
                    java.net.http.HttpRequest
                            .newBuilder()
                            .uri(java.net.URI.create(
                                    "http://localhost:11434"
                                            + "/api/tags"))
                            .GET()
                            .build();
            var resp = client.send(req,
                    java.net.http.HttpResponse
                            .BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                sb.append("[OK] Ollama:         Running\n");
            } else {
                sb.append("[!!] Ollama:         Unexpected "
                        + resp.statusCode() + "\n");
            }
        } catch (Exception e) {
            sb.append("[!!] Ollama:         Not running\n");
            sb.append("     Fix: ollama serve\n");
        }

        // Check login status
        if (state.isLoggedIn()) {
            sb.append("[OK] Auth:           Logged in as "
                    + state.getUsername() + "\n");
        } else {
            sb.append("[--] Auth:           Not logged in\n");
            sb.append("     Fix: Type 'login'\n");
        }

        sb.append("\n");
        sb.append("USAGE TIPS:\n");
        sb.append(
                "  jarvis:> login    - authenticate\n");
        sb.append(
                "  jarvis:> chat     - start chatting\n");
        sb.append(
                "  jarvis:> session  - list sessions\n");
        sb.append(
                "  NOTE: type messages INSIDE 'chat'\n");

        return sb.toString();
    }

    @Command(
            name = "about",
            description = "Show Jarvis platform information"
    )
    public String about() {
        return "\nJarvis AI Platform v0.1.0\n"
                + "-------------------------\n"
                + "Local-first AI assistant platform.\n\n"
                + "Built with:\n"
                + "  Java 21        | Spring Boot 4.0.6\n"
                + "  Spring AI 2.0  | Spring Shell 4.0\n\n"
                + "GitHub:\n"
                + "  github.com/sujankim/jarvis-ai-platform\n\n"
                + "License: Apache-2.0\n\n"
                + "Your AI. Your Data. Your Machine.\n";
    }

    @Command(
            name = "examples",
            description = "Show common command usage examples"
    )
    public String examples() {
        return """

                QUICK START:
                  login                    Sign in to Jarvis
                  chat                     Start chatting with AI
                  ask -m "question"        Quick single question

                SESSION MANAGEMENT:
                  session                  List all conversations
                  switch-session -n 2      Switch to session #2
                  new-session              Start fresh session

                SYSTEM:
                  status                   Check all services
                  doctor                   Full health diagnostics
                  jarvis-version           Version information
                  about                    About Jarvis
                """;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0))
                + s.substring(1);
    }
}