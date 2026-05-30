package ai.jarvis.ai.prompt;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WorkingMemoryBuilder {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d yyyy, HH:mm:ss z");

    /**
     * Builds a working memory string injected into
     * every prompt. Contains real-time context:
     * current date/time, user info, session info.
     */
    public String build(
            String username,
            String role,
            String sessionId,
            String modelName){

        String currentTime = ZonedDateTime
                .now(ZoneId.systemDefault())
                .format(FORMATTER);

        return """
                === CURRENT CONTEXT ===
                Date and Time: %s
                User: %s (Role: %s)
                Session ID: %s
                AI Model: %s
                =======================
                """.formatted(
                currentTime,
                username,
                role,
                sessionId,
                modelName
        );
    }
}
