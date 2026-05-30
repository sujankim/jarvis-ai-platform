package ai.jarvis.chat.message;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("messages")
public record Message(

        @Id
        UUID id,

        @Column("session_id")
        UUID sessionId,

        MessageRole role,

        String content,

        @Column("provider_id")
        UUID providerId,

        @Column("model_name")
        String modelName,

        @Column("prompt_tokens")
        Integer promptTokens,

        @Column("completion_tokens")
        Integer completionTokens,

        @Column("total_tokens")
        Integer totalTokens,

        @Column("duration_ms")
        Integer durationMs,

        @Column("finish_reason")
        String finishReason,

        @Column("is_error")
        boolean error,

        @Column("error_message")
        String errorMessage,

        @Column("created_at")
        Instant createdAt

) {
    // Factory: create a USER message
    public static Message userMessage(
            UUID id,
            UUID sessionId,
            String content) {
        return new Message(
                id, sessionId, MessageRole.USER, content,
                null, null, null,
                null, null,
                null, null, false, null, Instant.now()
        );
    }

    // Factory: create an ASSISTANT message (after AI responds)
    public static Message assistantMessage(
            UUID id,
            UUID sessionId,
            String content,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer durationMs) {
        int total = (promptTokens != null ? promptTokens : 0)
                + (completionTokens != null ? completionTokens : 0);
        return new Message(
                id, sessionId, MessageRole.ASSISTANT, content,
                null, modelName,
                promptTokens, completionTokens, total,
                durationMs, "STOP",
                false, null, Instant.now()
        );
    }

    // Factory: create an error message
    public static Message errorMessage(
            UUID id,
            UUID sessionId,
            String errorText) {
        return new Message(
                id, sessionId, MessageRole.ASSISTANT,
                "I encountered an error. Please try again.",
                null, null, null, null, null,
                null, "ERROR",
                true, errorText, Instant.now()
        );
    }
}