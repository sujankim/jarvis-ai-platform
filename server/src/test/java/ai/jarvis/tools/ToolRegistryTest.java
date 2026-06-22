package ai.jarvis.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolRegistry Tests")
class ToolRegistryTest {

    @Test
    @DisplayName("registry holds all registered tools")
    void shouldHoldRegisteredTools() {
        DateTimeTool dateTool = new DateTimeTool();
        CalculatorTool calcTool = new CalculatorTool();

        ToolRegistry registry = new ToolRegistry(
                List.of(dateTool, calcTool));

        assertThat(registry.count()).isEqualTo(2);
        assertThat(registry.hasTools()).isTrue();
        assertThat(registry.getAll())
                .hasSize(2)
                .contains(dateTool, calcTool);
    }

    @Test
    @DisplayName("empty registry reports no tools")
    void shouldHandleEmptyRegistry() {
        ToolRegistry registry =
                new ToolRegistry(List.of());

        assertThat(registry.count()).isEqualTo(0);
        assertThat(registry.hasTools()).isFalse();
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    @DisplayName("asArray returns correct array")
    void shouldReturnToolsAsArray() {
        DateTimeTool tool = new DateTimeTool();
        ToolRegistry registry =
                new ToolRegistry(List.of(tool));

        Object[] array = registry.asArray();

        assertThat(array).hasSize(1);
        assertThat(array[0]).isEqualTo(tool);
    }
}