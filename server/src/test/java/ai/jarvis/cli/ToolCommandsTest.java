package ai.jarvis.cli;

import ai.jarvis.tools.ToolRegistry;
import ai.jarvis.tools.builtin.CalculatorTool;
import ai.jarvis.tools.builtin.DateTimeTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolCommands Unit Tests")
class ToolCommandsTest {
    @Mock
    private CliStateManager state;

    private ToolCommands toolCommands;

    @BeforeEach
    void setUp() {
        CalculatorTool calculatorTool = new CalculatorTool();
        DateTimeTool dateTimeTool = new DateTimeTool();
        ToolRegistry toolRegistry = new ToolRegistry(List.of(calculatorTool, dateTimeTool));
        toolCommands = new ToolCommands(state, toolRegistry);
    }
    @Test
    @DisplayName("tools should require login")
    void toolsShouldRequireLogin() {
        when(state.isLoggedIn()).thenReturn(false);
        String result = toolCommands.tools();
        assertThat(result).contains("Not logged in");
    }
    @Test
    @DisplayName("tools should list registered tools and methods")
    void toolsShouldListRegisteredToolsAndMethods() {
        when(state.isLoggedIn()).thenReturn(true);
        String result = toolCommands.tools();
        assertThat(result).contains("Available Tools (2)");
        assertThat(result).contains("CalculatorTool")
                .contains("calculate")
                .contains("calculatePercentage")
                .contains("squareRoot");
        assertThat(result).contains("DateTimeTool")
                .contains("getCurrentDateTime")
                .contains("getCurrentTimeInZone")
                .contains("getCurrentDate")
                .contains("listTimezonesForRegion");

    }

    @Test
    @DisplayName("tool-test should require login")
    void toolTestShouldRequireLogin() {
        when(state.isLoggedIn()).thenReturn(false);
        String result = toolCommands.toolTest("CalculatorTool", "calculate", "1 + 1");
        assertThat(result).contains("Not logged in");
    }
    @Test
    @DisplayName("tool-test should call calculator")
    void toolTestShouldCallCalculator() {
        when(state.isLoggedIn()).thenReturn(true);
        String result = toolCommands.toolTest("CalculatorTool", "calculate", "2847 * 391");
        assertThat(result).contains("2847 * 391").contains("1,113,177");
    }
    @Test
    @DisplayName("tool-test should call date time method without input")
    void toolTestShouldCallDateTimeWithoutInput() {
        when(state.isLoggedIn()).thenReturn(true);
        String result = toolCommands.toolTest("DateTimeTool", "getCurrentDateTime", "");
        assertThat(result).isNotBlank();
    }
    @Test
    @DisplayName("tool-test should point to tools command for unknown tool")
    void toolTestShouldPointToToolsCommandForUnknownTool() {
        when(state.isLoggedIn()).thenReturn(true);
        String result = toolCommands.toolTest("UnknownTool", "calculate", "2847 * 391");
        assertThat(result).contains("Unknown tool: UnknownTool")
                .contains("Run 'tools'");
    }
    @Test
    @DisplayName("tool-test should point to tools command for unknown method")
    void toolTestShouldPointToToolsCommandForUnknownMethod() {
        when(state.isLoggedIn()).thenReturn(true);
        String result = toolCommands.toolTest("CalculatorTool", "unknownMethod", "1 + 1");
        assertThat(result).contains("Unknown method: unknownMethod")
                .contains("Run 'tools'");
    }
}
