package ai.jarvis.cli;

import ai.jarvis.memory.Memory;
import ai.jarvis.memory.MemoryService;
import ai.jarvis.memory.MemoryType;
import org.jline.reader.LineReader;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MemoryCommands {

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
        List<Memory> memories = memoryService.getAll(userId).collectList().block();

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
        Memory saved = memoryService.saveManual(userId, type, content.trim()).block();

        if (saved == null) {
            return "Failed to save memory (it might be a duplicate).";
        }

        return "Memory added successfully!";
    }

    @Command(name = "memory delete", description = "Delete a memory by its list number")
    public String deleteMemory(@Option(required = true, description = "The memory index number from the list") int number) {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        UUID userId = state.getUserId();
        List<Memory> memories = memoryService.getAll(userId).collectList().block();

        if (memories == null || memories.isEmpty()) {
            return "No memories found to delete.";
        }

        if (number < 1 || number > memories.size()) {
            return "Invalid memory number. Check 'memory list' for correct indices.";
        }

        Memory targetMemory = memories.get(number - 1);
        try {
            memoryService.delete(targetMemory.id(), userId).block();
            return "Memory deleted successfully!";
        } catch (Exception e) {
            return "Failed to delete memory: " + e.getMessage();
        }
    }

    @Command(name = "memory clear", description = "Clear all long-term memories")
    public String clearMemories() {
        if (!state.isLoggedIn()) {
            return "Not logged in. Type: login";
        }

        String confirmation = lineReader.readLine("Are you sure you want to clear all memories? (yes/no): ");
        if (!"yes".equalsIgnoreCase(confirmation.trim())) {
            return "Clear operation canceled.";
        }

        UUID userId = state.getUserId();
        try {
            memoryService.deleteAll(userId).block();
            return "All memories cleared successfully!";
        } catch (Exception e) {
            return "Failed to clear memories: " + e.getMessage();
        }
    }
}
