package ai.jarvis.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final ChatClient.Builder chatClientBuilder;
    private final MemoryService memoryService;

    private static final String EXTRACTION_PROMPT = """
            You are a memory extraction assistant.
            Analyze the user message and extract important
            long-term facts worth remembering.

            Return ONLY a JSON array. No other text.
            Each item: {"type": "TYPE", "content": "fact"}

            Types:
            FACT       - true info about the user
            GOAL       - what user wants to achieve
            PREFERENCE - how user likes things done
            CONTEXT    - current project or situation
            EVENT      - something that happened

            Rules:
            - Extract max 3 facts
            - Only clear, specific, lasting facts
            - Skip greetings, questions, vague statements
            - Skip temporary info ("it's raining today")
            - If nothing to extract, return: []

            Examples:
            Input: "I prefer dark mode and use Windows 11"
            Output: [
              {"type":"PREFERENCE","content":"User prefers dark mode"},
              {"type":"CONTEXT","content":"User uses Windows 11"}
            ]

            Input: "hello how are you"
            Output: []
            """;

    // Fix 2: timeout constant — configurable and visible
    private static final Duration EXTRACTION_TIMEOUT =
            Duration.ofSeconds(15);

    // Fix 3: hard cap enforced in code, not just in prompt
    private static final int MAX_MEMORIES_PER_MESSAGE = 3;

    public Mono<Void> extractAndSave(
            UUID userId,
            UUID sessionId,
            String userMessage) {

        // Fix 1: guard null identifiers FIRST
        // Prevents wasted Ollama calls and DB errors
        if (userId == null || sessionId == null) {
            log.debug(
                    "Skipping extraction: null "
                            + "userId or sessionId");
            return Mono.empty();
        }

        // Short messages have nothing to extract
        if (userMessage == null
                || userMessage.trim().length() < 10) {
            return Mono.empty();
        }

        return Mono.fromCallable(() ->
                        callExtractionModel(userMessage))
                .subscribeOn(Schedulers.boundedElastic())
                // Fix 2: timeout prevents thread starvation
                // if Ollama is slow or unresponsive
                .timeout(EXTRACTION_TIMEOUT)
                .flatMap(json ->
                        parseAndSaveAll(
                                json, userId, sessionId))
                .onErrorResume(error -> {
                    // Handles ALL errors including TimeoutException
                    // Extraction failure NEVER affects chat
                    log.debug(
                            "Memory extraction skipped "
                                    + "for user={}: {}",
                            userId,
                            error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    // ── Private Helpers ───────────────────────────

    private String callExtractionModel(
            String userMessage) {

        ChatClient chatClient =
                chatClientBuilder.build();

        List<org.springframework.ai.chat.messages.Message>
                messages = List.of(
                new SystemMessage(EXTRACTION_PROMPT),
                new UserMessage(userMessage)
        );

        return chatClient
                .prompt(new Prompt(messages))
                .call()
                .content();
    }

    private Mono<Void> parseAndSaveAll(
            String json,
            UUID userId,
            UUID sessionId) {

        if (json == null || json.isBlank()) {
            return Mono.empty();
        }

        String trimmed = json.trim();

        if (trimmed.equals("[]")
                || trimmed.equals("[ ]")) {
            log.debug(
                    "No memories extracted for user={}",
                    userId);
            return Mono.empty();
        }

        List<ExtractedMemory> extracted =
                parseJsonArray(trimmed);

        if (extracted.isEmpty()) {
            return Mono.empty();
        }

        log.debug(
                "Saving {} memories for user={}",
                Math.min(extracted.size(),
                        MAX_MEMORIES_PER_MESSAGE),
                userId);

        return reactor.core.publisher.Flux
                .fromIterable(extracted)
                // Fix 3: hard cap regardless of AI output
                // Prompt says max 3, but AI is not 100% reliable
                // .take(3) GUARANTEES max 3 saves
                .take(MAX_MEMORIES_PER_MESSAGE)
                // concatMap = sequential saves
                // Prevents race condition in duplicate check
                .concatMap(m ->
                        memoryService.save(
                                userId,
                                m.type(),
                                m.content(),
                                sessionId)
                )
                .then();
    }

    private List<ExtractedMemory> parseJsonArray(
            String json) {

        java.util.List<ExtractedMemory> results =
                new java.util.ArrayList<>();

        try {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');

            if (start == -1 || end == -1
                    || start >= end) {
                return results;
            }

            String array = json.substring(
                    start + 1, end);

            java.util.regex.Pattern pattern =
                    java.util.regex.Pattern.compile(
                            "\\{[^}]*\"type\"\\s*:\\s*"
                                    + "\"([^\"]+)\"[^}]*"
                                    + "\"content\"\\s*:\\s*"
                                    + "\"([^\"]+)\"[^}]*\\}",
                            java.util.regex.Pattern.DOTALL
                    );

            java.util.regex.Matcher matcher =
                    pattern.matcher(array);

            while (matcher.find()) {
                try {
                    String typeStr =
                            matcher.group(1)
                                    .trim()
                                    .toUpperCase();
                    String content =
                            matcher.group(2).trim();

                    if (content.isBlank()) continue;

                    MemoryType type =
                            MemoryType.valueOf(typeStr);
                    results.add(
                            new ExtractedMemory(
                                    type, content));

                } catch (IllegalArgumentException e) {
                    log.debug(
                            "Skipping unknown memory type: {}",
                            matcher.group(1));
                }
            }

        } catch (Exception e) {
            log.debug(
                    "JSON parse partial failure: {}",
                    e.getClass().getSimpleName());
        }

        return results;
    }

    private record ExtractedMemory(
            MemoryType type,
            String content) {}
}