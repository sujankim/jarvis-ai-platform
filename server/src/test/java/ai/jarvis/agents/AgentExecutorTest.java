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
                CalculatorTool calcTool = new CalculatorTool();
                ToolRegistry registry = new ToolRegistry(List.of(calcTool));

                executor = new AgentExecutor(planner, registry, r2dbcEntityTemplate);
                userId = UUID.randomUUID();
                testAgent = Agent.create(userId, null, "What is 25 * 4?").withRunning();
        }

        // ── EXISTING AGENT EVENT FACTORY TESTS ───────────────

        @Test
        @DisplayName("AgentEvent.think() creates THINK event with correct fields")
        void shouldCreateThinkEvent() {
                AgentEvent event = AgentEvent.think(0, "I need to calculate");

                assertThat(event.type()).isEqualTo(AgentEvent.EventType.THINK);
                assertThat(event.data()).isEqualTo("I need to calculate");
                assertThat(event.stepIndex()).isEqualTo(0);
                assertThat(event.toolName()).isNull();
        }

        @Test
        @DisplayName("AgentEvent.act() creates ACT event with tool info")
        void shouldCreateActEvent() {
                AgentEvent event = AgentEvent.act(1, "calculate", "25 * 4");

                assertThat(event.type()).isEqualTo(AgentEvent.EventType.ACT);
                assertThat(event.toolName()).isEqualTo("calculate");
                assertThat(event.data()).isEqualTo("25 * 4");
                assertThat(event.stepIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("AgentEvent.observe() creates OBSERVE event with result")
        void shouldCreateObserveEvent() {
                AgentEvent event = AgentEvent.observe(2, "calculate", "100");

                assertThat(event.type()).isEqualTo(AgentEvent.EventType.OBSERVE);
                assertThat(event.data()).isEqualTo("100");
                assertThat(event.toolName()).isEqualTo("calculate");
                assertThat(event.stepIndex()).isEqualTo(2);
        }

        @Test
        @DisplayName("AgentEvent.finalAnswer() creates FINAL event")
        void shouldCreateFinalEvent() {
                AgentEvent event = AgentEvent.finalAnswer(3, "The answer is 100");

                assertThat(event.type()).isEqualTo(AgentEvent.EventType.FINAL);
                assertThat(event.data()).isEqualTo("The answer is 100");
                assertThat(event.stepIndex()).isEqualTo(3);
                assertThat(event.toolName()).isNull();
        }

        @Test
        @DisplayName("AgentEvent.error() creates ERROR event with stepIndex -1")
        void shouldCreateErrorEvent() {
                AgentEvent event = AgentEvent.error("something broke");

                assertThat(event.type()).isEqualTo(AgentEvent.EventType.ERROR);
                assertThat(event.data()).isEqualTo("something broke");
                assertThat(event.stepIndex()).isEqualTo(-1);
                assertThat(event.toolName()).isNull();
        }

        // ── EXISTING EXECUTE() TESTS ─────────────────────────

        @Test
        @DisplayName("execute() emits FINAL event when AI gives direct answer")
        void shouldEmitFinalEventOnDirectAnswer() {
                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just("FINAL_ANSWER: The answer is 100"));
                when(planner.parseResponse("FINAL_ANSWER: The answer is 100"))
                                .thenReturn(PlanResult.finalAnswer(null, "The answer is 100"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL &&
                                                event.data().equals("The answer is 100"))
                                .verifyComplete();
        }

        @Test
        @DisplayName("execute() emits THINK then FINAL when AI thinks first")
        void shouldEmitThinkThenFinal() {
                String aiResponse = "THOUGHT: I have enough info\nFINAL_ANSWER: The answer is 100";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(aiResponse));
                when(planner.parseResponse(aiResponse))
                                .thenReturn(PlanResult.finalAnswer("I have enough info", "The answer is 100"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.THINK &&
                                                event.data().equals("I have enough info"))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL &&
                                                event.data().equals("The answer is 100"))
                                .verifyComplete();
        }

        @Test
        @DisplayName("execute() emits ACT and OBSERVE when AI calls a tool")
        void shouldEmitActAndObserve() {
                String firstResponse = "THOUGHT: Need to calculate\nACTION: calculate\nINPUT: 25 * 4";
                String secondResponse = "FINAL_ANSWER: 25 * 4 = 100";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(firstResponse), Mono.just(secondResponse));
                when(planner.parseResponse(firstResponse))
                                .thenReturn(PlanResult.action("Need to calculate", "calculate", "25 * 4"));
                when(planner.parseResponse(secondResponse))
                                .thenReturn(PlanResult.finalAnswer(null, "25 * 4 = 100"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.THINK)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ACT &&
                                                event.toolName().equals("calculate"))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.OBSERVE)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL)
                                .verifyComplete();
        }

        @Test
        @DisplayName("execute() emits ERROR when planner throws")
        void shouldEmitErrorOnPlannerFailure() {
                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.error(new RuntimeException("AI unavailable")));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ERROR &&
                                                event.data().contains("AI unavailable"))
                                .verifyComplete();
        }

        // ── NEW TESTS FOR EXECUTION EDGE CASES ───────────────

        @Test
        @DisplayName("execute() continues when tool returns error string")
        void shouldContinueWhenToolReturnsError() {
                String firstResponse = "THOUGHT: Need to calculate\nACTION: calculate\nINPUT: invalid!";
                String secondResponse = "FINAL_ANSWER: The tool failed, please try again";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(firstResponse), Mono.just(secondResponse));
                when(planner.parseResponse(firstResponse))
                                .thenReturn(PlanResult.action("Need to calculate", "calculate", "invalid!"));
                when(planner.parseResponse(secondResponse))
                                .thenReturn(PlanResult.finalAnswer(null, "The tool failed, please try again"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.THINK)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ACT)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.OBSERVE &&
                                                (event.data().contains("error") || event.data().contains("Error")))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL)
                                .verifyComplete();
        }

        @Test
        @DisplayName("execute() emits ERROR and completes when MAX_STEPS reached")
        void shouldEmitErrorOnMaxSteps() {
                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                String actionResponse = "THOUGHT: Step\nACTION: calculate\nINPUT: 25 * 4";

                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(actionResponse));
                when(planner.parseResponse(actionResponse))
                                .thenReturn(PlanResult.action("Step", "calculate", "25 * 4"));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .thenConsumeWhile(event -> event.type() != AgentEvent.EventType.ERROR)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ERROR &&
                                                event.data().contains("Maximum steps"))
                                .verifyComplete();
        }

        @Test
        @DisplayName("executeTool() returns helpful error when tool not found")
        void shouldReturnHelpfulErrorWhenToolNotFound() {
                String firstResponse = "THOUGHT: Need a tool\nACTION: nonExistentTool\nINPUT: test";
                String secondResponse = "FINAL_ANSWER: The tool nonExistentTool was not found. Available tools: calculate";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(firstResponse), Mono.just(secondResponse));
                when(planner.parseResponse(firstResponse))
                                .thenReturn(PlanResult.action("Need a tool", "nonExistentTool", "test"));
                when(planner.parseResponse(secondResponse))
                                .thenReturn(PlanResult.finalAnswer(null,
                                                "The tool nonExistentTool was not found. Available tools: calculate"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.THINK)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ACT)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.OBSERVE &&
                                                event.data().contains("not found") &&
                                                (event.data().contains("calculate")
                                                                || event.data().contains("available")))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL)
                                .verifyComplete();
        }

        @Test
        @DisplayName("executeTool() error message includes available tools")
        void shouldIncludeAvailableToolsInErrorMessage() {
                String firstResponse = "THOUGHT: Find a tool\nACTION: missingTool\nINPUT: data";
                String secondResponse = "FINAL_ANSWER: Tool missingTool not found. Available: calculate";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(firstResponse), Mono.just(secondResponse));
                when(planner.parseResponse(firstResponse))
                                .thenReturn(PlanResult.action("Find a tool", "missingTool", "data"));
                when(planner.parseResponse(secondResponse))
                                .thenReturn(PlanResult.finalAnswer(null,
                                                "Tool missingTool not found. Available: calculate"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.THINK)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.ACT)
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.OBSERVE &&
                                                event.data().contains("Tool") &&
                                                event.data().contains("missingTool") &&
                                                event.data().contains("Available tools"))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL)
                                .verifyComplete();
        }

        @Test
        @DisplayName("execute() handles step persistence failure gracefully")
        void shouldHandleStepPersistenceFailure() {
                String response = "FINAL_ANSWER: The answer is 100";

                when(planner.formatToolList(any())).thenReturn("calculate: math");
                when(planner.planNextStep(anyString(), anyString(), anyString()))
                                .thenReturn(Mono.just(response));
                when(planner.parseResponse(response))
                                .thenReturn(PlanResult.finalAnswer(null, "The answer is 100"));
                when(r2dbcEntityTemplate.insert(any(AgentStep.class)))
                                .thenReturn(Mono.error(new RuntimeException("DB connection failed")));

                StepVerifier.create(executor.execute(testAgent, userId))
                                .expectNextMatches(event -> event.type() == AgentEvent.EventType.FINAL)
                                .verifyComplete();
        }
}