package ai.jarvis.cli;

import ai.jarvis.security.auth.request.LoginRequest;
import ai.jarvis.security.auth.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCommands {

    private final CliStateManager state;
    private final WebClient.Builder webClientBuilder;

    private WebClient client() {
        return webClientBuilder
                .baseUrl("http://localhost:8080")
                .build();
    }

    @Command(
            name = "login",
            description = "Login to Jarvis"
    )
    public String login() {

        if(state.isLoggedIn()) {
            return "⚠ Already logged in as: "
                    + state.getUsername()
                    + "\nType 'logout' first.";
        }

        System.out.print("Username: ");
        System.out.flush();
        String username = readLine();

        System.out.print("Password: ");
        System.out.flush();
        String password;
        if(System.console() != null) {
            password = new String(
                    System.console().readPassword());
        }else{
            password = readLine();
        }

        try{
            TokenResponse response = client()
                    .post()
                    .uri("/api/v1/auth/login")
                    .bodyValue(new LoginRequest(
                            username, password))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response != null
                    && response.user() != null) {
                state.setAccessToken(
                        response.accessToken());
                state.setUsername(
                        response.user().username());
                state.setRole(
                        response.user().role().name());
                state.setUserId(
                        response.user().userId());

                return "✅ Welcome back, "
                        + response.user().displayName()
                        + "! ("
                        + response.user().role().name()
                        + ")";
            }
            return "❌ Login failed";

        } catch (Exception e) {
            return "❌ Login failed: "
                    + extractMessage(e);
        }
    }

    @Command(
            name = "logout",
            description = "Logout from Jarvis"
    )
    public String logout() {
        if (!state.isLoggedIn()) {
            return "Not logged in.";
        }
        String name = state.getUsername();
        state.clear();
        return "✅ Goodbye, " + name + "!";
    }

    @Command(
            name = "whoami",
            description = "Show current user info"
    )
    public String whoami() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        return """
                ┌─────────────────────────────┐
                │  Current User               │
                ├─────────────────────────────┤
                │  Username: %s│
                │  Role:     %s│
                │  ID:       %s│
                └─────────────────────────────┘
                """.formatted(
                padRight(state.getUsername(), 18),
                padRight(state.getRole(), 18),
                padRight(state.getUserId()
                        .toString()
                        .substring(0, 8) + "...", 18)
        );
    }

    // ── Helpers ───────────────────────────────────

    private String readLine() {
        try {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(System.in))
                    .readLine();
        } catch (Exception e) {
            return "";
        }
    }

    private String extractMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("401")) {
            return "Invalid username or password";
        }
        if (msg != null
                && msg.contains("Connection refused")) {
            return "Cannot connect to server. "
                    + "Is Jarvis running?";
        }
        return msg != null ? msg : "Unknown error";
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
