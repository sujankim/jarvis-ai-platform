package ai.jarvis.cli;

import ai.jarvis.memory.Memory;
import ai.jarvis.memory.MemoryService;
import ai.jarvis.memory.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class MemoryCommands {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final CliStateManager state;
    private final MemoryService memoryService;
    private final LineReader lineReader;

    public MemoryCommands(
            CliStateManager state,
            MemoryService memoryService,
            @Lazy LineReader lineReader) {
        this.state = state;
        this.memoryService = memoryService;
        this.lineReader = lineReader;
    }

    @Command(name = "memory list", description = "List all long-term memories")
    public String listMemories() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        UUID userId = state.getUserId();
        List<Memory> memories;
        try {
            memories = memoryService.getAll(userId).collectList().block(TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to load memories for user: {}", userId, e);
            return "Failed to load memories: " + e.getMessage();
        }

        if (memories == null || memories.isEmpty()) {
            return "No memories found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Long-Term Memories ===\n");
        for (int i = 0; i < memories.size(); i++) {
            Memory m = memories.get(i);
            sb.append(String.format("[%d] Type: %s | %s\n", i + 1, m.type(), m.content()));
        }
        return sb.toString();
    }

    @Command(name = "memory add", description = "Add a new memory manually")
    public String addMemory() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        String typeInput = lineReader.readLine("Memory Type (FACT, GOAL, PREFERENCE, CONTEXT, EVENT): ");
        if (typeInput == null) {
            return "Operation canceled.";
        }

        MemoryType type;
        try {
            type = MemoryType.valueOf(typeInput.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Invalid memory type. Must be one of: FACT, GOAL, PREFERENCE, CONTEXT, EVENT";
        }

        String content = lineReader.readLine("Content: ");
        if (content == null || content.isBlank()) {
            return "Memory content cannot be empty.";
        }

        UUID userId = state.getUserId();
        try {
            Memory saved = memoryService.saveManual(userId, type, content.trim()).block(TIMEOUT);
            if (saved == null) {
                return "Failed to save memory (it might be a duplicate).";
            }
            return "Memory added successfully!";
        } catch (Exception e) {
            log.error("Failed to add memory for user: {}", userId, e);
            return "Failed to add memory: " + e.getMessage();
        }
    }

    @Command(name = "memory delete", description = "Delete a memory by its list number")
    public String deleteMemory(@Option(longName = "number", required = true, description = "The memory index number from the list") int number) {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        UUID userId = state.getUserId();
        List<Memory> memories;
        try {
            memories = memoryService.getAll(userId).collectList().block(TIMEOUT);
        } catch (Exception e) {
            log.error("Failed to load memories for deletion, user: {}", userId, e);
            return "Failed to load memories for deletion: " + e.getMessage();
        }

        if (memories == null || memories.isEmpty()) {
            return "No memories found to delete.";
        }

        if (number < 1 || number > memories.size()) {
            return "Invalid memory number. Check 'memory list' for correct indices.";
        }

        Memory targetMemory = memories.get(number - 1);
        try {
            memoryService.delete(targetMemory.id(), userId).block(TIMEOUT);
            return "Memory deleted successfully!";
        } catch (Exception e) {
            log.error("Failed to delete memory {} for user: {}", targetMemory.id(), userId, e);
            return "Failed to delete memory: " + e.getMessage();
        }
    }

    @Command(name = "memory clear", description = "Clear all long-term memories")
    public String clearMemories() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        String confirmation = lineReader.readLine("Are you sure you want to clear all memories? (yes/no): ");
        if (confirmation == null || !"yes".equalsIgnoreCase(confirmation.trim())) {
            return "Clear operation canceled.";
        }

        UUID userId = state.getUserId();
        try {
            memoryService.deleteAll(userId).block(TIMEOUT);
            return "All memories cleared successfully!";
        } catch (Exception e) {
            log.error("Failed to clear memories for user: {}", userId, e);
            return "Failed to clear memories: " + e.getMessage();
        }
    }
}
