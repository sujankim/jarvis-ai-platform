package ai.jarvis.tools.builtin;

import ai.jarvis.config.TestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CalculatorTool}.
 *
 * <p>Verifies three things the unit test does not cover:
 * <ol>
 *   <li>Basic arithmetic operations produce the exact formatted
 *       output ({@code "expression = result"}) using the Spring-wired bean.</li>
 *   <li>Large whole-number results are formatted with comma separators
 *       via {@code String.format("%,d")}.</li>
 *   <li>Division by zero is handled gracefully regardless of whether
 *       the JS script engine or the arithmetic fallback is in use.</li>
 * </ol>
 */
@SpringBootTest(
        properties = {
                "spring.shell.interactive.enabled=false",
                "jarvis.security.jwt.secret="
                        + "integration-test-secret-key-min-32-chars-long",
                "spring.ai.google.genai.api-key="
        }
)
@ImportTestcontainers(TestContainerConfig.class)
@DisplayName("CalculatorTool Integration Tests")
class CalculatorToolIntegrationTest {

    @Autowired
    private CalculatorTool calculatorTool;

    // ── 1. Basic arithmetic correct ───────────────────────────────────────

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource({
            "10 + 5,         10 + 5 = 15",
            "20 - 7,         20 - 7 = 13",
            "6 * 7,          6 * 7 = 42",
            "100 / 4,        100 / 4 = 25",
            "(10 + 5) * 2,   (10 + 5) * 2 = 30",
            "2 + 3 * 4,      2 + 3 * 4 = 14",
            "100 - 49 + 1,   100 - 49 + 1 = 52"
    })
    @DisplayName("basic arithmetic produces exact formatted output")
    void shouldProduceExactFormattedOutputForBasicArithmetic(
            String expression, String expectedOutput) {

        String result = calculatorTool.calculate(expression.trim());

        assertThat(result)
                .as("Expression '%s' must produce exact output", expression)
                .isEqualTo(expectedOutput.trim());
    }

    @Test
    @DisplayName("output always contains ' = ' separating expression from result")
    void shouldAlwaysContainEqualsSeparator() {
        String result = calculatorTool.calculate("8 * 8");

        assertThat(result)
                .as("Output format must be 'expression = result'")
                .contains(" = ");

        String[] parts = result.split(" = ");
        assertThat(parts)
                .as("Output must have exactly two parts split on ' = '")
                .hasSize(2);

        assertThat(parts[0].trim())
                .as("Left-hand side must echo the original expression")
                .isEqualTo("8 * 8");

        assertThat(parts[1].trim())
                .as("Right-hand side must be the numeric result")
                .isEqualTo("64");
    }

    // ── 2. Large number formatting correct ───────────────────────────────

    public static Stream<Arguments> largeNumberCases() {
        return Stream.of(
                Arguments.of("2847 * 391",     "2847 * 391 = 1,113,177"),
                Arguments.of("100000 * 10000", "100000 * 10000 = 1,000,000,000"),
                Arguments.of("999999 + 1",     "999999 + 1 = 1,000,000"),
                Arguments.of("1000000 * 1000", "1000000 * 1000 = 1,000,000,000"),
                Arguments.of("123456789 + 0",  "123456789 + 0 = 123,456,789")
        );
    }

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @MethodSource("largeNumberCases")
    @DisplayName("large whole-number results are comma-formatted")
    void shouldFormatLargeNumbersWithCommas(
            String expression, String expectedOutput) {

        String result = calculatorTool.calculate(expression.trim());

        assertThat(result)
                .as("Large number result for '%s' must use comma separators",
                        expression)
                .isEqualTo(expectedOutput);
    }

    @Test
    @DisplayName("result below 1,000 is not comma-formatted")
    void shouldNotAddCommasToSmallNumbers() {
        String result = calculatorTool.calculate("100 + 200");

        assertThat(result)
                .as("Small result must not contain a comma")
                .isEqualTo("100 + 200 = 300");
    }

    // ── 3. Division by zero handled ───────────────────────────────────────

    @Test
    @DisplayName("10 / 0 returns a graceful error, not a numeric result")
    void shouldHandleDivisionByZero() {
        String result = calculatorTool.calculate("10 / 0");

        assertThat(result)
                .as("Division by zero must not produce a numeric answer")
                .doesNotContain("Infinity");

        assertThat(result)
                .as("Division by zero must return one of the two error paths")
                .satisfiesAnyOf(
                        r -> assertThat(r)
                                .contains("Division by zero")
                                .contains("undefined"),
                        r -> assertThat(r)
                                .contains("Could not evaluate")
                );
    }

    @Test
    @DisplayName("0 / 0 returns a graceful error, not NaN")
    void shouldHandleZeroDividedByZero() {
        String result = calculatorTool.calculate("0 / 0");

        assertThat(result)
                .as("0 / 0 must not produce 'NaN' as output")
                .doesNotContain("NaN");

        assertThat(result)
                .as("0 / 0 must return an error string")
                .satisfiesAnyOf(
                        r -> assertThat(r).contains("Division by zero"),
                        r -> assertThat(r).contains("Could not evaluate")
                );
    }

    @Test
    @DisplayName("expression with embedded divide-by-zero is caught")
    void shouldHandleEmbeddedDivisionByZeroInExpression() {
        String result = calculatorTool.calculate("(5 + 5) / 0");

        assertThat(result)
                .as("Compound expression dividing by zero must not produce 'Infinity'")
                .doesNotContain("Infinity");

        assertThat(result)
                .as("Compound expression must return a graceful error")
                .satisfiesAnyOf(
                        r -> assertThat(r).contains("Division by zero"),
                        r -> assertThat(r).contains("Could not evaluate")
                );
    }
}