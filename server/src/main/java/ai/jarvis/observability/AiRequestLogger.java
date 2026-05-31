package ai.jarvis.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiRequestLogger {

    public void logRequestStart(
            String username,
            String sessionId,
            int historySize) {
        log.info(
                "AI_REQUEST_START user={} session={} history={}",
                username, sessionId, historySize
        );
    }

    public void logRequestComplete(
            String username,
            String sessionId,
            int tokens,
            long durationMs) {
        double tokensPerSecond = durationMs > 0
                ? (tokens * 1000.0 / durationMs)
                : 0;
        log.info(
                "AI_REQUEST_COMPLETE user={} session={} "
                        + "tokens={} duration={}ms tps={:.1f}",
                username, sessionId,
                tokens, durationMs, tokensPerSecond
        );
    }

    public void logRequestError(
            String username,
            String sessionId,
            String error) {
        log.error(
                "AI_REQUEST_ERROR user={} session={} error={}",
                username, sessionId, error
        );
    }
}
