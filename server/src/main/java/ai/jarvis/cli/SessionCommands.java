package ai.jarvis.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCommands {

    private final CliStateManager state;
    private final CliHttpClient http;

    @Command(
            name = "sessions",
            description = "List all chat sessions"
    )
    public String sessions() {

        if (!state.isLoggedIn()) {
            return "⚠️  Please login first.";
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
            sb.append(
                    "┌────┬────────────────────────────"
                            + "───┬──────────┐\n");
            sb.append(
                    "│ #  │ Title                      "
                            + "   │ Messages │\n");
            sb.append(
                    "├────┼────────────────────────────"
                            + "───┼──────────┤\n");

            int i = 1;
            for (var session : sessions) {
                String title =
                        session.get("title") != null
                                ? session.get("title").toString()
                                : "Untitled";
                if (title.length() > 31) {
                    title = title.substring(0, 28)
                            + "...";
                }
                int msgCount =
                        session.get("messageCount")
                                != null
                                ? ((Number) session
                                .get("messageCount"))
                                .intValue()
                                : 0;
                sb.append(String.format(
                        "│ %-2d │ %-31s │ %-8d │\n",
                        i++, title, msgCount));
            }

            sb.append(
                    "└────┴────────────────────────────"
                            + "───┴──────────┘");

            return sb.toString();

        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    @Command(
            name = "new-session",
            description = "Start a new chat session"
    )
    public String newSession() {
        if (!state.isLoggedIn()) {
            return "⚠️  Please login first.";
        }
        state.setActiveSessionId(null);
        state.setActiveSessionTitle("New Session");
        return "✅ Ready for new session. Type: chat";
    }
}