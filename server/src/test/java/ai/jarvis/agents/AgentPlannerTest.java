package ai.jarvis.agents;

import ai.jarvis.agents.AgentPlanner.PlanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentPlanner Tests")
class AgentPlannerTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    private AgentPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new AgentPlanner(chatClientBuilder);
    }

    // ── parseResponse() tests ─────────────────────

    @Test
    @DisplayName("parseResponse() extracts ACTION correctly")
    void shouldParseActionResponse() {
        String response = """
                THOUGHT: I need to check the weather.
                ACTION: getWeather
                INPUT: London
                """;

        PlanResult result =
                planner.parseResponse(response);

        assertThat(result.isAction()).isTrue();
        assertThat(result.thought())
                .contains("check the weather");
        assertThat(result.toolName())
                .isEqualTo("getWeather");
        assertThat(result.toolInput())
                .isEqualTo("London");
    }

    @Test
    @DisplayName("parseResponse() extracts FINAL_ANSWER correctly")
    void shouldParseFinalAnswer() {
        String response = """
                THOUGHT: I have all the data needed.
                FINAL_ANSWER: London is 22°C and sunny.
                """;

        PlanResult result =
                planner.parseResponse(response);

        assertThat(result.isFinal()).isTrue();
        assertThat(result.thought())
                .contains("all the data needed");
        assertThat(result.finalAnswer())
                .contains("London is 22°C");
    }

    @Test
    @DisplayName("parseResponse() handles empty response")
    void shouldHandleEmptyResponse() {
        PlanResult result =
                planner.parseResponse("");

        assertThat(result.isError()).isTrue();
        assertThat(result.error())
                .contains("Empty response");
    }

    @Test
    @DisplayName("parseResponse() handles null response")
    void shouldHandleNullResponse() {
        PlanResult result =
                planner.parseResponse(null);

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("parseResponse() treats unstructured text as final")
    void shouldTreatUnstructuredAsFinal() {
        String response = "The answer is 42.";

        PlanResult result =
                planner.parseResponse(response);

        assertThat(result.isFinal()).isTrue();
        assertThat(result.finalAnswer())
                .isEqualTo("The answer is 42.");
    }

    @Test
    @DisplayName("parseResponse() handles THOUGHT only")
    void shouldHandleThoughtOnly() {
        String response = """
                THOUGHT: Still gathering information.
                """;

        PlanResult result =
                planner.parseResponse(response);

        assertThat(result.type())
                .isEqualTo(PlanResult.PlanType.THOUGHT);
        assertThat(result.thought())
                .contains("gathering information");
    }

    @Test
    @DisplayName("parseResponse() handles empty ACTION input")
    void shouldHandleEmptyInput() {
        String response = """
                THOUGHT: Check current time.
                ACTION: getCurrentDateTime
                INPUT:
                """;

        PlanResult result =
                planner.parseResponse(response);

        assertThat(result.isAction()).isTrue();
        assertThat(result.toolName())
                .isEqualTo("getCurrentDateTime");
        assertThat(result.toolInput()).isEmpty();
    }

    // ── formatToolList() tests ────────────────────

    @Test
    @DisplayName("formatToolList() formats correctly")
    void shouldFormatToolList() {
        Map<String, String> tools = Map.of(
                "getWeather",
                "Get current weather for a city",
                "calculate",
                "Evaluate math expressions");

        String result =
                planner.formatToolList(tools);

        assertThat(result)
                .contains("getWeather")
                .contains("Get current weather")
                .contains("calculate")
                .contains("Evaluate math");
    }

    @Test
    @DisplayName("formatToolList() handles empty map")
    void shouldHandleEmptyTools() {
        String result =
                planner.formatToolList(Map.of());

        assertThat(result)
                .isEqualTo("No tools available.");
    }

    @Test
    @DisplayName("formatToolList() handles null map")
    void shouldHandleNullTools() {
        String result =
                planner.formatToolList(null);

        assertThat(result)
                .isEqualTo("No tools available.");
    }

    // ── PlanResult factory tests ──────────────────

    @Test
    @DisplayName("PlanResult.action() creates ACTION type")
    void shouldCreateActionResult() {
        PlanResult result = PlanResult.action(
                "reasoning", "tool", "input");

        assertThat(result.type())
                .isEqualTo(PlanResult.PlanType.ACTION);
        assertThat(result.isAction()).isTrue();
        assertThat(result.isFinal()).isFalse();
        assertThat(result.isError()).isFalse();
        assertThat(result.toolName())
                .isEqualTo("tool");
        assertThat(result.toolInput())
                .isEqualTo("input");
    }

    @Test
    @DisplayName("PlanResult.finalAnswer() creates FINAL type")
    void shouldCreateFinalResult() {
        PlanResult result = PlanResult.finalAnswer(
                "done reasoning", "final answer");

        assertThat(result.type())
                .isEqualTo(PlanResult.PlanType.FINAL);
        assertThat(result.isFinal()).isTrue();
        assertThat(result.isAction()).isFalse();
        assertThat(result.finalAnswer())
                .isEqualTo("final answer");
    }

    @Test
    @DisplayName("PlanResult.error() creates ERROR type")
    void shouldCreateErrorResult() {
        PlanResult result =
                PlanResult.error("something broke");

        assertThat(result.isError()).isTrue();
        assertThat(result.error())
                .isEqualTo("something broke");
    }
}