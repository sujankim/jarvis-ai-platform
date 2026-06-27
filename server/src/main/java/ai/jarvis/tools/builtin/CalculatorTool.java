package ai.jarvis.tools.builtin;

import ai.jarvis.tools.JarvisTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Tool for mathematical calculations.
 *
 * PROVIDES:
 * - Basic arithmetic (+, -, *, /)
 * - Power operations
 * - Square root, percentage
 * - Expression evaluation
 *
 * WHY NEEDED:
 * AI models are notoriously bad at exact arithmetic.
 * Providing a calculator tool ensures:
 * - 100% accurate results for math questions
 * - No hallucinated numbers
 * - Handles large numbers correctly
 *
 * SECURITY:
 * Expression evaluator uses safe math-only evaluation.
 * No arbitrary code execution.
 * Input sanitized to allow only math characters.
 *
 * Example AI usage:
 * User: "What is 2847 × 391?"
 * AI calls: calculate("2847 * 391")
 * Returns: "2847 × 391 = 1,113,177"
 */
@Slf4j
@Component
public class CalculatorTool implements JarvisTool {

    /**
     * Evaluate a mathematical expression.
     * Supports: +, -, *, /, ^, sqrt(), %
     *
     * AI calls this for any math calculation.
     *
     * @param expression mathematical expression string
     *                   Examples: "2847 * 391",
     *                   "sqrt(144)", "15% of 200",
     *                   "(100 + 50) * 2"
     * @return calculation result as formatted string
     */
    @Tool(description =
            "Evaluate a mathematical expression "
                    + "and return the result. "
                    + "Use for any arithmetic, "
                    + "calculations, or math questions. "
                    + "Supports: +, -, *, /, "
                    + "parentheses, power (^), "
                    + "square root (sqrt). "
                    + "Always use this tool instead of "
                    + "calculating yourself.")
    public String calculate(
            @ToolParam(
                    description =
                            "Mathematical expression to evaluate. "
                                    + "Examples: '2847 * 391', "
                                    + "'sqrt(144)', "
                                    + "'(100 + 50) * 2 / 3', "
                                    + "'2^10'")
            String expression) {

        if (expression == null
                || expression.isBlank()) {
            return "Error: Empty expression provided.";
        }

        log.debug(
                "CalculatorTool.calculate: {}",
                expression);

        try {
            // Sanitize: allow only safe math chars
            String sanitized = sanitize(expression);

            if (sanitized == null) {
                return "Error: Expression contains "
                        + "invalid characters. "
                        + "Only numbers and math "
                        + "operators are allowed.";
            }

            // Evaluate the expression
            double result = evaluate(sanitized);

            // Format nicely
            String formatted = formatResult(
                    expression, result);

            log.debug(
                    "CalculatorTool result: {} = {}",
                    expression, result);

            return formatted;

        } catch (ArithmeticException e) {
            if (e.getMessage() != null
                    && e.getMessage().contains(
                    "zero")) {
                return "Error: Division by zero "
                        + "is undefined.";
            }
            return "Math error: " + e.getMessage();

        } catch (Exception e) {
            log.warn(
                    "Calculation failed for '{}': {}",
                    expression, e.getMessage());
            return "Could not evaluate: '"
                    + expression + "'. "
                    + "Please check the expression "
                    + "format.";
        }
    }

    /**
     * Calculate percentage.
     * AI calls this for percentage questions.
     *
     * @param value   the base value
     * @param percent the percentage to calculate
     * @return result string
     */
    @Tool(description =
            "Calculate a percentage of a value. "
                    + "Use for questions like "
                    + "'what is 15% of 200' or "
                    + "'calculate 20% tip on 85'.")
    public String calculatePercentage(
            @ToolParam(description = "The base value")
            double value,
            @ToolParam(
                    description = "The percentage (0-100+)")
            double percent) {

        double result = (value * percent) / 100.0;

        String formatted = String.format(
                "%.2f%% of %.2f = %.2f",
                percent, value, result);

        log.debug("CalculatorTool.percentage: {}",
                formatted);

        return formatted;
    }

    /**
     * Calculate square root.
     *
     * @param number the number to find square root of
     * @return square root result string
     */
    @Tool(description =
            "Calculate the square root of a number. "
                    + "Use for sqrt questions.")
    public String squareRoot(
            @ToolParam(
                    description =
                            "The number to find square root of. "
                                    + "Must be non-negative.")
            double number) {

        if (number < 0) {
            return "Error: Cannot calculate square root "
                    + "of a negative number.";
        }

        double result = Math.sqrt(number);

        String formatted = String.format(
                "√%.4f = %.6f",
                number, result);

        // Clean up if whole number
        if (result == Math.floor(result)) {
            formatted = String.format(
                    "√%.0f = %.0f",
                    number, result);
        }

        log.debug("CalculatorTool.sqrt: {}", formatted);
        return formatted;
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Sanitize expression — allow only math chars.
     * SECURITY: Prevents code injection.
     *
     * Allowed: 0-9, +, -, *, /, ., (, ), ^,
     *          spaces, sqrt, Math functions
     *
     * @param expression raw expression
     * @return sanitized expression or null if invalid
     */
    private String sanitize(String expression) {
        // Replace ^ with ** for evaluation
        String cleaned = expression
                .trim()
                .replace("^", "**")
                .replace("sqrt(", "Math.sqrt(")
                .replace("√", "Math.sqrt");

        // Check for invalid characters
        // Allow: digits, operators, parens, dots,
        //        spaces, and known function names
        if (!cleaned.matches(
                "[0-9+\\-*/()., " +
                        "Math.sqrte%!\n\t]*")) {
            return null;
        }

        return cleaned;
    }

    /**
     * Evaluate mathematical expression safely.
     * Uses JavaScript engine for expression eval.
     * Input MUST be sanitized before calling.
     *
     * @param sanitizedExpression pre-sanitized expression
     * @return numerical result
     * @throws Exception if evaluation fails
     */
    
        private double evaluate(String expression) {
                return new ExpressionBuilder(expression)
                .build()
                .evaluate();
        }
    
    /**
     * Format result nicely for display.
     * Removes trailing zeros for whole numbers.
     *
     * @param expression original expression
     * @param result     numerical result
     * @return formatted string
     */
    private String formatResult(
            String expression, double result) {

        // Check if result is a whole number
        if (result == Math.floor(result)
                && !Double.isInfinite(result)
                && Math.abs(result) < 1e15) {

            long longResult = (long) result;
            return expression + " = "
                    + String.format(
                    "%,d", longResult);
        }

        // Decimal result — round to 8 significant figures
        BigDecimal bd = new BigDecimal(result)
                .round(new MathContext(
                        8, RoundingMode.HALF_UP));

        return expression + " = "
                + bd.stripTrailingZeros()
                .toPlainString();
    }
}