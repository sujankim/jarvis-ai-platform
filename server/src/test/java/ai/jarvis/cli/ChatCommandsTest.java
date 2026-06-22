package ai.jarvis.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatCommands Tests")
class ChatCommandsTest {

    @Test
    @DisplayName("Should format stream stats after a tokenized response")
    void shouldFormatStreamStats() {
        String line = ChatCommands.formatStreamStats(
                new CliHttpClient.StreamStats(16, 7500)
        );

        assertThat(line)
                .isEqualTo("        -- 16 tokens · 7.5s --");
    }

    @Test
    @DisplayName("Should hide stream stats when no tokens were counted")
    void shouldHideEmptyStreamStats() {
        String line = ChatCommands.formatStreamStats(
                new CliHttpClient.StreamStats(0, 7500)
        );

        assertThat(line).isEmpty();
    }
}
