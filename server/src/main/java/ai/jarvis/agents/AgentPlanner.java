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

/**
 * Plans agent execution by asking the AI model
 * what the next step should be.
 *
 * DOES NOT execute tools — only produces the
 * AI's reasoning about what to do next.
 * AgentExecutor handles actual tool execution.
 *
 * STRUCTURED OUTPUT FORMAT:
 * The system prompt instructs the AI to respond
 * in a specific format that parseResponse() can
 * reliably parse. This avoids JSON parsing issues
 * and works well with all Ollama models.
 *
 * THOUGHT/ACTION/INPUT format:
 * THOUGHT: reasoning here
 * ACTION: toolName
 * INPUT: tool input
 *
 * OR when task complete:
 * THOUGHT: reasoning here
 * FINAL_ANSWER: complete answer here
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanner {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * System prompt that instructs the AI to act
     * as a ReACT agent. Injected before every call.
     *
     * {tools} placeholder replaced with actual
     * available tools before each request.
     */
    private static final String AGENT_SYSTEM_PROMPT =
            """
            You are an AI agent that solves tasks step by step.
            
            You have access to these tools:
            {tools}
            
            For each step, respond in EXACTLY this format:
            
            THOUGHT: [your reasoning about what to do next]
            ACTION: [tool_name]
            INPUT: [input to the tool]
            
            When you have enough information to answer:
            
            THOUGHT: [your final reasoning]
            FINAL_ANSWER: [your complete answer to the user]
            
            Rules:
            - Always start with THOUGHT
            - Use tools when you need real-time data
            - Never make up information you don't have
            - Keep tool inputs concise and specific
            - Maximum 10 tool calls per task
            """;

    /**
     * Ask the AI what the next step should be.
     *
     * Sends the full execution history so the AI
     * understands what has already been done and
     * can decide what comes next.
     *
     * Runs on boundedElastic because ChatClient.call()
     * is a blocking HTTP call to Ollama.
     *
     * @param goal             user's original task
     * @param toolList         formatted available tools
     * @param executionHistory previous steps as text
     * @return Mono<String> raw AI response
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
                            && !executionHistory
                            .isBlank()) {
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
     * Each tool shown as: - toolName: description
     *
     * @param toolDescriptions map of name → description
     * @return formatted multi-line string
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
     * Handles four cases:
     * 1. THOUGHT + ACTION + INPUT → tool call needed
     * 2. THOUGHT + FINAL_ANSWER   → task complete
     * 3. THOUGHT only             → thinking step
     * 4. Unstructured text        → treat as final answer
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

        // Check for FINAL_ANSWER first
        if (trimmed.contains("FINAL_ANSWER:")) {
            String answer = extractAfter(
                    trimmed, "FINAL_ANSWER:");
            String thought = extractAfter(
                    trimmed, "THOUGHT:");
            return PlanResult.finalAnswer(
                    thought, answer);
        }

        // Check for ACTION (tool call)
        if (trimmed.contains("ACTION:")) {
            String thought = extractAfter(
                    trimmed, "THOUGHT:");
            String action = extractAfter(
                    trimmed, "ACTION:");
            String input = extractAfter(
                    trimmed, "INPUT:");

            if (action.isBlank()) {
                return PlanResult.error(
                        "AI did not specify a tool");
            }

            return PlanResult.action(
                    thought,
                    action.trim(),
                    input.trim());
        }

        // THOUGHT only — reasoning step
        if (trimmed.contains("THOUGHT:")) {
            String thought = extractAfter(
                    trimmed, "THOUGHT:");
            return PlanResult.thought(thought);
        }

        // Unstructured — treat as final answer
        return PlanResult.finalAnswer(
                "Direct response", trimmed);
    }

    /**
     * Extract text after a label, stopping at
     * the next label in the response.
     *
     * Example:
     * "THOUGHT: foo\nACTION: bar"
     * extractAfter("THOUGHT:") → "foo"
     *
     * @param text  full response text
     * @param label label to extract after
     * @return text content after the label
     */
    private String extractAfter(
            String text, String label) {

        int idx = text.indexOf(label);
        if (idx == -1) return "";

        String after = text.substring(
                idx + label.length()).trim();

        // Stop at next label
        String[] labels = {
                "THOUGHT:", "ACTION:",
                "INPUT:", "FINAL_ANSWER:"
        };

        int endIdx = after.length();
        for (String next : labels) {
            if (next.equals(label)) continue;
            int nextIdx = after.indexOf(next);
            if (nextIdx > 0 && nextIdx < endIdx) {
                endIdx = nextIdx;
            }
        }

        return after.substring(0, endIdx).trim();
    }

    // ── Plan Result ───────────────────────────────

    /**
     * Parsed result from an AI planning response.
     *
     * ACTION: AI wants to call a tool
     * FINAL:  AI has the complete answer
     * THOUGHT: AI is still reasoning
     * ERROR:  Something went wrong
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

        public static PlanResult finalAnswer(
                String thought, String answer) {
            return new PlanResult(
                    PlanType.FINAL,
                    thought,
                    null, null,
                    answer, null);
        }

        public static PlanResult thought(
                String thought) {
            return new PlanResult(
                    PlanType.THOUGHT,
                    thought,
                    null, null,
                    null, null);
        }

        public static PlanResult error(
                String message) {
            return new PlanResult(
                    PlanType.ERROR,
                    null,
                    null, null,
                    null, message);
        }

        public boolean isFinal() {
            return type == PlanType.FINAL;
        }

        public boolean isAction() {
            return type == PlanType.ACTION;
        }

        public boolean isError() {
            return type == PlanType.ERROR;
        }
    }
}