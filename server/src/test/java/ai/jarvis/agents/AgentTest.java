package ai.jarvis.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Agent Entity Tests")
class AgentTest {

    // ── Agent create() tests ──────────────────────

    @Test
    @DisplayName("create() sets PENDING status with defaults")
    void shouldCreateWithPendingStatus() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Agent agent = Agent.create(
                userId, sessionId,
                "Research Spring Boot 4");

        assertThat(agent.id()).isNotNull();
        assertThat(agent.userId()).isEqualTo(userId);
        assertThat(agent.sessionId())
                .isEqualTo(sessionId);
        assertThat(agent.goal())
                .isEqualTo("Research Spring Boot 4");
        assertThat(agent.status())
                .isEqualTo(AgentStatus.PENDING);
        assertThat(agent.stepCount()).isEqualTo(0);
        assertThat(agent.finalAnswer()).isNull();
        assertThat(agent.errorMessage()).isNull();
        assertThat(agent.durationMs()).isNull();
        assertThat(agent.createdAt()).isNotNull();
        assertThat(agent.completedAt()).isNull();
    }

    @Test
    @DisplayName("create() allows null sessionId")
    void shouldAllowNullSessionId() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test goal");

        assertThat(agent.sessionId()).isNull();
        assertThat(agent.status())
                .isEqualTo(AgentStatus.PENDING);
    }

    // ── Agent state transition tests ──────────────

    @Test
    @DisplayName("withRunning() transitions PENDING → RUNNING")
    void shouldTransitionToRunning() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test");

        Agent running = agent.withRunning();

        assertThat(running.status())
                .isEqualTo(AgentStatus.RUNNING);
        // Immutable — original unchanged
        assertThat(agent.status())
                .isEqualTo(AgentStatus.PENDING);
    }

    @Test
    @DisplayName("withRunning() throws if not PENDING")
    void shouldThrowWhenRunningFromNonPending() {
        Agent running = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        assertThatThrownBy(running::withRunning)
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("RUNNING")
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("withCompleted() transitions RUNNING → COMPLETED")
    void shouldTransitionToCompleted() {
        Agent agent = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent completed = agent.withCompleted(
                "Final answer", 5, 3000);

        assertThat(completed.status())
                .isEqualTo(AgentStatus.COMPLETED);
        assertThat(completed.finalAnswer())
                .isEqualTo("Final answer");
        assertThat(completed.stepCount()).isEqualTo(5);
        assertThat(completed.durationMs())
                .isEqualTo(3000);
        assertThat(completed.completedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("withCompleted() throws if not RUNNING")
    void shouldThrowWhenCompletingFromPending() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test");

        assertThatThrownBy(() ->
                agent.withCompleted("answer", 1, 100))
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("RUNNING");
    }

    @Test
    @DisplayName("withFailed() transitions RUNNING → FAILED")
    void shouldTransitionToFailed() {
        Agent agent = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent failed = agent.withFailed(
                "Tool timed out");

        assertThat(failed.status())
                .isEqualTo(AgentStatus.FAILED);
        assertThat(failed.errorMessage())
                .isEqualTo("Tool timed out");
        assertThat(failed.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("withFailed() sets finalAnswer to null — satisfies DB constraint")
    void shouldSetFinalAnswerNullOnFailed() {
        Agent agent = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent failed = agent.withFailed("Some error");

        assertThat(failed.finalAnswer()).isNull();
        assertThat(failed.status())
                .isEqualTo(AgentStatus.FAILED);
    }

    @Test
    @DisplayName("withFailed() throws if not RUNNING")
    void shouldThrowWhenFailingFromPending() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test");

        assertThatThrownBy(() ->
                agent.withFailed("error"))
                .isInstanceOf(
                        IllegalStateException.class);
    }

    @Test
    @DisplayName("withCancelled() cancels non-terminal agent")
    void shouldCancelNonTerminalAgent() {
        Agent running = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent cancelled = running.withCancelled();

        assertThat(cancelled.status())
                .isEqualTo(AgentStatus.CANCELLED);
        assertThat(cancelled.completedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("withCancelled() sets finalAnswer to null — satisfies DB constraint")
    void shouldSetFinalAnswerNullOnCancelled() {
        // DB constraint: final_answer IS NULL OR status = 'COMPLETED'
        // withCancelled() must always produce null finalAnswer.
        Agent running = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent cancelled = running.withCancelled();

        assertThat(cancelled.finalAnswer()).isNull();
        assertThat(cancelled.errorMessage()).isNull();
    }

    @Test
    @DisplayName("withCancelled() sets errorMessage to null — satisfies DB constraint")
    void shouldSetErrorMessageNullOnCancelled() {
        // DB constraint: error_message IS NULL OR status = 'FAILED'
        // withCancelled() must always produce null errorMessage.
        Agent running = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent cancelled = running.withCancelled();

        assertThat(cancelled.errorMessage()).isNull();
    }

    @Test
    @DisplayName("withCancelled() throws if already terminal")
    void shouldThrowWhenCancellingTerminal() {
        Agent completed = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning()
                .withCompleted("done", 1, 100);

        assertThatThrownBy(completed::withCancelled)
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    @DisplayName("withIncrementedStepCount() increments correctly")
    void shouldIncrementStepCount() {
        Agent agent = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning();

        Agent step1 = agent.withIncrementedStepCount();
        assertThat(step1.stepCount()).isEqualTo(1);

        Agent step2 = step1.withIncrementedStepCount();
        assertThat(step2.stepCount()).isEqualTo(2);

        // Originals unchanged — immutable record
        assertThat(agent.stepCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("withIncrementedStepCount() throws for terminal agent")
    void shouldThrowWhenIncrementingTerminal() {
        Agent completed = Agent.create(
                        UUID.randomUUID(), null, "Test")
                .withRunning()
                .withCompleted("done", 1, 100);

        assertThatThrownBy(
                completed::withIncrementedStepCount)
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    // ── AgentStep factory tests ───────────────────

    @Test
    @DisplayName("createThink() creates THINK step PENDING")
    void shouldCreateThinkStep() {
        AgentStep step = AgentStep.createThink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0, "I need weather data");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.THINK);
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.PENDING);
        assertThat(step.toolName()).isNull();
        assertThat(step.completedAt()).isNull();
    }

    @Test
    @DisplayName("createObserve() creates OBSERVE step already DONE")
    void shouldCreateObserveDone() {
        AgentStep step = AgentStep.createObserve(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2, "WeatherTool", "22°C Sunny");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.OBSERVE);
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(step.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("createFinal() creates FINAL step already DONE")
    void shouldCreateFinalDone() {
        AgentStep step = AgentStep.createFinal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3, "London is 22°C.");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.FINAL);
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(step.completedAt()).isNotNull();
    }

    // ── AgentStep transition guard tests ─────────

    @Test
    @DisplayName("withRunning() valid for THINK PENDING")
    void shouldMarkThinkStepRunning() {
        AgentStep step = AgentStep.createThink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0, "reasoning");

        AgentStep running = step.withRunning();

        assertThat(running.status())
                .isEqualTo(AgentStepStatus.RUNNING);
    }

    @Test
    @DisplayName("withRunning() throws for OBSERVE step")
    void shouldThrowWhenRunningObserve() {
        AgentStep observe = AgentStep.createObserve(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2, "tool", "result");

        assertThatThrownBy(observe::withRunning)
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    @DisplayName("withOutput() valid from RUNNING")
    void shouldProduceOutputFromRunning() {
        AgentStep running = AgentStep.createThink(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        0, "reasoning")
                .withRunning();

        AgentStep done =
                running.withOutput("result", 100);

        assertThat(done.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(done.output()).isEqualTo("result");
    }

    @Test
    @DisplayName("withOutput() throws if not RUNNING")
    void shouldThrowOutputFromPending() {
        AgentStep pending = AgentStep.createThink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0, "reasoning");

        assertThatThrownBy(() ->
                pending.withOutput("result", 100))
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    @DisplayName("withFailed() throws if not RUNNING")
    void shouldThrowFailedFromPending() {
        AgentStep pending = AgentStep.createAct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1, "tool", "input");

        assertThatThrownBy(() ->
                pending.withFailed("error"))
                .isInstanceOf(
                        IllegalStateException.class)
                .hasMessageContaining("RUNNING");
    }

    // ── Enum tests ────────────────────────────────

    @Test
    @DisplayName("AgentStatus has all 5 values")
    void shouldHaveAllStatusValues() {
        assertThat(AgentStatus.values())
                .containsExactly(
                        AgentStatus.PENDING,
                        AgentStatus.RUNNING,
                        AgentStatus.COMPLETED,
                        AgentStatus.FAILED,
                        AgentStatus.CANCELLED);
    }

    @Test
    @DisplayName("AgentStepType has all 4 values")
    void shouldHaveAllStepTypeValues() {
        assertThat(AgentStepType.values())
                .containsExactly(
                        AgentStepType.THINK,
                        AgentStepType.ACT,
                        AgentStepType.OBSERVE,
                        AgentStepType.FINAL);
    }

    @Test
    @DisplayName("AgentStepStatus has all 4 values")
    void shouldHaveAllStepStatusValues() {
        assertThat(AgentStepStatus.values())
                .containsExactly(
                        AgentStepStatus.PENDING,
                        AgentStepStatus.RUNNING,
                        AgentStepStatus.DONE,
                        AgentStepStatus.FAILED);
    }
}