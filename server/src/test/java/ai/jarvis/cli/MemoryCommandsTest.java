package ai.jarvis.cli;

import ai.jarvis.memory.Memory;
import ai.jarvis.memory.MemoryService;
import ai.jarvis.memory.MemoryType;
import org.jline.reader.LineReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryCommands Unit Tests")
class MemoryCommandsTest {

    @Mock
    private CliStateManager state;

    @Mock
    private MemoryService memoryService;

    @Mock
    private LineReader lineReader;

    private MemoryCommands memoryCommands;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        memoryCommands = new MemoryCommands(state, memoryService, lineReader);
    }

    @Nested
    @DisplayName("Unauthorized Context Tests")
    class UnauthorizedContext {

        @BeforeEach
        void setUp() {
            when(state.isLoggedIn()).thenReturn(false);
        }

        @Test
        @DisplayName("listMemories should return unauthorized message when not logged in")
        void testListMemories_Unauthorized() {
            String result = memoryCommands.listMemories();
            assertTrue(result.contains("Not logged in"));
        }

        @Test
        @DisplayName("addMemory should return unauthorized message when not logged in")
        void testAddMemory_Unauthorized() {
            String result = memoryCommands.addMemory();
            assertTrue(result.contains("Not logged in"));
        }

        @Test
        @DisplayName("deleteMemory should return unauthorized message when not logged in")
        void testDeleteMemory_Unauthorized() {
            String result = memoryCommands.deleteMemory(1);
            assertTrue(result.contains("Not logged in"));
        }

        @Test
        @DisplayName("clearMemories should return unauthorized message when not logged in")
        void testClearMemories_Unauthorized() {
            String result = memoryCommands.clearMemories();
            assertTrue(result.contains("Not logged in"));
        }
    }

    @Nested
    @DisplayName("Authorized Context Tests")
    class AuthorizedContext {

        @BeforeEach
        void setUp() {
            when(state.isLoggedIn()).thenReturn(true);
        }

        @Test
        @DisplayName("listMemories should show memories when list is not empty")
        void testListMemories_Success() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "Test Content", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(memory));

            String result = memoryCommands.listMemories();
            assertTrue(result.contains("=== Long-Term Memories ==="));
            assertTrue(result.contains("Test Content"));
        }

        @Test
        @DisplayName("listMemories should report when no memories exist")
        void testListMemories_Empty() {
            when(state.getUserId()).thenReturn(userId);
            when(memoryService.getAll(userId)).thenReturn(Flux.empty());

            String result = memoryCommands.listMemories();
            assertTrue(result.contains("No memories found"));
        }

        @Test
        @DisplayName("listMemories should emit output matching sequential emission order")
        void testListMemories_EmissionOrder() {
            when(state.getUserId()).thenReturn(userId);
            Memory m1 = Memory.create(userId, MemoryType.FACT, "First Memory", null);
            Memory m2 = Memory.create(userId, MemoryType.FACT, "Second Memory", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(m1, m2));

            String result = memoryCommands.listMemories();
            assertTrue(result.indexOf("First Memory") < result.indexOf("Second Memory"));
        }

        @Test
        @DisplayName("addMemory should successfully add a memory with valid inputs")
        void testAddMemory_Success() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "New Fact", null);
            
            when(lineReader.readLine(anyString()))
                    .thenReturn("FACT")
                    .thenReturn("New Fact");
                    
            when(memoryService.saveManual(eq(userId), eq(MemoryType.FACT), eq("New Fact"))).thenReturn(Mono.just(memory));

            String result = memoryCommands.addMemory();
            assertTrue(result.contains("Memory added successfully"));
        }

        @Test
        @DisplayName("addMemory should fail with invalid type input")
        void testAddMemory_InvalidType() {
            when(lineReader.readLine(anyString())).thenReturn("INVALID_TYPE");

            String result = memoryCommands.addMemory();
            assertTrue(result.contains("Invalid memory type"));
            verify(memoryService, never()).saveManual(any(), any(), any());
        }

        @Test
        @DisplayName("addMemory should fail when content is blank")
        void testAddMemory_BlankContent() {
            when(lineReader.readLine(anyString()))
                    .thenReturn("FACT")
                    .thenReturn("   ");

            String result = memoryCommands.addMemory();
            assertTrue(result.contains("Memory content cannot be empty"));
            verify(memoryService, never()).saveManual(any(), any(), any());
        }

        @Test
        @DisplayName("addMemory should fail when saveManual returns empty representing duplicate")
        void testAddMemory_Duplicate() {
            when(state.getUserId()).thenReturn(userId);
            when(lineReader.readLine(anyString()))
                    .thenReturn("FACT")
                    .thenReturn("Duplicate Fact");
            when(memoryService.saveManual(eq(userId), eq(MemoryType.FACT), eq("Duplicate Fact"))).thenReturn(Mono.empty());

            String result = memoryCommands.addMemory();
            assertTrue(result.contains("Failed to save memory (it might be a duplicate)"));
        }

        @Test
        @DisplayName("deleteMemory should successfully delete memory by valid index")
        void testDeleteMemory_Success() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "To Delete", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(memory));
            when(memoryService.delete(memory.id(), userId)).thenReturn(Mono.empty());

            String result = memoryCommands.deleteMemory(1);
            assertTrue(result.contains("Memory deleted successfully"));
        }

        @Test
        @DisplayName("deleteMemory should fail with out of bounds index")
        void testDeleteMemory_OutOfBounds() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "Keep This", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(memory));

            String result = memoryCommands.deleteMemory(5);
            assertTrue(result.contains("Invalid memory number"));
            verify(memoryService, never()).delete(any(), any());
        }

        @Test
        @DisplayName("deleteMemory should fail with index 0")
        void testDeleteMemory_IndexZero() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "Keep This", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(memory));

            String result = memoryCommands.deleteMemory(0);
            assertTrue(result.contains("Invalid memory number"));
            verify(memoryService, never()).delete(any(), any());
        }

        @Test
        @DisplayName("deleteMemory should fail with negative index")
        void testDeleteMemory_NegativeIndex() {
            when(state.getUserId()).thenReturn(userId);
            Memory memory = Memory.create(userId, MemoryType.FACT, "Keep This", null);
            when(memoryService.getAll(userId)).thenReturn(Flux.just(memory));

            String result = memoryCommands.deleteMemory(-1);
            assertTrue(result.contains("Invalid memory number"));
            verify(memoryService, never()).delete(any(), any());
        }

        @Test
        @DisplayName("clearMemories should clear all when user confirms lowercase yes")
        void testClearMemories_ConfirmLowercaseYes() {
            when(state.getUserId()).thenReturn(userId);
            when(lineReader.readLine(anyString())).thenReturn("yes");
            when(memoryService.deleteAll(userId)).thenReturn(Mono.empty());

            String result = memoryCommands.clearMemories();
            assertTrue(result.contains("All memories cleared successfully"));
        }

        @Test
        @DisplayName("clearMemories should clear all when user confirms uppercase YES")
        void testClearMemories_ConfirmUppercaseYes() {
            when(state.getUserId()).thenReturn(userId);
            when(lineReader.readLine(anyString())).thenReturn("YES");
            when(memoryService.deleteAll(userId)).thenReturn(Mono.empty());

            String result = memoryCommands.clearMemories();
            assertTrue(result.contains("All memories cleared successfully"));
        }

        @Test
        @DisplayName("clearMemories should cancel when user confirms no")
        void testClearMemories_ConfirmNo() {
            when(lineReader.readLine(anyString())).thenReturn("no");

            String result = memoryCommands.clearMemories();
            assertTrue(result.contains("Clear operation canceled"));
            verify(memoryService, never()).deleteAll(any());
        }
    }
}
