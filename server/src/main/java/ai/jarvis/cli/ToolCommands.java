package ai.jarvis.cli;

import ai.jarvis.tools.JarvisTool;
import ai.jarvis.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

@Slf4j
@Component
public class ToolCommands {
    private final CliStateManager state;
    private final ToolRegistry toolRegistry;

    public ToolCommands(CliStateManager state, ToolRegistry toolRegistry) {
        this.state = state;
        this.toolRegistry = toolRegistry;
    }
    @Command(
            name = "tools",
            description = "List all registered AI tools and methods"
    )
    public String tools() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }
        List<JarvisTool> tools = toolRegistry.getAll();

        StringBuilder output = new StringBuilder();
        output.append("\nAvailable Tools (").append(tools.size()).append(")\n");
        output.append("---------------------------\n");

        for (JarvisTool tool : tools) {
            output.append("\n")
                    .append(tool.getClass().getSimpleName())
                    .append("\n");

            Method[] methods = tool.getClass().getDeclaredMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(Tool.class)
                        || !Modifier.isPublic(method.getModifiers())) {
                    continue;
                }

                output.append("  - ").append(method.getName()).append("(");
                Class<?>[] parameterTypes = method.getParameterTypes();

                for (int i = 0; i < parameterTypes.length; i++) {
                    if (i > 0) {
                        output.append(", ");
                    }
                    output.append(parameterTypes[i].getSimpleName());
                }
                output.append(")\n");
            }
        }
        return output.toString();
    }
    @Command(
            name = "tool-test",
            description = "Test any registered tool method directly"
    )
    public String toolTest(
            @Option(
                    longName = "tool",
                    shortName = 't',
                    required = true,
                    description = "Tool name, for example CalculatorTool"
            )
            String toolName,
            @Option(
                    longName = "method",
                    shortName = 'm',
                    required = true,
                    description = "Tool method name"
            )
            String methodName,
            @Option(
                    longName = "input",
                    shortName = 'i',
                    required = false,
                    defaultValue = "",
                    description = "Comma-separated method arguments"
            )
            String input) {

        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        JarvisTool selectedTool = null;
        for (JarvisTool tool : toolRegistry.getAll()) {
            if (toolName.equals(tool.getClass().getSimpleName())) {
                selectedTool = tool;
                break;
            }
        }
        if (selectedTool == null) {
            return "Unknown tool: "+ toolName + ". Run 'tools' to see available tools and methods.";
        }
        Method selectedMethod = null;
        for (Method method : selectedTool.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)
                    && methodName.equals(method.getName())
                    && Modifier.isPublic(method.getModifiers())) {
                selectedMethod = method;
                break;
            }
        }
        if (selectedMethod == null) {
            return "Unknown method: " + methodName + ". Run 'tools' to see available tools and methods.";
        }
        try {
            Class<?>[] parameterTypes = selectedMethod.getParameterTypes();
            Object[] arguments = new Object[parameterTypes.length];
            if (parameterTypes.length > 0) {
                String[] values =  input.split("\\s*,\\s*");
                if (parameterTypes.length != values.length) {
                    return "Invalid input format";
                }
                for (int i = 0; i < parameterTypes.length; i++) {
                    arguments[i] = convertValue(values[i], parameterTypes[i]);
                }
            }
            Object result = selectedMethod.invoke(selectedTool, arguments);
            if (result == null) {
                return "Tool returned no result.";
            }
            return result.toString();
        } catch (Exception e) {
            return "Tool execution failed: " + e.getMessage();
        }
    }

    private Object convertValue(String value, Class<?> targetType) {
        String trimmed = value.trim();
        if (targetType == String.class) {
            return trimmed;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.valueOf(trimmed);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.valueOf(trimmed);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.valueOf(trimmed);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException("Boolean value must be true or false.");
            }
            return Boolean.valueOf(trimmed);
        }
        throw new IllegalArgumentException("Unsupported parameter type: " + targetType.getName());
    }
}