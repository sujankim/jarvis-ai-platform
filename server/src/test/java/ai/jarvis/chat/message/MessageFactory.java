package ai.jarvis.chat.message;

import java.util.UUID;

public class MessageFactory {

    public static Message generateUserMessage(UUID sessionId, String content) {
        UUID userMsgId = UUID.randomUUID();
        return Message.userMessage(userMsgId, sessionId, content);
    }

    public static Message generateAssistantMessage(UUID sessionId, String content, String modelName) {
        return Message.assistantMessage(
                UUID.randomUUID(),
                sessionId,
                content,
                modelName,
                null,
                1000,
                1000
        );
    }
}
