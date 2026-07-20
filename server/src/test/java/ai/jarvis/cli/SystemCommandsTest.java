package ai.jarvis.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemCommands Unit Tests")
class SystemCommandsTest {

    @Mock
    private CliStateManager state;

    @Mock
    private CliHttpClient http;

    @Test
    @DisplayName("examples() returns non-blank output with key sections")
    void shouldReturnExamplesOutput() {
        SystemCommands commands = new SystemCommands(state, http);

        String result = commands.examples();

        assertThat(result).isNotBlank();
        assertThat(result).contains("QUICK START");
        assertThat(result).contains("SESSION MANAGEMENT");
        assertThat(result).contains("MEMORY");
        assertThat(result).contains("SYSTEM");
    }
}
