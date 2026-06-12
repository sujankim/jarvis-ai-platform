package ai.jarvis.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryExtractionService Tests")
class MemoryExtractionServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private MemoryService memoryService;

    private MemoryExtractionService service;
    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        service = new MemoryExtractionService(
                chatClientBuilder, memoryService);
    }

    // ── Null/empty guard tests ────────────────────

    @Test
    @DisplayName("skips extraction when userId is null")
    void shouldSkipWhenUserIdIsNull() {
        StepVerifier
                .create(service.extractAndSave(
                        null, sessionId,
                        "I am building a Java platform"))
                .verifyComplete();

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips extraction when sessionId is null")
    void shouldSkipWhenSessionIdIsNull() {
        StepVerifier
                .create(service.extractAndSave(
                        userId, null,
                        "I am building a Java platform"))
                .verifyComplete();

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips short messages under 10 chars")
    void shouldSkipShortMessages() {
        StepVerifier
                .create(service.extractAndSave(
                        userId, sessionId, "hi"))
                .verifyComplete();

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips null messages")
    void shouldSkipNullMessages() {
        StepVerifier
                .create(service.extractAndSave(
                        userId, sessionId, null))
                .verifyComplete();

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }

    @Test
    @DisplayName("skips blank messages under 10 chars")
    void shouldSkipBlankMessages() {
        // Fix 4: correct name — this tests SHORT MESSAGE guard
        // NOT the JSON array parser
        StepVerifier
                .create(service.extractAndSave(
                        userId, sessionId, "   "))
                .verifyComplete();

        verify(memoryService, never())
                .save(any(), any(), any(), any());
    }
}