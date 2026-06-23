package ai.jarvis.memory;

public class MemoryFactory {

    public static final String MEMORY_REQUEST_WITH_NULL_MEMORY_TYPE = """
            {
            "memoryType": null,
            "content": "Memory content"
            }
            """;

    public static final String MEMORY_REQUEST_WITH_MISSING_MEMORY_TYPE = """
            {
            "content": "Memory content"
            }
            """;

    public static final String MEMORY_REQUEST_WITH_NULL_CONTENT = """
            {
            "memoryType": "FACT",
            "content": null
            }
            """;

    public static final String MEMORY_REQUEST_WITH_MISSING_CONTENT = """
            {
            "memoryType": "FACT"
            }
            """;

    public static final String MEMORY_REQUEST_WITH_BLANK_CONTENT = """
            {
            "memoryType": "FACT",
            "content": "   "
            }
            """;

    public static final String MEMORY_REQUEST_TEMPLATE = """
            {
            "memoryType": "%s",
            "content": "%s"
            }
            """;

    public static String createMemoryRequestJson(MemoryType memoryType, String content) {
        return MEMORY_REQUEST_TEMPLATE.formatted(memoryType, content);
    }

}
