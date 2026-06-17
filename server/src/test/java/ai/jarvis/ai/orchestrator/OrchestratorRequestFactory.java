package ai.jarvis.ai.orchestrator;

import java.util.UUID;

public class OrchestratorRequestFactory {

    public static final String MESSAGE = "Hello";
    public static final String USERNAME = "username";
    public static final String ROLE = "User";

    public static OrchestratorRequest generateOrchestrationRequest() {
        return new OrchestratorRequest(
                UUID.randomUUID(),
                MESSAGE,
                USERNAME,
                ROLE,
                UUID.randomUUID()
                ,UUID.randomUUID()
        );
    }
}
