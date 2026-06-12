package ai.jarvis.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * Extracts long-term memories from user messages.
 *
 * WHEN CALLED:
 * After every AI response completes.
 * Always async — never blocks the chat stream.
 *
 * WHAT IT DOES:
 * 1. Takes the user's message
 * 2. Sends it to Ollama with extraction prompt
 * 3. Parses the JSON response
 * 4. Saves each extracted fact via MemoryService
 *
 * WHAT IT EXTRACTS:
 * FACT:       "User's name is Dravin"
 * GOAL:       "User is building Jarvis AI Platform"
 * PREFERENCE: "User prefers dark mode"
 * CONTEXT:    "User uses Windows 11, 16GB RAM"
 * EVENT:      "User published article on Dev.to"
 *
 * WHAT IT DOES NOT EXTRACT:
 * - Greetings ("hello", "hi")
 * - Questions without facts
 * - Temporary statements ("it's raining today")
 * - Very short/vague messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final ChatClient.Builder chatClientBuilder;
    private final MemoryService memoryService;

    /**
     * Extraction prompt template.
     *
     * WHY STRICT JSON FORMAT:
     * We parse this programmatically.
     * If AI returns free text → parsing fails.
     * We explicitly tell it: return ONLY JSON.
     *
     * WHY SHORT LIMIT (max 3 facts):
     * One message rarely has more than 3 key facts.
     * Extracting too many = noise in memory.
     * Quality over quantity.
     */
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

            Input: "I'm building a Spring Boot AI platform"
            Output: [
              {"type":"GOAL","content":"User is building a Spring Boot AI platform"}
            ]
            """;

    /**
     * Extract memories from a user message and save them.
     *
     * CALLED BY: AiOrchestrator.doOnComplete()
     * RUNS ON: boundedElastic thread (blocking Ollama call)
     * NEVER BLOCKS: the caller uses .subscribe() to fire-and-forget
     *
     * @param userId      who sent the message
     * @param sessionId   which session this came from
     * @param userMessage the actual message to analyze
     */
    public Mono<Void> extractAndSave(
            UUID userId,
            UUID sessionId,
            String userMessage) {

        // Skip extraction for very short messages
        // "hi", "yes", "ok" → nothing to extract
        if (userMessage == null
                || userMessage.trim().length() < 10) {
            return Mono.empty();
        }

        return Mono.fromCallable(() ->
                        callExtractionModel(userMessage))
                // boundedElastic = thread pool for
                // blocking calls (Ollama is blocking)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(json ->
                        parseAndSaveAll(
                                json, userId, sessionId))
                .onErrorResume(error -> {
                    // Extraction failure NEVER affects chat
                    // Log quietly and continue
                    log.debug(
                            "Memory extraction skipped "
                                    + "for user={}: {}",
                            userId,
                            error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Call Ollama with extraction prompt.
     * Returns raw JSON string from AI.
     *
     * WHY String not structured type:
     * We parse manually to handle malformed JSON
     * gracefully without crashing.
     *
     * WHY fromCallable + subscribeOn boundedElastic:
     * ChatClient.call() is BLOCKING (not reactive).
     * We must run it on a thread pool designed for
     * blocking operations, NOT on the Netty event loop.
     * Blocking on event loop = deadlock/starvation.
     */
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

    /**
     * Parse JSON array and save each memory.
     *
     * WHY MANUAL JSON PARSING:
     * We do NOT use ObjectMapper here.
     * Why? The AI might return slightly malformed JSON.
     * ObjectMapper throws exception on any error.
     * Manual parsing skips bad entries gracefully.
     *
     * EXAMPLE PARSING:
     * Input:  [{"type":"FACT","content":"User likes Java"}]
     * Step 1: Find all {"type":"...","content":"..."}
     * Step 2: Extract type and content strings
     * Step 3: Convert to MemoryType enum
     * Step 4: Save via MemoryService
     */
    private Mono<Void> parseAndSaveAll(
            String json,
            UUID userId,
            UUID sessionId) {

        if (json == null || json.isBlank()) {
            return Mono.empty();
        }

        String trimmed = json.trim();

        // AI returned empty array → nothing to save
        if (trimmed.equals("[]")
                || trimmed.equals("[ ]")) {
            log.debug(
                    "No memories extracted for user={}",
                    userId);
            return Mono.empty();
        }

        // Parse each memory object from the JSON array
        List<ExtractedMemory> extracted =
                parseJsonArray(trimmed);

        if (extracted.isEmpty()) {
            return Mono.empty();
        }

        log.debug(
                "Extracted {} memories for user={}",
                extracted.size(), userId);

        // Save all extracted memories
        // concatMap = sequential (not parallel)
        // Why sequential? MemoryService checks duplicates.
        // Parallel could cause race condition.
        return reactor.core.publisher.Flux
                .fromIterable(extracted)
                .concatMap(m ->
                        memoryService.save(
                                userId,
                                m.type(),
                                m.content(),
                                sessionId)
                )
                .then();
    }

    /**
     * Parse JSON array of memory objects.
     *
     * HANDLES MALFORMED JSON GRACEFULLY:
     * If AI returns extra text around JSON,
     * we try to find the JSON array within it.
     * Each object parsed individually — bad ones skipped.
     *
     * @param json raw JSON string from AI
     * @return list of valid ExtractedMemory objects
     */
    private List<ExtractedMemory> parseJsonArray(
            String json) {

        java.util.List<ExtractedMemory> results =
                new java.util.ArrayList<>();

        try {
            // Find the JSON array boundaries
            // AI sometimes adds text before/after
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');

            if (start == -1 || end == -1
                    || start >= end) {
                return results;
            }

            String array = json.substring(
                    start + 1, end);

            // Simple regex to find each object
            // Pattern: {"type":"TYPE","content":"text"}
            java.util.regex.Pattern pattern =
                    java.util.regex.Pattern.compile(
                            "\\{[^}]*\"type\"\\s*:\\s*"
                                    + "\"([^\"]+)\"[^}]*"
                                    + "\"content\"\\s*:\\s*"
                                    + "\"([^\"]+)\"[^}]*\\}",
                            java.util.regex.Pattern
                                    .DOTALL
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
                    // Unknown type → skip this entry
                    log.debug(
                            "Skipping unknown memory type: {}",
                            matcher.group(1));
                }
            }

        } catch (Exception e) {
            // Any parsing error → return what we have
            log.debug(
                    "JSON parse partial failure: {}",
                    e.getClass().getSimpleName());
        }

        return results;
    }

    // ── Inner Record ──────────────────────────────

    /**
     * Represents one extracted memory before saving.
     * Temporary data class — not stored in DB directly.
     */
    private record ExtractedMemory(
            MemoryType type,
            String content) {}
}