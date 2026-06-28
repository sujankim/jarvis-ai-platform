package ai.jarvis.agents;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plans agent execution by asking the AI model
 * what the next ReACT step should be.
 *
 * DOES NOT execute tools — only produces the
 * AI's structured reasoning about what to do.
 * AgentExecutor handles actual tool execution.
 *
 * STRUCTURED OUTPUT FORMAT:
 * The system prompt instructs AI to respond in
 * a specific format that parseResponse() extracts.
 * This avoids JSON parsing issues and works well
 * with all Ollama models.
 *
 * THOUGHT/ACTION/INPUT format (tool call):
 *   THOUGHT: reasoning here
 *   ACTION: toolMethodName
 *   INPUT: tool input text
 *
 * THOUGHT/FINAL_ANSWER format (task complete):
 *   THOUGHT: reasoning here
 *   FINAL_ANSWER: complete answer here
 *
 *
 * extractAfter() now uses regex anchored to line starts.
 * Previous indexOf() approach broke when tool inputs
 * contained label-like text:
 * - "Use the literal string ACTION: to do X"
 *   would be truncated at "ACTION:"
 * - "THOUGHT: I need to search" in a FINAL_ANSWER
 *   would be truncated at the embedded "THOUGHT:"
 * Regex with (?m)^LABEL anchors to line beginnings only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Pre-compiled patterns for each label.
     * Anchored to line starts with (?m) multiline flag.
     * (?ms) = multiline + dotall for multi-line content.
     *
     * WHY pre-compiled:
     * parseResponse() is called for every AI step.
     * Compiling patterns on every call is wasteful.
     * Static pre-compilation runs once at class load.
     *
     * Pattern: ^LABEL\s*(.*?)(?=^NEXT_LABEL|\z)
     * Captures everything after LABEL until next label
     * at a line start or end of string.
     */
    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile(
                    "(?ms)^THOUGHT:\\s*(.*?)"
                            + "(?=^(?:ACTION:|INPUT:"
                            + "|FINAL_ANSWER:)|\\z)");

    private static final Pattern ACTION_PATTERN =
            Pattern.compile(
                    "(?ms)^ACTION:\\s*(.*?)"
                            + "(?=^(?:THOUGHT:|INPUT:"
                            + "|FINAL_ANSWER:)|\\z)");

    private static final Pattern INPUT_PATTERN =
            Pattern.compile(
                    "(?ms)^INPUT:\\s*(.*?)"
                            + "(?=^(?:THOUGHT:|ACTION:"
                            + "|FINAL_ANSWER:)|\\z)");

    private static final Pattern FINAL_PATTERN =
            Pattern.compile(
                    "(?ms)^FINAL_ANSWER:\\s*(.*?)"
                            + "(?=^(?:THOUGHT:|ACTION:"
                            + "|INPUT:)|\\z)");

    /**
     * System prompt instructing AI to act as ReACT agent.
     * {tools} placeholder replaced before each request.
     *
     * The structured format is important — parseResponse()
     * depends on exact label positions at line starts.
     */
    private static final String AGENT_SYSTEM_PROMPT =
            """
            You are an AI agent that solves tasks step by step.
            
            You have access to these tools:
            {tools}
            
            For each step, respond in EXACTLY this format:
            
            THOUGHT: [your reasoning about what to do next]
            ACTION: [tool_method_name]
            INPUT: [input to the tool]
            
            When you have enough information to answer:
            
            THOUGHT: [your final reasoning]
            FINAL_ANSWER: [your complete answer to the user]
            
            Rules:
            - Always start with THOUGHT
            - ACTION must be the exact method name shown above
            - Use tools only when you need real-time data
            - Never make up information you don't have
            - Maximum 10 tool calls per task
            """;

    /**
     * Ask the AI what the next ReACT step should be.
     *
     * Sends the full execution history so the AI knows
     * what has already been done and what comes next.
     *
     * Runs on boundedElastic because ChatClient.call()
     * is a blocking HTTP call to Ollama.
     *
     * @param goal             user's original task
     * @param toolList         formatted available tools
     * @param executionHistory previous steps as text
     * @return Mono<String> raw AI response text
     */
    public Mono<String> planNextStep(
            String goal,
            String toolList,
            String executionHistory) {

        return Mono.fromCallable(() -> {

                    ChatClient client =
                            chatClientBuilder.build();

                    String systemPrompt =
                            AGENT_SYSTEM_PROMPT
                                    .replace(
                                            "{tools}",
                                            toolList);

                    StringBuilder userMsg =
                            new StringBuilder();
                    userMsg.append("TASK: ")
                            .append(goal)
                            .append("\n\n");

                    if (executionHistory != null
                            && !executionHistory.isBlank()) {
                        userMsg.append(
                                        "PREVIOUS STEPS:\n")
                                .append(executionHistory)
                                .append("\n\n")
                                .append(
                                        "What is the next step?");
                    } else {
                        userMsg.append(
                                "Begin solving this task.");
                    }

                    String response = client
                            .prompt(new Prompt(List.of(
                                    new SystemMessage(
                                            systemPrompt),
                                    new UserMessage(
                                            userMsg.toString()))))
                            .call()
                            .content();

                    log.debug(
                            "Planner response: {} chars",
                            response != null
                                    ? response.length()
                                    : 0);

                    return response;
                })
                .subscribeOn(
                        Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.error(
                            "Planning failed: {}",
                            error.getMessage());
                    return Mono.just(
                            "THOUGHT: Planning failed: "
                                    + error.getMessage()
                                    + "\nFINAL_ANSWER: "
                                    + "I encountered an error. "
                                    + "Please try again.");
                });
    }

    /**
     * Format available tools for the system prompt.
     * Each tool shown as: "- methodName: description"
     *
     * The method name is shown because AI must return
     * the exact method name in ACTION: field.
     * extractAfter() with exact tool dispatch requires this.
     *
     * @param toolDescriptions map of methodName → description
     * @return formatted multi-line string or "No tools available."
     */
    public String formatToolList(
            Map<String, String> toolDescriptions) {

        if (toolDescriptions == null
                || toolDescriptions.isEmpty()) {
            return "No tools available.";
        }

        StringBuilder sb = new StringBuilder();
        toolDescriptions.forEach((name, desc) ->
                sb.append("- ")
                        .append(name)
                        .append(": ")
                        .append(desc)
                        .append("\n"));

        return sb.toString().trim();
    }

    /**
     * Parse the AI's raw response into a structured result.
     *
     * Four cases handled:
     * 1. THOUGHT + ACTION + INPUT → tool call needed
     * 2. THOUGHT + FINAL_ANSWER   → task complete
     * 3. THOUGHT only             → still reasoning
     * 4. Unstructured text        → treat as final answer
     *
     * Uses pre-compiled regex patterns anchored to line
     * starts to avoid truncating content that contains
     * label-like text (e.g. tool input saying "use ACTION:").
     *
     * @param response raw AI response text
     * @return PlanResult with type and extracted fields
     */
    public PlanResult parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return PlanResult.error(
                    "Empty response from AI");
        }

        String trimmed = response.trim();

        // Check FINAL_ANSWER first
        // (AI may include THOUGHT before it)
        String finalAnswer =
                extractWithPattern(trimmed, FINAL_PATTERN);
        if (!finalAnswer.isEmpty()) {
            String thought =
                    extractWithPattern(trimmed, THOUGHT_PATTERN);
            return PlanResult.finalAnswer(
                    thought.isEmpty() ? null : thought,
                    finalAnswer);
        }

        // Check for ACTION (tool call)
        String action =
                extractWithPattern(trimmed, ACTION_PATTERN);
        if (!action.isEmpty()) {
            String thought =
                    extractWithPattern(trimmed, THOUGHT_PATTERN);
            String input =
                    extractWithPattern(trimmed, INPUT_PATTERN);
            return PlanResult.action(
                    thought.isEmpty() ? null : thought,
                    action.trim(),
                    input.trim());
        }

        // THOUGHT only — AI still reasoning
        String thought =
                extractWithPattern(trimmed, THOUGHT_PATTERN);
        if (!thought.isEmpty()) {
            return PlanResult.thought(thought);
        }

        // Unstructured response — treat as final answer
        // Handles models that don't follow the format exactly
        return PlanResult.finalAnswer(
                "Direct response", trimmed);
    }

    /**
     * Extract text matching a pre-compiled pattern.
     *
     * Replaces the old indexOf() approach.
     *
     * OLD extractAfter() problem:
     * text.indexOf("ACTION:") found ANY "ACTION:" in the string.
     * If tool input was "Use the string ACTION: as prefix",
     * the THOUGHT content would be truncated there.
     *
     * NEW extractWithPattern() solution:
     * Pattern uses (?ms)^LABEL — anchored to LINE STARTS.
     * "ACTION:" inside a line (not at start) is ignored.
     * Only label occurrences at column 0 trigger section breaks.
     *
     * @param text    full response text
     * @param pattern pre-compiled label pattern
     * @return extracted section text or empty string
     */
    private String extractWithPattern(
            String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find()
                ? matcher.group(1).trim()
                : "";
    }

    // ── Plan Result Record ────────────────────────

    /**
     * Parsed result from one AI planning response.
     *
     * ACTION: AI wants to call a tool with given input
     * FINAL:  AI has synthesized the complete answer
     * THOUGHT: AI reasoning step (no action yet)
     * ERROR:  Parsing or planning failed
     */
    public record PlanResult(
            PlanType type,
            String thought,
            String toolName,
            String toolInput,
            String finalAnswer,
            String error
    ) {

        public enum PlanType {
            ACTION, FINAL, THOUGHT, ERROR
        }

        /**
         * AI wants to call a tool.
         *
         * @param thought  AI reasoning (may be null)
         * @param toolName exact method name to call
         * @param toolInput input for the tool
         */
        public static PlanResult action(
                String thought,
                String toolName,
                String toolInput) {
            return new PlanResult(
                    PlanType.ACTION,
                    thought,
                    toolName, toolInput,
                    null, null);
        }

        /**
         * AI has the complete answer.
         *
         * @param thought     final reasoning (may be null)
         * @param finalAnswer the answer for the user
         */
        public static PlanResult finalAnswer(
                String thought, String answer) {
            return new PlanResult(
                    PlanType.FINAL,
                    thought,
                    null, null,
                    answer, null);
        }

        /**
         * AI reasoning step — no tool call or final answer yet.
         *
         * @param thought AI reasoning text
         */
        public static PlanResult thought(
                String thought) {
            return new PlanResult(
                    PlanType.THOUGHT,
                    thought,
                    null, null,
                    null, null);
        }

        /**
         * Planning or parsing failed.
         *
         * @param message description of what went wrong
         */
        public static PlanResult error(
                String message) {
            return new PlanResult(
                    PlanType.ERROR,
                    null,
                    null, null,
                    null, message);
        }

        /** True if AI wants to call a tool. */
        public boolean isAction() {
            return type == PlanType.ACTION;
        }

        /** True if AI has the complete answer. */
        public boolean isFinal() {
            return type == PlanType.FINAL;
        }

        /** True if planning/parsing failed. */
        public boolean isError() {
            return type == PlanType.ERROR;
        }
    }
}