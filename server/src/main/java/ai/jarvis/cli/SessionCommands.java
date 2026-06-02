package ai.jarvis.cli;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SessionCommands {

    private final CliStateManager state;
    private final CliHttpClient http;

    public SessionCommands(
            CliStateManager state,
            CliHttpClient http) {
        this.state = state;
        this.http = http;
    }

    @Command(
            name = "session",
            description = "List all chat sessions"
    )
    public String session() {

        if (!state.isLoggedIn()) {
            return "Please login first.";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.get(
                    "/api/v1/sessions",
                    state.getAccessToken(),
                    Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sessions =
                    response != null
                            ? (List<Map<String, Object>>)
                            response.get("data")
                            : List.of();

            if (sessions == null || sessions.isEmpty()) {
                return "No sessions yet. Type: chat";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append(
                    "+----+-------------------------------"
                            + "+----------+\n");
            sb.append(
                    "| #  | Title                         "
                            + "| Messages |\n");
            sb.append(
                    "+----+-------------------------------"
                            + "+----------+\n");

            int i = 1;
            for (var s : sessions) {
                String title =
                        s.get("title") != null
                                ? s.get("title").toString()
                                : "Untitled";

                // Fix truncation: proper 29 char limit
                if (title.length() > 29) {
                    title = title.substring(0, 26)
                            + "...";
                }

                int msgCount =
                        s.get("messageCount") != null
                                ? ((Number) s.get("messageCount"))
                                .intValue()
                                : 0;

                // Mark active session with *
                boolean isActive =
                        state.getActiveSessionId() != null
                                && s.get("id") != null
                                && state.getActiveSessionId()
                                .toString()
                                .equals(s.get("id")
                                        .toString());

                sb.append(String.format(
                        "| %s%-2d | %-29s | %-8d |\n",
                        isActive ? "*" : " ",
                        i++,
                        title,
                        msgCount));
            }

            sb.append(
                    "+----+-------------------------------"
                            + "+----------+\n");
            sb.append("  * = active session\n");

            return sb.toString();

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Command(
            name = "new-session",
            description = "Start a new chat session"
    )
    public String newSession() {
        if (!state.isLoggedIn()) {
            return "Please login first.";
        }
        state.setActiveSessionId(null);
        state.setActiveSessionTitle("New Session");
        return "Ready for new session. Type: chat";
    }

    @Command(
            name = "switch-session",
            description = "Switch to a session by number. "
                    + "Usage: switch-session --number 3"
    )
    public String switchSession(
            @Option(
                    longName = "number",
                    shortName = 'n',
                    description = "Session number from list",
                    required = true
            ) int number) {

        if (!state.isLoggedIn()) {
            return "Please login first.";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.get(
                    "/api/v1/sessions",
                    state.getAccessToken(),
                    Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sessions =
                    response != null
                            ? (List<Map<String, Object>>)
                            response.get("data")
                            : List.of();

            if (sessions == null
                    || sessions.isEmpty()) {
                return "No sessions found.";
            }

            if (number < 1
                    || number > sessions.size()) {
                return "Invalid number. Use 1-"
                        + sessions.size();
            }

            Map<String, Object> chosen =
                    sessions.get(number - 1);
            String id = (String) chosen.get("id");
            String title = chosen.get("title") != null
                    ? chosen.get("title").toString()
                    : "Untitled";

            state.setActiveSessionId(
                    java.util.UUID.fromString(id));
            state.setActiveSessionTitle(title);

            return "Switched to: " + title;

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}