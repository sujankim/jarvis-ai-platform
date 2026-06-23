package ai.jarvis.tools.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalculatorTool Tests")
class CalculatorToolTest {

    private CalculatorTool tool;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
    }

    @Test
    @DisplayName("calculates simple addition")
    void shouldAddNumbers() {
        String result = tool.calculate("2 + 3");
        assertThat(result).contains("5");
    }

    @Test
    @DisplayName("calculates multiplication correctly")
    void shouldMultiplyNumbers() {
        String result = tool.calculate("2847 * 391");
        assertThat(result).contains("1,113,177");
    }

    @Test
    @DisplayName("calculates division")
    void shouldDivideNumbers() {
        String result = tool.calculate("100 / 4");
        assertThat(result).contains("25");
    }

    @Test
    @DisplayName("calculates percentage")
    void shouldCalculatePercentage() {
        String result = tool
                .calculatePercentage(200, 15);
        assertThat(result).contains("30");
    }

    @Test
    @DisplayName("calculates square root")
    void shouldCalculateSquareRoot() {
        String result = tool.squareRoot(144);
        assertThat(result).contains("12");
    }

    @Test
    @DisplayName("handles negative square root")
    void shouldHandleNegativeSqrt() {
        String result = tool.squareRoot(-4);
        assertThat(result)
                .contains("Error")
                .contains("negative");
    }

    @Test
    @DisplayName("handles empty expression")
    void shouldHandleEmptyExpression() {
        String result = tool.calculate("");
        assertThat(result).contains("Error");
    }

    @Test
    @DisplayName("rejects invalid expressions")
    void shouldRejectInvalidExpressions() {
        String result = tool.calculate(
                "System.exit(0)");
        assertThat(result).contains("invalid");
    }
}