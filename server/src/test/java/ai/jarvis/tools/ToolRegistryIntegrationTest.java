package ai.jarvis.tools;

import ai.jarvis.ai.provider.OllamaProvider;
import ai.jarvis.config.TestContainerConfig;
import ai.jarvis.tools.builtin.CalculatorTool;
import ai.jarvis.tools.builtin.DateTimeTool;
import ai.jarvis.tools.builtin.WeatherTool;
import ai.jarvis.tools.builtin.WebSearchTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ToolRegistry}.
 *
 * <p>Verifies three things that unit tests cannot cover:
 * <ol>
 *   <li>All {@code @Component} {@link JarvisTool} beans are auto-discovered
 *       by Spring and injected into the registry.</li>
 *   <li>{@link ToolRegistry} holds the correct tool count after
 *       the full application context loads.</li>
 *   <li>The {@link ToolRegistry} singleton is correctly injected into
 *       {@link OllamaProvider} with the same tools available.</li>
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
@DisplayName("ToolRegistry Integration Tests")
class ToolRegistryIntegrationTest {

    /**
     * Number of builtin tools that must be registered.
     * Update this constant when a new builtin tool is added.
     */
    private static final int EXPECTED_TOOL_COUNT = 4;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private OllamaProvider ollamaProvider;

    // ── 1. All tools auto-discovered by Spring ────────────────────────────

    @Test
    @DisplayName("all builtin JarvisTool beans are auto-discovered by Spring")
    void allToolsShouldBeAutoDiscovered() {
        List<JarvisTool> tools = toolRegistry.getAll();

        assertThat(tools)
                .as("DateTimeTool must be auto-discovered via @Component")
                .anyMatch(t -> t instanceof DateTimeTool);

        assertThat(tools)
                .as("CalculatorTool must be auto-discovered via @Component")
                .anyMatch(t -> t instanceof CalculatorTool);

        assertThat(tools)
                .as("WeatherTool must be auto-discovered via @Component")
                .anyMatch(t -> t instanceof WeatherTool);

        assertThat(tools)
                .as("WebSearchTool must be auto-discovered via @Component")
                .anyMatch(t -> t instanceof WebSearchTool);
    }

    // ── 2. ToolRegistry contains expected tool count ──────────────────────

    @Test
    @DisplayName("ToolRegistry holds the expected number of tools")
    void toolRegistryShouldContainExpectedToolCount() {
        assertThat(toolRegistry.count())
                .as("Expected exactly %d builtin tools", EXPECTED_TOOL_COUNT)
                .isEqualTo(EXPECTED_TOOL_COUNT);

        assertThat(toolRegistry.getAll())
                .as("getAll() size must match count()")
                .hasSize(EXPECTED_TOOL_COUNT);

        assertThat(toolRegistry.hasTools())
                .as("hasTools() must be true when tools are registered")
                .isTrue();
    }

    // ── 3. Tools injected into OllamaProvider correctly ───────────────────

    @Test
    @DisplayName("ToolRegistry is injected into OllamaProvider with all tools available")
    void toolsShouldBeInjectedIntoOllamaProviderCorrectly()
            throws NoSuchFieldException, IllegalAccessException {

        // Retrieve the private toolRegistry field from OllamaProvider via reflection.
        Field field = OllamaProvider.class
                .getDeclaredField("toolRegistry");
        field.setAccessible(true);
        ToolRegistry injectedRegistry =
                (ToolRegistry) field.get(ollamaProvider);

        // The injected instance must be the same Spring singleton
        assertThat(injectedRegistry)
                .as("OllamaProvider must receive the same ToolRegistry singleton")
                .isSameAs(toolRegistry);

        // The tools visible to OllamaProvider must match the full registry
        assertThat(injectedRegistry.count())
                .as("OllamaProvider's registry must hold %d tools",
                        EXPECTED_TOOL_COUNT)
                .isEqualTo(EXPECTED_TOOL_COUNT);

        assertThat(injectedRegistry.hasTools())
                .as("OllamaProvider's registry must report hasTools() = true")
                .isTrue();

        assertThat(injectedRegistry.asArray())
                .as("asArray() length must match count() "
                        + "so that the OllamaProvider can pass them to ChatClient")
                .hasSize(EXPECTED_TOOL_COUNT);
    }
}