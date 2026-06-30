package ai.jarvis.agents;

import ai.jarvis.agents.AgentPlanner.PlanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
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

        // ── EXISTING TESTS ────────────────────────────────────

        @Test
        @DisplayName("parseResponse() extracts ACTION correctly")
        void shouldParseActionResponse() {
                String response = """
                                THOUGHT: I need to check the weather.
                                ACTION: getWeather
                                INPUT: London
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isAction()).isTrue();
                assertThat(result.thought()).contains("check the weather");
                assertThat(result.toolName()).isEqualTo("getWeather");
                assertThat(result.toolInput()).isEqualTo("London");
        }

        @Test
        @DisplayName("parseResponse() extracts FINAL_ANSWER correctly")
        void shouldParseFinalAnswer() {
                String response = """
                                THOUGHT: I have all the data needed.
                                FINAL_ANSWER: London is 22°C and sunny.
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isFinal()).isTrue();
                assertThat(result.thought()).contains("all the data needed");
                assertThat(result.finalAnswer()).contains("London is 22°C");
        }

        @Test
        @DisplayName("parseResponse() handles empty response")
        void shouldHandleEmptyResponse() {
                PlanResult result = planner.parseResponse("");

                assertThat(result.isError()).isTrue();
                assertThat(result.error()).contains("Empty response");
        }

        @Test
        @DisplayName("parseResponse() handles null response")
        void shouldHandleNullResponse() {
                PlanResult result = planner.parseResponse(null);

                assertThat(result.isError()).isTrue();
                assertThat(result.error()).contains("Empty response");
        }

        @Test
        @DisplayName("parseResponse() treats unstructured text as final")
        void shouldTreatUnstructuredAsFinal() {
                String response = "The answer is 42.";

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isFinal()).isTrue();
                assertThat(result.finalAnswer()).isEqualTo("The answer is 42.");
        }

        @Test
        @DisplayName("parseResponse() handles THOUGHT only")
        void shouldHandleThoughtOnly() {
                String response = """
                                THOUGHT: Still gathering information.
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.type()).isEqualTo(PlanResult.PlanType.THOUGHT);
                assertThat(result.thought()).contains("gathering information");
        }

        @Test
        @DisplayName("parseResponse() handles empty ACTION input")
        void shouldHandleEmptyInput() {
                String response = """
                                THOUGHT: Check current time.
                                ACTION: getCurrentDateTime
                                INPUT:
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isAction()).isTrue();
                assertThat(result.toolName()).isEqualTo("getCurrentDateTime");
                assertThat(result.toolInput()).isEmpty();
        }

        // ── NEW TESTS FOR PARSE RESPONSE EDGE CASES ──────────

        @Test
        @DisplayName("parseResponse() handles response with no THOUGHT/ACTION/FINAL labels")
        void shouldHandleNoLabelsResponse() {
                String response = "Just a plain response without any labels or structure";

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isFinal()).isTrue();
                assertThat(result.finalAnswer()).isEqualTo("Just a plain response without any labels or structure");
                assertThat(result.thought()).isEqualTo("Direct response");
        }

        @Test
        @DisplayName("parseResponse() handles ACTION with multi-line INPUT block")
        void shouldHandleMultiLineInput() {
                String response = """
                                THOUGHT: Need to process this text
                                ACTION: processText
                                INPUT: Line 1 of the text
                                Line 2 of the text
                                Line 3 of the text
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isAction()).isTrue();
                assertThat(result.toolName()).isEqualTo("processText");
                assertThat(result.toolInput())
                                .contains("Line 1 of the text")
                                .contains("Line 2 of the text")
                                .contains("Line 3 of the text");
        }

        @Test
        @DisplayName("parseResponse() handles FINAL_ANSWER containing the text ACTION:")
        void shouldHandleFinalAnswerContainingAction() {
                String response = """
                                THOUGHT: I have the answer now
                                FINAL_ANSWER: To use this tool, type ACTION: run_tool with your input
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isFinal()).isTrue();
                assertThat(result.finalAnswer()).isEqualTo("To use this tool, type ACTION: run_tool with your input");
                assertThat(result.finalAnswer()).contains("ACTION:");
                assertThat(result.thought()).isEqualTo("I have the answer now");
        }

        @Test
        @DisplayName("parseResponse() handles very long THOUGHT section")
        void shouldHandleVeryLongThought() {
                StringBuilder longThought = new StringBuilder();
                longThought.append("THOUGHT: ");
                for (int i = 0; i < 100; i++) {
                        longThought.append(
                                        "This is a long reasoning step that tests the parser's ability to handle large content. ");
                }
                String response = longThought.toString().trim();

                PlanResult result = planner.parseResponse(response);

                assertThat(result.type()).isEqualTo(PlanResult.PlanType.THOUGHT);
                assertThat(result.thought().length()).isGreaterThan(500);
                assertThat(result.thought()).contains("This is a long reasoning step");
                assertThat(result.thought()).doesNotContain("...");
        }

        @Test
        @DisplayName("parseResponse() handles response containing only whitespace")
        void shouldHandleWhitespaceOnlyResponse() {
                PlanResult result1 = planner.parseResponse("   ");
                PlanResult result2 = planner.parseResponse("\n\t\n  \n");
                PlanResult result3 = planner.parseResponse(" \t \n ");

                assertThat(result1.isError()).isTrue();
                assertThat(result1.error()).contains("Empty response");

                assertThat(result2.isError()).isTrue();
                assertThat(result2.error()).contains("Empty response");

                assertThat(result3.isError()).isTrue();
                assertThat(result3.error()).contains("Empty response");
        }

        @Test
        @DisplayName("parseResponse() handles response with mixed case labels - treats as final answer")
        void shouldHandleMixedCaseLabels() {
                String response = """
                                thought: I need to calculate
                                action: calculate
                                input: 25 * 4
                                """;

                PlanResult result = planner.parseResponse(response);

                assertThat(result.isFinal()).isTrue();
                assertThat(result.finalAnswer()).contains("thought: I need to calculate");
        }

        // ── NEW TESTS FOR FORMAT TOOL LIST EDGE CASES ────────

        @Test
        @DisplayName("formatToolList() handles single registered tool")
        void shouldFormatSingleTool() {
                Map<String, String> tools = new LinkedHashMap<>();
                tools.put("calculate", "Evaluates math expressions");

                String result = planner.formatToolList(tools);

                assertThat(result).isEqualTo("- calculate: Evaluates math expressions");
                assertThat(result).doesNotContain("\n");
        }

        @Test
        @DisplayName("formatToolList() handles many registered tools (10+)")
        void shouldFormatManyTools() {
                Map<String, String> tools = new LinkedHashMap<>();
                for (int i = 0; i < 12; i++) {
                        tools.put("tool" + i, "Description for tool " + i);
                }

                String result = planner.formatToolList(tools);

                for (int i = 0; i < 12; i++) {
                        assertThat(result).contains("- tool" + i + ": Description for tool " + i);
                }
                assertThat(result.split("\n").length).isEqualTo(12);
        }

        @Test
        @DisplayName("formatToolList() handles tool with very long description")
        void shouldFormatToolWithLongDescription() {
                Map<String, String> tools = new LinkedHashMap<>();
                StringBuilder longDesc = new StringBuilder();
                for (int i = 0; i < 50; i++) {
                        longDesc.append("This is a very long description that tests how the formatter handles ");
                }
                longDesc.append("extremely long text.");
                tools.put("complexTool", longDesc.toString());

                String result = planner.formatToolList(tools);

                assertThat(result).startsWith("- complexTool: ");
                assertThat(result).contains(longDesc.toString());
                assertThat(result.length()).isGreaterThan(500);
        }

        @Test
        @DisplayName("formatToolList() preserves tool order")
        void shouldPreserveToolOrder() {
                Map<String, String> tools = new LinkedHashMap<>();
                tools.put("first", "First tool");
                tools.put("second", "Second tool");
                tools.put("third", "Third tool");

                String result = planner.formatToolList(tools);

                int firstPos = result.indexOf("first");
                int secondPos = result.indexOf("second");
                int thirdPos = result.indexOf("third");
                assertThat(firstPos).isLessThan(secondPos);
                assertThat(secondPos).isLessThan(thirdPos);
        }

        // ── PlanResult Factory Tests ─────────────────────────

        @Test
        @DisplayName("PlanResult.thought() creates THOUGHT type")
        void shouldCreateThoughtResult() {
                PlanResult result = PlanResult.thought("thinking about stuff");

                assertThat(result.type()).isEqualTo(PlanResult.PlanType.THOUGHT);
                assertThat(result.thought()).isEqualTo("thinking about stuff");
                assertThat(result.isAction()).isFalse();
                assertThat(result.isFinal()).isFalse();
                assertThat(result.isError()).isFalse();
        }
}