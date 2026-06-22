package ai.jarvis.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Central registry for all Jarvis tools.
 *
 * AUTO-DISCOVERY:
 * Spring injects ALL JarvisTool beans automatically.
 * New tool = add @Component — zero registry changes.
 *
 * USAGE BY AiOrchestrator:
 * Passes getAll() to ChatClient.tools() so the
 * AI model knows what tools are available.
 *
 * FUTURE:
 * - Per-user tool permissions
 * - Enable/disable tools at runtime
 * - Tool usage analytics
 */
@Slf4j
@Component
public class ToolRegistry {

    private final List<JarvisTool> tools;

    /**
     * Spring injects all JarvisTool beans.
     * List is populated at startup automatically.
     */
    public ToolRegistry(List<JarvisTool> tools) {
        this.tools = Collections
                .unmodifiableList(tools);

        log.info(
                "ToolRegistry initialized: {} tools",
                tools.size());

        tools.forEach(tool ->
                log.info(
                        "  Tool registered: {}",
                        tool.getClass()
                                .getSimpleName()));
    }

    /**
     * Get all registered tools.
     * Passed to ChatClient.tools() in providers.
     *
     * @return unmodifiable list of all tools
     */
    public List<JarvisTool> getAll() {
        return tools;
    }

    /**
     * Get tools as array for Spring AI API.
     * ChatClient.tools() accepts Object... varargs.
     *
     * @return array of tool instances
     */
    public Object[] asArray() {
        return tools.toArray();
    }

    /**
     * Count of registered tools.
     * Used for health checks + logging.
     */
    public int count() {
        return tools.size();
    }

    /**
     * Check if any tools are registered.
     * If false, skip tool registration in providers.
     */
    public boolean hasTools() {
        return !tools.isEmpty();
    }
}