package ai.jarvis.ai.prompt;

import ai.jarvis.chat.message.Message;
import ai.jarvis.chat.message.MessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are Jarvis, an intelligent personal AI assistant.
            You are helpful, professional, and concise.
            Use markdown formatting in your responses.
            Never make up facts. If unsure, say so clearly.
            Reference previous conversation context when relevant.
            """;

    private static final int MAX_HISTORY_MESSAGES = 20;

    /**
     * Assembles the complete prompt from all context sources.
     *
     * Phase 1 assembly order:
     * 1. System prompt (Jarvis personality)
     * 2. Working memory (current date, user, session)
     * 3. Session history (last N messages)
     * 4. Current user message
     */
    public Prompt assemble(
            String userMessage,
            String workingMemory,
            List<Message> history,
            String username) {
        List<org.springframework.ai.chat.messages.Message>
                messages = new ArrayList<>();

        // ── 1. System Prompt ──────────────────────────
        String systemContent = DEFAULT_SYSTEM_PROMPT
                + "\nUser's name: " + username;
        messages.add(new SystemMessage(systemContent));

        // ── 2. Working Memory ─────────────────────────
        // Injected as additional system context
        messages.add(new SystemMessage(workingMemory));

        // ── 3. Session History ────────────────────────
        // Keep last N messages to stay within context window
        List<Message> recentHistory = history.size()
                > MAX_HISTORY_MESSAGES
                ? history.subList(
                        history.size() - MAX_HISTORY_MESSAGES,
                        history.size())
                : history;

        for (Message msg : recentHistory) {
            if(msg.role() == MessageRole.USER){
                messages.add(new SystemMessage(msg.content()));
            } else if(msg.role() == MessageRole.ASSISTANT
                    && msg.error()){
                messages.add(
                        new AssistantMessage(msg.content()));
            }
        }

        // ── 4. Current User Message ───────────────────
        messages.add(new UserMessage(userMessage));

        log.debug(
                "Prompt assembled: {} messages "
                        + "(history={} + current=1)",
                messages.size(),
                recentHistory.size()
        );

        return new Prompt(messages);
    }
}
