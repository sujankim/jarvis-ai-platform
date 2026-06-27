package ai.jarvis.agents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Agent Entity Tests")
class AgentTest {

    // ── Agent entity tests ────────────────────────

    @Test
    @DisplayName("create() sets PENDING status with defaults")
    void shouldCreateWithPendingStatus() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Agent agent = Agent.create(
                userId,
                sessionId,
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
        assertThat(agent.updatedAt()).isNotNull();
        assertThat(agent.completedAt()).isNull();
    }

    @Test
    @DisplayName("create() allows null sessionId")
    void shouldAllowNullSessionId() {
        Agent agent = Agent.create(
                UUID.randomUUID(),
                null,       // ← no session
                "Test goal");

        assertThat(agent.sessionId()).isNull();
        assertThat(agent.status())
                .isEqualTo(AgentStatus.PENDING);
    }

    @Test
    @DisplayName("withRunning() transitions to RUNNING")
    void shouldTransitionToRunning() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test goal");

        Agent running = agent.withRunning();

        assertThat(running.status())
                .isEqualTo(AgentStatus.RUNNING);
        // Original unchanged — immutable record
        assertThat(agent.status())
                .isEqualTo(AgentStatus.PENDING);
    }

    @Test
    @DisplayName("withCompleted() sets answer + COMPLETED")
    void shouldTransitionToCompleted() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test goal");

        Agent completed = agent.withCompleted(
                "Final answer here",
                5,      // totalSteps
                3000);  // durationMs

        assertThat(completed.status())
                .isEqualTo(AgentStatus.COMPLETED);
        assertThat(completed.finalAnswer())
                .isEqualTo("Final answer here");
        assertThat(completed.stepCount()).isEqualTo(5);
        assertThat(completed.durationMs())
                .isEqualTo(3000);
        assertThat(completed.completedAt())
                .isNotNull();
        assertThat(completed.errorMessage()).isNull();
    }

    @Test
    @DisplayName("withFailed() sets errorMessage + FAILED")
    void shouldTransitionToFailed() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test goal");

        Agent failed = agent.withFailed(
                "Tool call timed out");

        assertThat(failed.status())
                .isEqualTo(AgentStatus.FAILED);
        assertThat(failed.errorMessage())
                .isEqualTo("Tool call timed out");
        assertThat(failed.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("withCancelled() transitions to CANCELLED")
    void shouldTransitionToCancelled() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test goal");

        Agent cancelled = agent.withCancelled();

        assertThat(cancelled.status())
                .isEqualTo(AgentStatus.CANCELLED);
        assertThat(cancelled.completedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("withIncrementedStepCount() increments correctly")
    void shouldIncrementStepCount() {
        Agent agent = Agent.create(
                UUID.randomUUID(), null, "Test");

        assertThat(agent.stepCount()).isEqualTo(0);

        Agent step1 = agent.withIncrementedStepCount();
        assertThat(step1.stepCount()).isEqualTo(1);

        Agent step2 = step1.withIncrementedStepCount();
        assertThat(step2.stepCount()).isEqualTo(2);

        // Originals unchanged — immutable
        assertThat(agent.stepCount()).isEqualTo(0);
        assertThat(step1.stepCount()).isEqualTo(1);
    }

    // ── AgentStep entity tests ────────────────────

    @Test
    @DisplayName("createThink() creates THINK step with PENDING status")
    void shouldCreateThinkStep() {
        UUID agentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AgentStep step = AgentStep.createThink(
                agentId, userId,
                0,
                "I need to search for weather data");

        assertThat(step.id()).isNotNull();
        assertThat(step.agentId()).isEqualTo(agentId);
        assertThat(step.userId()).isEqualTo(userId);
        assertThat(step.stepIndex()).isEqualTo(0);
        assertThat(step.stepType())
                .isEqualTo(AgentStepType.THINK);
        assertThat(step.toolName()).isNull();
        assertThat(step.input())
                .isEqualTo(
                        "I need to search for weather data");
        assertThat(step.output()).isNull();
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.PENDING);
        assertThat(step.createdAt()).isNotNull();
        assertThat(step.completedAt()).isNull();
    }

    @Test
    @DisplayName("createAct() creates ACT step with tool name")
    void shouldCreateActStep() {
        AgentStep step = AgentStep.createAct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "getWeather",
                "London");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.ACT);
        assertThat(step.toolName())
                .isEqualTo("getWeather");
        assertThat(step.input()).isEqualTo("London");
        assertThat(step.output()).isNull();
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.PENDING);
    }

    @Test
    @DisplayName("createObserve() creates OBSERVE step already DONE")
    void shouldCreateObserveStep() {
        AgentStep step = AgentStep.createObserve(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "getWeather",
                "22°C, Sunny, Humidity: 45%");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.OBSERVE);
        assertThat(step.toolName())
                .isEqualTo("getWeather");
        assertThat(step.output())
                .isEqualTo("22°C, Sunny, Humidity: 45%");
        // OBSERVE is immediately DONE
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(step.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("createFinal() creates FINAL step already DONE")
    void shouldCreateFinalStep() {
        AgentStep step = AgentStep.createFinal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                "The weather in London is 22°C.");

        assertThat(step.stepType())
                .isEqualTo(AgentStepType.FINAL);
        assertThat(step.output())
                .isEqualTo(
                        "The weather in London is 22°C.");
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(step.completedAt()).isNotNull();
        assertThat(step.toolName()).isNull();
        assertThat(step.input()).isNull();
    }

    @Test
    @DisplayName("withOutput() marks step DONE with result")
    void shouldMarkStepDoneWithOutput() {
        AgentStep step = AgentStep.createAct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1, "getWeather", "London");

        AgentStep done = step.withOutput(
                "22°C, Sunny", 250);

        assertThat(done.output())
                .isEqualTo("22°C, Sunny");
        assertThat(done.status())
                .isEqualTo(AgentStepStatus.DONE);
        assertThat(done.durationMs()).isEqualTo(250);
        assertThat(done.completedAt()).isNotNull();
        // Original unchanged
        assertThat(step.status())
                .isEqualTo(AgentStepStatus.PENDING);
    }

    @Test
    @DisplayName("withFailed() marks step FAILED with error")
    void shouldMarkStepFailed() {
        AgentStep step = AgentStep.createAct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1, "getWeather", "London");

        AgentStep failed = step.withFailed(
                "Connection timeout");

        assertThat(failed.status())
                .isEqualTo(AgentStepStatus.FAILED);
        assertThat(failed.output())
                .isEqualTo("Connection timeout");
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