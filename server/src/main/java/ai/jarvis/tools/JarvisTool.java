package ai.jarvis.tools;

/**
 * Marker interface for all Jarvis tool implementations.
 *
 * USAGE:
 * Implement this interface on any class that
 * provides tools to the AI model.
 *
 * Spring AI tools use @Tool annotation on methods.
 * JarvisTool marks the class for auto-discovery
 * by ToolRegistry.
 *
 * ADDING A NEW TOOL:
 * 1. Create class implementing JarvisTool
 * 2. Add @Component
 * 3. Annotate methods with @Tool
 * 4. That's it — ToolRegistry picks it up automatically
 *
 * EXAMPLE:
 * @Component
 * public class MyTool implements JarvisTool {
 *
 *     @Tool(description = "Does something useful")
 *     public String doSomething(String input) {
 *         return "result";
 *     }
 * }
 */
public interface JarvisTool {
    // Marker interface — no methods required
    // @Tool methods defined in implementing classes
}