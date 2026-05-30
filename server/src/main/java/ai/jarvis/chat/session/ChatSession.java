package ai.jarvis.chat.session;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("chat_sessions")
public record ChatSession(

        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        String title,

        String status,

        @Column("provider_id")
        UUID providerId,

        @Column("system_prompt")
        String systemPrompt,

        @Column("message_count")
        int messageCount,

        @Column("total_tokens")
        int totalToken,

        @CreatedDate
        @Column("created_at")
        Instant createdAt,

        @LastModifiedDate
        @Column("updated_at")
        Instant updatedAt,

        @Column("last_message_at")
        Instant lastMessageAt
) {
    //Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_DELETED = "DELETED";

    // Factory method for creating a new session
    public static ChatSession create(UUID id, UUID userId){
        return new ChatSession(
                id,
                userId,
                null,           // title generated later
                STATUS_ACTIVE,
                null,           // provider set later
                null,           // system prompt = use default
                0,              // message count starts at 0
                0,              // total tokens starts at 0
                Instant.now(),
                Instant.now(),
                null            // no messages yet
        );
    }

    // Create a copy with updated title
    public ChatSession withTitle(String newTitle) {
        return new ChatSession(
                id, userId, newTitle, status,
                providerId, systemPrompt,
                messageCount, totalToken,
                createdAt, updatedAt, lastMessageAt
        );
    }

    // Create a copy with incremented message count
    public ChatSession withIncrementedMessageCount(
            int tokenAdded){
        return new ChatSession(
                id, userId, title, status,
                providerId, systemPrompt,
                messageCount + 1,
                totalToken + tokenAdded,
                createdAt, Instant.now(), Instant.now()
        );
    }
}
