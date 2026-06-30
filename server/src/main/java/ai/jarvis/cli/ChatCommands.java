package ai.jarvis.cli;

import ai.jarvis.chat.streaming.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ChatCommands {

    private final CliStateManager state;
    private final CliHttpClient http;
    private final LineReader lineReader;

    /**
     * Single constructor.
     * Spring Framework 7 auto-detects it.
     * @Lazy breaks the circular dependency.
     */
    public ChatCommands(
            CliStateManager state,
            CliHttpClient http,
            @Lazy LineReader lineReader) {
        this.state = state;
        this.http = http;
        this.lineReader = lineReader;
    }

    @Command(
            name = "chat",
            description = "Start an interactive chat with Jarvis"
    )
    public void chat(
            @Option(
                    longName = "new",
                    description = "Start a new session",
                    defaultValue = "false"
            ) boolean newSession) {

        if (!state.isLoggedIn()) {
            System.out.println(
                    "Please login first. Type: login");
            return;
        }

        if (!http.isServerReachable()) {
            System.out.println(
                    "Cannot connect to server.");
            return;
        }

        if (newSession) {
            state.setActiveSessionId(null);
            state.setActiveSessionTitle(null);
        }

        String sessionInfo = state.hasActiveSession()
                ? "Session: "
                  + state.getActiveSessionTitle()
                : "Starting new session...";

        System.out.println(
                "+--------------------------------------+");
        System.out.println(
                "|  " + padRight(sessionInfo, 36) + "|");
        System.out.println(
                "|  Type 'exit' to return to menu       |");
        System.out.println(
                "+--------------------------------------+");
        System.out.println();

        while (true) {
            try {
                String input = lineReader.readLine(
                        "You: ");

                if (input == null
                        || input.trim()
                        .equalsIgnoreCase("exit")
                        || input.trim()
                        .equalsIgnoreCase("quit")) {
                    System.out.println(
                            "\nEnding chat. Goodbye!");
                    break;
                }

                if (input.trim().isEmpty()) {
                    continue;
                }

                sendAndStream(input.trim());

            } catch (UserInterruptException e) {
                System.out.println(
                        "\nChat interrupted. Goodbye!");
                break;
            } catch (EndOfFileException e) {
                System.out.println(
                        "\nEnding chat. Goodbye!");
                break;
            } catch (Exception e) {
                System.out.println(
                        "Error: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "ask",
            description = "Ask a single question. "
                    + "Usage: ask --message \"Hello\""
    )
    public void ask(
            @Option(
                    longName = "message",
                    shortName = 'm',
                    description = "Your question",
                    required = true
            ) String message) {

        if (!state.isLoggedIn()) {
            System.out.println(
                    "Not logged in. Type: login");
            return;
        }

        sendAndStream(message);
        System.out.println();
    }

    private void sendAndStream(String message) {
        System.out.println();
        System.out.print("Jarvis: ");
        System.out.flush();

        ChatRequest request = new ChatRequest(
                state.getActiveSessionId(),
                message, null);

        http.streamChat(
                state.getAccessToken(),
                request,
                sessionId -> {
                    if (state.getActiveSessionId()
                            == null) {
                        try {
                            state.setActiveSessionId(
                                    UUID.fromString(
                                            sessionId.trim()));
                            state.setActiveSessionTitle(
                                    message.length() > 30
                                            ? message.substring(
                                            0, 27) + "..."
                                            : message);
                        } catch (Exception ignored) {}
                    }
                },
                token -> {
                    System.out.print(token);
                    System.out.flush();
                },
                stats -> {
                    System.out.println();
                    if (stats.tokenCount() > 0) {
                        System.out.printf(
                                "── %d tokens · %.1fs ──%n",
                                stats.tokenCount(),
                                stats.durationSeconds());
                    }
                    System.out.println();
                    System.out.flush();
                },
                error -> {
                    System.out.println(
                            "\nError: "
                                    + formatError(error));
                    System.out.flush();
                }
        );
    }

    private String formatError(String msg) {
        if (msg != null && msg.contains("401")) {
            return "Session expired. Type: login";
        }
        if (msg != null
                && msg.contains("Connection refused")) {
            return "Cannot connect to server.";
        }
        return msg != null ? msg : "Unknown error";
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() > n) return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }
}