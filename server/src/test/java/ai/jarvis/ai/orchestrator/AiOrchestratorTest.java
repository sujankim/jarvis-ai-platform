package ai.jarvis.ai.orchestrator;

import static ai.jarvis.ai.orchestrator.OrchestratorRequestFactory.generateOrchestrationRequest;
import static ai.jarvis.chat.message.MessageFactory.generateAssistantMessage;
import static ai.jarvis.chat.message.MessageFactory.generateUserMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import ai.jarvis.ai.prompt.PromptAssembler;
import ai.jarvis.ai.prompt.WorkingMemoryBuilder;
import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.GeminiProvider;
import ai.jarvis.ai.provider.OllamaProvider;
import ai.jarvis.ai.provider.ProviderRouter;
import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.message.MessageRole;
import ai.jarvis.chat.session.ChatSessionRepository;
import ai.jarvis.memory.MemoryExtractionService;
import ai.jarvis.memory.MemoryService;
import ai.jarvis.memory.session.SessionMemoryService;
import ai.jarvis.rag.RagSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorTest {

    private static final String MEMORY_CONTEXT = "memory context";
    private static final String RAG_CONTEXT = "rag context";
    private static final String WORKING_MEMORY = "working memory";
    private static final String MODEL_NAME = "abstract_model_name";
    public static final String OLLAMA_MODEL_NAME = "ollama_model_name";
    public static final String GEMINI_MODEL_NAME = "gemini_model_name";

    @Mock
    private ProviderRouter providerRouter;

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Mock
    private PromptAssembler promptAssembler;

    @Mock
    private WorkingMemoryBuilder workingMemoryBuilder;

    @Mock
    private SessionMemoryService sessionMemoryService;

    @Mock
    private MemoryService memoryService;

    @Mock
    private MemoryExtractionService memoryExtractionService;

    @Mock
    private RagSearchService ragSearchService;

    @InjectMocks
    private AiOrchestrator aiOrchestrator;

    @Test
    @DisplayName("Should save user message before calling AI")
    void shouldSaveUserMessageBeforeCallingAI() {
        // Given
        OrchestratorRequest orchestratorRequest = generateOrchestrationRequest();

        UUID sessionId = orchestratorRequest.sessionId();
        UUID userId = orchestratorRequest.userId();
        String username = orchestratorRequest.username();
        String message = orchestratorRequest.message();
        Message userMsg = generateUserMessage(sessionId, message);

        Prompt prompt = mock(Prompt.class);
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.streamChat(prompt)).thenReturn(Flux.just("Hello"," back"));
        when(aiProvider.getModelName()).thenReturn(MODEL_NAME);

        when(this.r2dbcEntityTemplate.insert(any(Message.class))).thenReturn(Mono.just(userMsg));
        when(this.sessionMemoryService.loadHistory(sessionId)).thenReturn(Mono.just(List.of()));
        when(this.memoryService.formatForPrompt(userId, message)).thenReturn(Mono.just(MEMORY_CONTEXT));
        when(this.ragSearchService.formatForPrompt(userId, message)).thenReturn(Mono.just(RAG_CONTEXT));
        when(this.providerRouter.route()).thenReturn(Mono.just(aiProvider));
        when(this.workingMemoryBuilder.build(username, orchestratorRequest.role(), sessionId.toString(), MODEL_NAME)).thenReturn(WORKING_MEMORY);
        when(this.promptAssembler.assemble(message, WORKING_MEMORY, List.of(), username, MEMORY_CONTEXT, RAG_CONTEXT)).thenReturn(prompt);
        when(this.sessionMemoryService.onMessageSaved(sessionId)).thenReturn(Mono.empty());
        when(this.memoryExtractionService.extractAndSave(userId, sessionId, message)).thenReturn(Mono.empty());
        when(this.sessionRepository.incrementMessageCount(sessionId, 2)).thenReturn(Mono.just(1));

        // When + Then
        StepVerifier.create(this.aiOrchestrator.chat(orchestratorRequest))
                .expectNext("Hello")
                .expectNext(" back")
                .verifyComplete();

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        InOrder inOrder = inOrder(this.r2dbcEntityTemplate, aiProvider);
        inOrder.verify(this.r2dbcEntityTemplate).insert(messageArgumentCaptor.capture());
        inOrder.verify(aiProvider).streamChat(any(Prompt.class));

        Message userMessage = messageArgumentCaptor.getAllValues()
                .getFirst();
        assertNotNull(userMessage.id());
        assertEquals(sessionId, userMessage.sessionId());
        assertEquals(MessageRole.USER, userMessage.role());
        assertEquals(message, userMessage.content());
    }

    private static Stream<Arguments> provideAiProviderToModelName() {
        return Stream.of(
                Arguments.of(
                        "Should call Ollama when available",
                        mock(OllamaProvider.class), OLLAMA_MODEL_NAME),
                Arguments.of(
                        "Should call Gemini when available",
                        mock(GeminiProvider.class), GEMINI_MODEL_NAME)
        );
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Should call supported provided model")
    @MethodSource("provideAiProviderToModelName")
    void shouldCallSupportedProvidedModel(String testName, AiProvider aiProvider, String modelName) {
        // Given
        OrchestratorRequest orchestratorRequest = generateOrchestrationRequest();

        UUID sessionId = orchestratorRequest.sessionId();
        UUID userId = orchestratorRequest.userId();
        String username = orchestratorRequest.username();
        String message = orchestratorRequest.message();
        Message userMsg = generateUserMessage(sessionId, message);

        Prompt prompt = mock(Prompt.class);
        when(aiProvider.streamChat(prompt)).thenReturn(Flux.just("Hello"," back"));
        when(aiProvider.getModelName()).thenReturn(modelName);

        when(this.r2dbcEntityTemplate.insert(any(Message.class))).thenReturn(Mono.just(userMsg));
        when(this.sessionMemoryService.loadHistory(sessionId)).thenReturn(Mono.just(List.of()));
        when(this.memoryService.formatForPrompt(userId, message)).thenReturn(Mono.just(MEMORY_CONTEXT));
        when(this.ragSearchService.formatForPrompt(userId, message)).thenReturn(Mono.just(RAG_CONTEXT));
        when(this.providerRouter.route()).thenReturn(Mono.just(aiProvider));
        when(this.workingMemoryBuilder.build(username, orchestratorRequest.role(), sessionId.toString(), modelName)).thenReturn(WORKING_MEMORY);
        when(this.promptAssembler.assemble(message, WORKING_MEMORY, List.of(), username, MEMORY_CONTEXT, RAG_CONTEXT)).thenReturn(prompt);
        when(this.sessionMemoryService.onMessageSaved(sessionId)).thenReturn(Mono.empty());
        when(this.memoryExtractionService.extractAndSave(userId, sessionId, message)).thenReturn(Mono.empty());
        when(this.sessionRepository.incrementMessageCount(sessionId, 2)).thenReturn(Mono.just(1));

        // When + Then
        StepVerifier.create(this.aiOrchestrator.chat(orchestratorRequest))
                .expectNext("Hello")
                .expectNext(" back")
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return error flux when all providers unavailable")
    void shouldReturnErrorFluxWhenAllProvidersUnavailable() {
        // Given
        RuntimeException exceptionToThrow = new RuntimeException("No AI Provider available");

        OrchestratorRequest orchestratorRequest = generateOrchestrationRequest();

        UUID sessionId = orchestratorRequest.sessionId();
        UUID userId = orchestratorRequest.userId();
        String message = orchestratorRequest.message();
        Message userMsg = generateUserMessage(sessionId, message);

        when(this.r2dbcEntityTemplate.insert(any(Message.class))).thenReturn(Mono.just(userMsg));
        when(this.sessionMemoryService.loadHistory(sessionId)).thenReturn(Mono.just(List.of()));
        when(this.memoryService.formatForPrompt(userId, message)).thenReturn(Mono.just(MEMORY_CONTEXT));
        when(this.ragSearchService.formatForPrompt(userId, message)).thenReturn(Mono.just(RAG_CONTEXT));
        when(this.providerRouter.route()).thenReturn(Mono.error(exceptionToThrow));
        when(this.sessionMemoryService.onMessageSaved(sessionId)).thenReturn(Mono.empty());
        when(this.memoryExtractionService.extractAndSave(userId, sessionId, message)).thenReturn(Mono.empty());

        // When + Then
        StepVerifier.create(this.aiOrchestrator.chat(orchestratorRequest))
                .verifyErrorMatches(Predicate.isEqual(exceptionToThrow));
    }

    @Test
    @DisplayName("Should save assistant message after stream completes")
    void shouldSaveAssistantMessageAfterStreamCompletes() {
        // Given
        OrchestratorRequest orchestratorRequest = generateOrchestrationRequest();

        String assistantMessageContent = "Hello back";
        UUID sessionId = orchestratorRequest.sessionId();
        UUID userId = orchestratorRequest.userId();
        String username = orchestratorRequest.username();
        String message = orchestratorRequest.message();

        Message userMsg = generateUserMessage(sessionId, message);
        Message assistantMsg = generateAssistantMessage(sessionId, assistantMessageContent, MODEL_NAME);

        Prompt prompt = mock(Prompt.class);
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.streamChat(prompt)).thenReturn(Flux.just("Hello"," back"));
        when(aiProvider.getModelName()).thenReturn(MODEL_NAME);

        when(this.r2dbcEntityTemplate.insert(any(Message.class))).thenReturn(Mono.just(userMsg)).thenReturn(Mono.just(assistantMsg));
        when(this.sessionMemoryService.loadHistory(sessionId)).thenReturn(Mono.just(List.of()));
        when(this.memoryService.formatForPrompt(userId, message)).thenReturn(Mono.just(MEMORY_CONTEXT));
        when(this.ragSearchService.formatForPrompt(userId, message)).thenReturn(Mono.just(RAG_CONTEXT));
        when(this.providerRouter.route()).thenReturn(Mono.just(aiProvider));
        when(this.workingMemoryBuilder.build(username, orchestratorRequest.role(), sessionId.toString(), MODEL_NAME)).thenReturn(WORKING_MEMORY);
        when(this.promptAssembler.assemble(message, WORKING_MEMORY, List.of(), username, MEMORY_CONTEXT, RAG_CONTEXT)).thenReturn(prompt);
        when(this.sessionMemoryService.onMessageSaved(sessionId)).thenReturn(Mono.empty());
        when(this.memoryExtractionService.extractAndSave(userId, sessionId, message)).thenReturn(Mono.empty());
        when(this.sessionRepository.incrementMessageCount(sessionId, 2)).thenReturn(Mono.just(1));

        // When + Then
        StepVerifier.create(this.aiOrchestrator.chat(orchestratorRequest))
                .expectNext("Hello")
                .expectNext(" back")
                .verifyComplete();

        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(this.r2dbcEntityTemplate, times(2)).insert(messageArgumentCaptor.capture());
        Message assistantMessage = messageArgumentCaptor.getValue();
        assertNotNull(assistantMessage.id());
        assertEquals(sessionId, assistantMessage.sessionId());
        assertEquals(MessageRole.ASSISTANT, assistantMessage.role());
        assertEquals(assistantMessageContent, assistantMessage.content());
    }

    @Test
    @DisplayName("Should load session history for context")
    void shouldLoadSessionHistoryForContext() {
        // Given
        OrchestratorRequest orchestratorRequest = generateOrchestrationRequest();

        UUID sessionId = orchestratorRequest.sessionId();
        UUID userId = orchestratorRequest.userId();
        String username = orchestratorRequest.username();
        String message = orchestratorRequest.message();

        Message firstHistoryMessage = generateUserMessage(sessionId, "first message");
        Message secondHistoryMessage = generateUserMessage(sessionId, "second message");
        List<Message> messageHistory = List.of(firstHistoryMessage, secondHistoryMessage);
        Message userMsg = generateUserMessage(sessionId, message);

        Prompt prompt = mock(Prompt.class);
        AiProvider aiProvider = mock(AiProvider.class);
        when(aiProvider.streamChat(prompt)).thenReturn(Flux.just("Hello"," back"));
        when(aiProvider.getModelName()).thenReturn(MODEL_NAME);

        when(this.r2dbcEntityTemplate.insert(any(Message.class))).thenReturn(Mono.just(userMsg));
        when(this.sessionMemoryService.loadHistory(sessionId)).thenReturn(Mono.just(messageHistory));
        when(this.memoryService.formatForPrompt(userId, message)).thenReturn(Mono.just(MEMORY_CONTEXT));
        when(this.ragSearchService.formatForPrompt(userId, message)).thenReturn(Mono.just(RAG_CONTEXT));
        when(this.providerRouter.route()).thenReturn(Mono.just(aiProvider));
        when(this.workingMemoryBuilder.build(username, orchestratorRequest.role(), sessionId.toString(), MODEL_NAME)).thenReturn(WORKING_MEMORY);
        when(this.promptAssembler.assemble(message, WORKING_MEMORY, messageHistory, username, MEMORY_CONTEXT, RAG_CONTEXT)).thenReturn(prompt);
        when(this.sessionMemoryService.onMessageSaved(sessionId)).thenReturn(Mono.empty());
        when(this.memoryExtractionService.extractAndSave(userId, sessionId, message)).thenReturn(Mono.empty());
        when(this.sessionRepository.incrementMessageCount(sessionId, 2)).thenReturn(Mono.just(1));

        // When + Then
        StepVerifier.create(this.aiOrchestrator.chat(orchestratorRequest))
                .expectNext("Hello")
                .expectNext(" back")
                .verifyComplete();
    }

}
