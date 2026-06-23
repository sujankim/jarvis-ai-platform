package ai.jarvis.tools.mcp;

import ai.jarvis.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Configuration for Jarvis.
 *
 * Exposes Jarvis built-in tools as MCP ToolCallbackProvider.
 * External MCP clients (Claude Desktop, IDEs, etc.)
 * can connect and use all Jarvis tools via MCP protocol.
 *
 * Source: Official Spring AI docs + Baeldung:
 * @Bean ToolCallbackProvider tools() {
 *     return MethodToolCallbackProvider.builder()
 *             .toolObjects(myTool1, myTool2)
 *             .build();
 * }
 *
 * WHY MethodToolCallbackProvider:
 * → Scans @Tool annotated methods on provided objects
 * → Generates JSON schema automatically
 * → Registers them with MCP server at startup
 * → Works with Spring AI 2.0.0-M8 API
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    private final ToolRegistry toolRegistry;

    /**
     * Register all Jarvis tools as MCP ToolCallbackProvider.
     *
     * MethodToolCallbackProvider scans @Tool annotated
     * methods on each JarvisTool instance and exposes
     * them via the MCP protocol.
     *
     * Spring AI MCP Server auto-discovers beans of
     * type ToolCallbackProvider at startup.
     *
     * @return ToolCallbackProvider with all built-in tools
     */
    @Bean
    public ToolCallbackProvider jarvisToolCallbacks() {

        if (!toolRegistry.hasTools()) {
            log.info(
                    "MCP Server: no tools registered");
            return MethodToolCallbackProvider
                    .builder()
                    .toolObjects()
                    .build();
        }

        Object[] toolObjects =
                toolRegistry.asArray();

        ToolCallbackProvider provider =
                MethodToolCallbackProvider
                        .builder()
                        .toolObjects(toolObjects)
                        .build();

        log.info(
                "MCP Server: registered {} tool objects",
                toolObjects.length);

        return provider;
    }
}