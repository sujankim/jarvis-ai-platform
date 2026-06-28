package ai.jarvis.agents;

import ai.jarvis.agents.AgentPlanner.PlanResult;
import ai.jarvis.tools.ToolRegistry;
import ai.jarvis.tools.builtin.CalculatorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentExecutor Tests")
class AgentExecutorTest {

    @Mock
    private AgentPlanner planner;

    @Mock
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    private AgentExecutor executor;
    private Agent testAgent;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // Real CalculatorTool — no mock needed
        // Tests actual tool discovery + execution
        CalculatorTool calcTool = new CalculatorTool();
        ToolRegistry registry = new ToolRegistry(
                List.of(calcTool));

        executor = new AgentExecutor(
                planner, registry,
                r2dbcEntityTemplate);

        userId = UUID.randomUUID();

        // Agent must be RUNNING for execution
        // create() → PENDING, withRunning() → RUNNING
        testAgent = Agent.create(
                        userId, null, "What is 25 * 4?")
                .withRunning();
    }

    // ── AgentEvent factory tests ──────────────────
    // These test the event record directly
    // No mocking needed — pure unit tests

    @Test
    @DisplayName("AgentEvent.think() creates THINK event with correct fields")
    void shouldCreateThinkEvent() {
        AgentEvent event =
                AgentEvent.think(0, "I need to calculate");

        assertThat(event.type())
                .isEqualTo(AgentEvent.EventType.THINK);
        assertThat(event.data())
                .isEqualTo("I need to calculate");
        assertThat(event.stepIndex()).isEqualTo(0);
        // THINK events have no toolName
        assertThat(event.toolName()).isNull();
    }

    @Test
    @DisplayName("AgentEvent.act() creates ACT event with tool info")
    void shouldCreateActEvent() {
        AgentEvent event =
                AgentEvent.act(1, "calculate", "25 * 4");

        assertThat(event.type())
                .isEqualTo(AgentEvent.EventType.ACT);
        assertThat(event.toolName())
                .isEqualTo("calculate");
        assertThat(event.data())
                .isEqualTo("25 * 4");
        assertThat(event.stepIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("AgentEvent.observe() creates OBSERVE event with result")
    void shouldCreateObserveEvent() {
        AgentEvent event =
                AgentEvent.observe(
                        2, "calculate", "100");

        assertThat(event.type())
                .isEqualTo(AgentEvent.EventType.OBSERVE);
        assertThat(event.data()).isEqualTo("100");
        assertThat(event.toolName())
                .isEqualTo("calculate");
        assertThat(event.stepIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("AgentEvent.finalAnswer() creates FINAL event")
    void shouldCreateFinalEvent() {
        AgentEvent event =
                AgentEvent.finalAnswer(
                        3, "The answer is 100");

        assertThat(event.type())
                .isEqualTo(AgentEvent.EventType.FINAL);
        assertThat(event.data())
                .isEqualTo("The answer is 100");
        assertThat(event.stepIndex()).isEqualTo(3);
        // FINAL events have no toolName
        assertThat(event.toolName()).isNull();
    }

    @Test
    @DisplayName("AgentEvent.error() creates ERROR event with stepIndex -1")
    void shouldCreateErrorEvent() {
        AgentEvent event =
                AgentEvent.error("something broke");

        assertThat(event.type())
                .isEqualTo(AgentEvent.EventType.ERROR);
        assertThat(event.data())
                .isEqualTo("something broke");
        // stepIndex -1 indicates non-step error
        assertThat(event.stepIndex()).isEqualTo(-1);
        assertThat(event.toolName()).isNull();
    }

    // ── execute() tests ───────────────────────────
    // These test the full ReACT loop with mocked planner
    // r2dbcEntityTemplate mocked to avoid DB dependency

    @Test
    @DisplayName("execute() emits FINAL event when AI gives direct answer")
    void shouldEmitFinalEventOnDirectAnswer() {
        // Planner returns FINAL immediately — no tool call
        when(planner.formatToolList(any()))
                .thenReturn("calculate: math");

        when(planner.planNextStep(
                anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(
                        "FINAL_ANSWER: The answer is 100"));

        // parseResponse called with the raw AI text
        when(planner.parseResponse(
                "FINAL_ANSWER: The answer is 100"))
                .thenReturn(PlanResult.finalAnswer(
                        null,
                        "The answer is 100"));

        // Mock DB save — return the step as-is
        when(r2dbcEntityTemplate.insert(
                any(AgentStep.class)))
                .thenAnswer(inv ->
                        Mono.just(inv.getArgument(0)));

        StepVerifier
                .create(executor.execute(
                        testAgent, userId))
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.FINAL
                                && event.data().equals(
                                "The answer is 100"))
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() emits THINK then FINAL when AI thinks first")
    void shouldEmitThinkThenFinal() {
        // AI returns THOUGHT + FINAL_ANSWER in one response
        // This is the scenario that crashed Flux.generate()
        // because it required two sink.next() calls:
        // #1: THINK event, #2: FINAL event
        // Flux.create() handles this correctly
        String aiResponse =
                "THOUGHT: I have enough info\n"
                        + "FINAL_ANSWER: The answer is 100";

        when(planner.formatToolList(any()))
                .thenReturn("calculate: math");

        when(planner.planNextStep(
                anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(aiResponse));

        // Parse returns FINAL with thought populated
        when(planner.parseResponse(aiResponse))
                .thenReturn(PlanResult.finalAnswer(
                        "I have enough info",
                        "The answer is 100"));

        when(r2dbcEntityTemplate.insert(
                any(AgentStep.class)))
                .thenAnswer(inv ->
                        Mono.just(inv.getArgument(0)));

        StepVerifier
                .create(executor.execute(
                        testAgent, userId))
                // THINK emitted first (thought not null)
                // This was the FIRST sink.next() call
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.THINK
                                && event.data().equals(
                                "I have enough info"))
                // FINAL emitted second
                // This was the SECOND sink.next() call
                // Only works with Flux.create()
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.FINAL
                                && event.data().equals(
                                "The answer is 100"))
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() emits ACT and OBSERVE when AI calls a tool")
    void shouldEmitActAndObserve() {
        // First call: AI wants to call calculator
        String firstResponse =
                "THOUGHT: Need to calculate\n"
                        + "ACTION: calculate\n"
                        + "INPUT: 25 * 4";

        // Second call: AI gives final answer
        String secondResponse =
                "FINAL_ANSWER: 25 * 4 = 100";

        when(planner.formatToolList(any()))
                .thenReturn("calculate: math");

        when(planner.planNextStep(
                anyString(), anyString(), anyString()))
                .thenReturn(
                        Mono.just(firstResponse),
                        Mono.just(secondResponse));

        when(planner.parseResponse(firstResponse))
                .thenReturn(PlanResult.action(
                        "Need to calculate",
                        "calculate",
                        "25 * 4"));

        when(planner.parseResponse(secondResponse))
                .thenReturn(PlanResult.finalAnswer(
                        null, "25 * 4 = 100"));

        when(r2dbcEntityTemplate.insert(
                any(AgentStep.class)))
                .thenAnswer(inv ->
                        Mono.just(inv.getArgument(0)));

        StepVerifier
                .create(executor.execute(
                        testAgent, userId))
                // THINK from first response
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.THINK)
                // ACT — tool being called
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.ACT
                                && event.toolName()
                                .equals("calculate"))
                // OBSERVE — tool result
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.OBSERVE)
                // FINAL — second AI call
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.FINAL)
                .verifyComplete();
    }

    @Test
    @DisplayName("execute() emits ERROR when planner throws")
    void shouldEmitErrorOnPlannerFailure() {
        when(planner.formatToolList(any()))
                .thenReturn("calculate: math");

        // Planner throws — agent should gracefully emit ERROR
        when(planner.planNextStep(
                anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(
                        new RuntimeException(
                                "AI unavailable")));

        StepVerifier
                .create(executor.execute(
                        testAgent, userId))
                .expectNextMatches(event ->
                        event.type() ==
                                AgentEvent.EventType.ERROR
                                && event.data()
                                .contains("AI unavailable"))
                .verifyComplete();
    }
}