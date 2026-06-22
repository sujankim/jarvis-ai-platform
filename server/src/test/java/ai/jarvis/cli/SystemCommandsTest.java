package ai.jarvis.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemCommands Tests")
class SystemCommandsTest {

    @Test
    @DisplayName("examples command should show quick start, session, and system commands")
    void examplesCommandShouldShowCommandGroups() {
        SystemCommands commands = new SystemCommands(
                new CliStateManager(),
                new CliHttpClient()
        );

        String output = commands.examples();

        assertThat(output)
                .contains("QUICK START:")
                .contains("login")
                .contains("chat")
                .contains("ask -m \"question\"")
                .contains("SESSION MANAGEMENT:")
                .contains("session")
                .contains("switch-session -n 2")
                .contains("new-session")
                .contains("SYSTEM:")
                .contains("status")
                .contains("doctor")
                .contains("jarvis-version")
                .contains("about");
    }
}
