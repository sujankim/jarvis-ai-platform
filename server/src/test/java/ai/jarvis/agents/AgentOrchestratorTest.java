package ai.jarvis.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentOrchestrator Tests")
class AgentOrchestratorTest {

    @Mock
    private AgentExecutor executor;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentStepRepository stepRepository;

    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    private AgentOrchestrator orchestrator;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(
                executor,
                agentRepository,
                stepRepository,
                r2dbcEntityTemplate);

        userId = UUID.randomUUID();
    }

    // ── startAgent() tests ────────────────────────

    @Test
    @DisplayName("startAgent() creates PENDING then RUNNING agent")
    void shouldCreateAndRunAgent() {
        Agent pendingAgent = Agent.create(
                userId, null, "Test goal");
        Agent runningAgent =
                pendingAgent.withRunning();

        // DB insert → returns pendingAgent
        when(r2dbcEntityTemplate
                .insert(any(Agent.class)))
                .thenReturn(Mono.just(pendingAgent));

        // DB update to RUNNING → returns runningAgent
        when(r2dbcEntityTemplate
                .update(any(Agent.class)))
                .thenReturn(Mono.just(runningAgent));

        // Executor returns immediate FINAL event
        when(executor.execute(
                any(Agent.class), any(UUID.class)))
                .thenReturn(Flux.just(
                        AgentEvent.finalAnswer(
                                0, "Done!")));

        // DB update to COMPLETED
        when(stepRepository
                .countByAgentId(any(UUID.class)))
                .thenReturn(Mono.just(1L));

        when(agentRepository.updateStatus(
                any(), anyString(), anyString(),
                anyInt(), anyString(), isNull()))
                .thenReturn(Mono.just(1));

        StepVerifier
                .create(orchestrator.startAgent(
                        "Test goal", userId, null))
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.FINAL
                                && event.data()
                                .equals("Done!"))
                .verifyComplete();
    }

    @Test
    @DisplayName("startAgent() marks agent FAILED on error event")
    void shouldMarkFailedOnErrorEvent() {
        Agent pendingAgent = Agent.create(
                userId, null, "Test goal");
        Agent runningAgent =
                pendingAgent.withRunning();

        when(r2dbcEntityTemplate
                .insert(any(Agent.class)))
                .thenReturn(Mono.just(pendingAgent));
        when(r2dbcEntityTemplate
                .update(any(Agent.class)))
                .thenReturn(Mono.just(runningAgent));

        // Executor returns ERROR event
        when(executor.execute(
                any(Agent.class), any(UUID.class)))
                .thenReturn(Flux.just(
                        AgentEvent.error("AI failed")));

        when(agentRepository.updateStatus(
                any(), anyString(), anyString(),
                anyInt(), isNull(), anyString()))
                .thenReturn(Mono.just(1));

        StepVerifier
                .create(orchestrator.startAgent(
                        "Test goal", userId, null))
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.ERROR)
                .verifyComplete();
    }

    // ── getUserAgents() tests ─────────────────────

    @Test
    @DisplayName("getUserAgents() returns all user agents")
    void shouldReturnUserAgents() {
        Agent agent1 = Agent.create(
                userId, null, "Task 1");
        Agent agent2 = Agent.create(
                userId, null, "Task 2");

        when(agentRepository
                .findByUserIdOrderByCreatedAtDesc(
                        userId))
                .thenReturn(
                        Flux.just(agent1, agent2));

        StepVerifier
                .create(orchestrator
                        .getUserAgents(userId))
                .expectNext(agent1)
                .expectNext(agent2)
                .verifyComplete();
    }

    // ── getAgent() tests ──────────────────────────

    @Test
    @DisplayName("getAgent() returns agent with steps")
    void shouldReturnAgentWithSteps() {
        Agent agent = Agent.create(
                userId, null, "Test goal");

        AgentStep step = AgentStep.createFinal(
                agent.id(), userId, 0, "Done");

        when(agentRepository
                .findByIdAndUserId(
                        agent.id(), userId))
                .thenReturn(Mono.just(agent));

        when(stepRepository
                .findByAgentIdOrderByStepIndexAsc(
                        agent.id()))
                .thenReturn(Flux.just(step));

        StepVerifier
                .create(orchestrator.getAgent(
                        agent.id(), userId))
                .expectNextMatches(aws ->
                        aws.agent().id()
                                .equals(agent.id())
                                && aws.steps().size() == 1)
                .verifyComplete();
    }

    @Test
    @DisplayName("getAgent() returns 404 when not found")
    void shouldReturn404WhenNotFound() {
        UUID agentId = UUID.randomUUID();

        when(agentRepository
                .findByIdAndUserId(
                        agentId, userId))
                .thenReturn(Mono.empty());

        StepVerifier
                .create(orchestrator.getAgent(
                        agentId, userId))
                .expectErrorMatches(error ->
                        error instanceof
                                ResponseStatusException rse
                                && rse.getStatusCode()
                                .value() == 404)
                .verify();
    }

    // ── cancelAgent() tests ───────────────────────

    @Test
    @DisplayName("cancelAgent() cancels RUNNING agent")
    void shouldCancelRunningAgent() {
        Agent running = Agent.create(
                        userId, null, "Test goal")
                .withRunning();

        when(agentRepository
                .findByIdAndUserId(
                        running.id(), userId))
                .thenReturn(Mono.just(running));

        when(agentRepository.updateStatus(
                any(), anyString(), anyString(),
                anyInt(), isNull(), isNull()))
                .thenReturn(Mono.just(1));

        StepVerifier
                .create(orchestrator.cancelAgent(
                        running.id(), userId))
                .verifyComplete();
    }

    @Test
    @DisplayName("cancelAgent() returns 409 for terminal agent")
    void shouldReturn409ForTerminalAgent() {
        Agent completed = Agent.create(
                        userId, null, "Test goal")
                .withRunning()
                .withCompleted("Done", 1, 100);

        when(agentRepository
                .findByIdAndUserId(
                        completed.id(), userId))
                .thenReturn(Mono.just(completed));

        StepVerifier
                .create(orchestrator.cancelAgent(
                        completed.id(), userId))
                .expectErrorMatches(error ->
                        error instanceof
                                ResponseStatusException rse
                                && rse.getStatusCode()
                                .value() == 409)
                .verify();
    }
}