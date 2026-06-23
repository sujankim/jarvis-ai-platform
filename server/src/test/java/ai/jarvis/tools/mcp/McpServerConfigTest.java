package ai.jarvis.tools.mcp;

import ai.jarvis.tools.ToolRegistry;
import ai.jarvis.tools.builtin.CalculatorTool;
import ai.jarvis.tools.builtin.DateTimeTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpServerConfig Tests")
class McpServerConfigTest {

    @Test
    @DisplayName("creates non-null provider with tools")
    void shouldCreateNonNullProvider() {
        DateTimeTool dateTool = new DateTimeTool();
        CalculatorTool calcTool = new CalculatorTool();

        ToolRegistry registry = new ToolRegistry(
                List.of(dateTool, calcTool));

        McpServerConfig config =
                new McpServerConfig(registry);

        ToolCallbackProvider provider =
                config.jarvisToolCallbacks();

        // Provider must exist — Spring AI MCP Server
        // auto-discovers it at startup
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("creates provider with tool callbacks")
    void shouldCreateProviderWithCallbacks() {
        DateTimeTool dateTool = new DateTimeTool();

        ToolRegistry registry = new ToolRegistry(
                List.of(dateTool));

        McpServerConfig config =
                new McpServerConfig(registry);

        ToolCallbackProvider provider =
                config.jarvisToolCallbacks();

        assertThat(provider).isNotNull();

        // getToolCallbacks() returns ToolCallback[]
        // DateTimeTool has 4 @Tool methods
        // so array length should be >= 1
        assertThat(provider.getToolCallbacks())
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("provider with no tools is still valid")
    void shouldHandleEmptyToolRegistry() {
        ToolRegistry emptyRegistry =
                new ToolRegistry(List.of());

        McpServerConfig config =
                new McpServerConfig(emptyRegistry);

        // Should not throw — empty provider is valid
        ToolCallbackProvider provider =
                config.jarvisToolCallbacks();

        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("provider exposes all tool methods")
    void shouldExposeAllToolMethods() {
        DateTimeTool dateTool = new DateTimeTool();
        CalculatorTool calcTool = new CalculatorTool();

        ToolRegistry registry = new ToolRegistry(
                List.of(dateTool, calcTool));

        McpServerConfig config =
                new McpServerConfig(registry);

        ToolCallbackProvider provider =
                config.jarvisToolCallbacks();

        // DateTimeTool: 4 @Tool methods
        // CalculatorTool: 3 @Tool methods
        // Total: 7 callbacks minimum
        assertThat(provider.getToolCallbacks().length)
                .isGreaterThanOrEqualTo(7);
    }
}