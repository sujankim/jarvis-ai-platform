package ai.jarvis.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractionService Additional Tests")
class MemoryExtractionServiceAdditionalTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private MemoryService memoryService;

    private MemoryExtractionService service;

    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {

        service = new MemoryExtractionService(
                chatClientBuilder,
                memoryService);

        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        when(chatClientBuilder.build())
                .thenReturn(chatClient);

        when(chatClient.prompt(any(Prompt.class)))
                .thenReturn(requestSpec);

        when(requestSpec.call())
                .thenReturn(callResponseSpec);
    }

    @Test
    @DisplayName("valid JSON extraction saves memories with embedding")
    void shouldSaveExtractedMemories() {

        when(callResponseSpec.content())
                .thenReturn("""
                        [
                          {
                            "type": "PREFERENCE",
                            "content": "User likes Java"
                          }
                        ]
                        """);

        // saveWithEmbedding() is called because extracted memories
        // must receive pgvector embeddings to be discoverable
        // via semantic search. save() only stores text with no embedding.
        when(memoryService.saveWithEmbedding(
                any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                        service.extractAndSave(
                                userId,
                                sessionId,
                                "I really enjoy coding in Java"))
                .verifyComplete();

        verify(memoryService)
                .saveWithEmbedding(any(), any(), any(), any());
    }

    @Test
    @DisplayName("empty JSON array saves nothing")
    void shouldSaveNothingForEmptyArray() {

        when(callResponseSpec.content())
                .thenReturn("[]");

        StepVerifier.create(
                        service.extractAndSave(
                                userId,
                                sessionId,
                                "I really enjoy coding in Java"))
                .verifyComplete();

        // Neither save() nor saveWithEmbedding() should be called
        // when AI returns empty array
        verify(memoryService, never())
                .saveWithEmbedding(any(), any(), any(), any());

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("malformed JSON is handled gracefully")
    void shouldHandleMalformedJson() {

        when(callResponseSpec.content())
                .thenReturn("This is not JSON at all!");

        StepVerifier.create(
                        service.extractAndSave(
                                userId,
                                sessionId,
                                "I really enjoy coding in Java"))
                .verifyComplete();

        // Malformed JSON must never reach the service layer
        verify(memoryService, never())
                .saveWithEmbedding(any(), any(), any(), any());

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("maximum of 3 memories enforced")
    void shouldEnforceMaxThreeMemories() {

        when(callResponseSpec.content())
                .thenReturn("""
                        [
                          {"type":"PREFERENCE","content":"User likes Java"},
                          {"type":"FACT","content":"User uses Windows"},
                          {"type":"GOAL","content":"User builds Jarvis"},
                          {"type":"CONTEXT","content":"User has 16GB RAM"},
                          {"type":"EVENT","content":"User published article"}
                        ]
                        """);


        when(memoryService.saveWithEmbedding(
                any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                        service.extractAndSave(
                                userId,
                                sessionId,
                                "I really enjoy coding in Java"))
                .verifyComplete();

        verify(memoryService, times(3))
                .saveWithEmbedding(any(), any(), any(), any());
    }
}