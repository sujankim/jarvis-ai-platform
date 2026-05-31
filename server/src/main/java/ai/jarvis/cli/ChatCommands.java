package ai.jarvis.cli;

import ai.jarvis.chat.streaming.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCommands {

    private final CliStateManager state;
    private final CliHttpClient http;

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
                    "⚠️  Please login first. Type: login");
            return;
        }

        if (!http.isServerReachable()) {
            System.out.println(
                    "❌ Cannot connect to server.");
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
                "╔══════════════════════════════════════╗");
        System.out.println(
                "║  " + padRight(sessionInfo, 36) + "║");
        System.out.println(
                "║  Type 'exit' to return to menu       ║");
        System.out.println(
                "╚══════════════════════════════════════╝");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print("\nYou: ");
                System.out.flush();

                String input = reader.readLine();

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

            } catch (Exception e) {
                System.out.println(
                        "❌ Error: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "ask",
            description = "Ask Jarvis a single question"
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
                    "⚠️  Please login first. Type: login");
            return;
        }

        sendAndStream(message);
        System.out.println();
    }

    // ── Stream via CliHttpClient ──────────────────

    private void sendAndStream(String message) {
        System.out.print("Jarvis: ");
        System.out.flush();

        ChatRequest request = new ChatRequest(
                state.getActiveSessionId(),
                message,
                null);

        http.streamChat(
                state.getAccessToken(),
                request,
                // onToken: print each token immediately
                token -> {
                    System.out.print(token);
                    System.out.flush();
                },
                // onDone: newline when complete
                () -> {
                    System.out.println();
                    System.out.flush();
                },
                // onError: show friendly message
                error -> {
                    System.out.println(
                            "\n❌ " + formatError(error));
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
        return "Error: " + (msg != null
                ? msg : "Unknown error");
    }

    private String padRight(String s, int n) {
        if (s.length() > n)
            return s.substring(0, n);
        return String.format("%-" + n + "s", s);
    }
}