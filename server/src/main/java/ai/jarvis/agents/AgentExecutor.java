package ai.jarvis.agents;

import ai.jarvis.tools.JarvisTool;
import ai.jarvis.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Executes the full ReACT agent loop.
 *
 * RESPONSIBILITIES:
 * 1. Build tool list from ToolRegistry
 * 2. Call AgentPlanner for each step
 * 3. Execute tools via reflection on @Tool methods
 * 4. Persist each step to agent_steps table
 * 5. Emit AgentEvent for each step (SSE streaming)
 * 6. Enforce MAX_STEPS + TIMEOUT safety limits
 *
 * WHY Flux.create() NOT Flux.generate():
 * Flux.generate() allows only ONE sink.next() per invocation.
 * When AI returns THOUGHT + FINAL in one response, two events
 * are needed → generate() crashes with "More than one call to onNext".
 * Flux.create() allows unlimited sink.next() calls per iteration.
 *
 *
 * 1. sink.isCancelled() check: honors SSE disconnect.
 *    Without this, abandoned clients still consume boundedElastic
 *    threads for the full 120s timeout.
 *
 * 2. stepIndex: captured once per loop iteration (currentStep).
 *    Previously stepIndex was incremented after THINK, ACT, OBSERVE
 *    making it an event counter not a step counter.
 *    THINK(0)/ACT(1)/OBSERVE(2) for one iteration was wrong —
 *    they all belong to the same logical step.
 *
 * 3. Tool matching: exact case-insensitive only (not substring).
 *    "webSearch".contains("search") matched wrong tools.
 *    "please calculate this".contains("calculate") caused false match.
 *    Now: methodName.equalsIgnoreCase(toolName.trim()) only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentPlanner planner;
    private final ToolRegistry toolRegistry;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // Safety limits to prevent runaway agents
    private static final int MAX_STEPS = 10;
    private static final Duration TOTAL_TIMEOUT =
            Duration.ofSeconds(120);
    private static final Duration STEP_TIMEOUT =
            Duration.ofSeconds(30);

    /**
     * Execute the full ReACT loop for an agent.
     *
     * Returns Flux<AgentEvent> — one or more events per
     * ReACT iteration so the client sees real-time progress.
     *
     * The loop runs entirely on boundedElastic because every
     * operation inside is blocking (Ollama calls, DB saves).
     * This keeps the WebFlux event loop thread free.
     *
     * @param agent  the Agent entity (already RUNNING status)
     * @param userId owner for DB operations
     * @return Flux<AgentEvent> streamed step events
     */
    public Flux<AgentEvent> execute(
            Agent agent, UUID userId) {

        String toolList = buildToolList();

        log.info(
                "Agent executing: id={} tools={}",
                agent.id(),
                toolRegistry.count());

        return Flux.<AgentEvent>create(sink ->
                        runLoop(sink, agent,
                                userId, toolList))
                .subscribeOn(
                        // Run blocking operations on separate thread
                        // Never block the WebFlux event loop
                        Schedulers.boundedElastic())
                .timeout(TOTAL_TIMEOUT)
                .onErrorResume(error -> {
                    log.error(
                            "Agent execution error: "
                                    + "id={} error={}",
                            agent.id(),
                            error.getMessage());
                    return Flux.just(
                            AgentEvent.error(
                                    "Agent failed: "
                                            + error.getMessage()));
                });
    }

    // ── Private: Main Loop ────────────────────────

    /**
     * The full ReACT execution loop.
     *
     * Runs synchronously on boundedElastic inside Flux.create().
     * Multiple sink.next() calls per iteration are allowed.
     *
     * Checks sink.isCancelled() at the start of
     * each iteration. If the SSE client disconnects, the loop
     * exits immediately instead of continuing to call Ollama
     * and saving steps for up to 120 seconds.
     *
     * Captures currentStep = stepIndex at the top
     * of each iteration. All events from one AI response use the
     * same stepIndex (THINK + FINAL are the same logical step).
     * stepIndex only increments ONCE per complete ReACT iteration,
     * not once per event emitted.
     *
     * @param sink     FluxSink to emit events to
     * @param agent    the agent being executed
     * @param userId   owner
     * @param toolList formatted tool list for planner
     */
    private void runLoop(
            FluxSink<AgentEvent> sink,
            Agent agent,
            UUID userId,
            String toolList) {

        StringBuilder history = new StringBuilder();
        int stepIndex = 0;

        try {
            while (stepIndex < MAX_STEPS) {

                // Honor subscriber cancellation.
                // If SSE client disconnects → stop immediately.
                // Without this: loop runs full 120s consuming
                // boundedElastic threads for abandoned requests.
                if (sink.isCancelled()) {
                    log.info(
                            "Agent cancelled by client: "
                                    + "id={}",
                            agent.id());
                    return;
                }

                // Capture step index ONCE per iteration.
                // THINK + FINAL in one AI response belong to the same
                // logical step. All events use currentStep.
                // stepIndex only increments once at end of iteration.
                final int currentStep = stepIndex;

                // Ask planner for next step
                // STEP_TIMEOUT prevents hanging if Ollama is slow
                String rawResponse = planner
                        .planNextStep(
                                agent.goal(),
                                toolList,
                                history.toString())
                        .timeout(STEP_TIMEOUT)
                        .block();

                AgentPlanner.PlanResult plan =
                        planner.parseResponse(rawResponse);

                if (plan == null) {
                    sink.next(AgentEvent.error(
                            "Null plan from AI"));
                    sink.complete();
                    return;
                }

                // ── Emit THINK event ─────────────────
                // Uses currentStep — same index as other events
                // this iteration (they are all one logical step)
                if (plan.thought() != null
                        && !plan.thought().isBlank()) {

                    saveStep(AgentStep.createThink(
                            agent.id(), userId,
                            currentStep,    // FIX: not stepIndex
                            plan.thought()))
                            .block();

                    sink.next(AgentEvent.think(
                            currentStep,    // FIX: same step
                            plan.thought()));

                    history.append("THOUGHT: ")
                            .append(plan.thought())
                            .append("\n");

                    // NOTE: stepIndex NOT incremented here
                    // Will increment once at end of iteration
                }

                // ── Emit FINAL event ─────────────────
                // Same currentStep as THINK above.
                // They are the same logical ReACT step.
                if (plan.isFinal()) {

                    saveStep(AgentStep.createFinal(
                            agent.id(), userId,
                            currentStep,    // FIX: same step
                            plan.finalAnswer()))
                            .block();

                    sink.next(AgentEvent.finalAnswer(
                            currentStep,    // FIX: same step
                            plan.finalAnswer()));

                    sink.complete();
                    return;
                }

                // ── Emit ACT + OBSERVE events ─────────
                // ACT and OBSERVE share currentStep with THINK.
                // They all represent one ReACT iteration.
                if (plan.isAction()) {

                    saveStep(AgentStep.createAct(
                            agent.id(), userId,
                            currentStep,    // FIX: same step
                            plan.toolName(),
                            plan.toolInput()))
                            .block();

                    sink.next(AgentEvent.act(
                            currentStep,    // FIX: same step
                            plan.toolName(),
                            plan.toolInput()));

                    history.append("ACTION: ")
                            .append(plan.toolName())
                            .append("\nINPUT: ")
                            .append(plan.toolInput())
                            .append("\n");

                    // Execute the tool (blocking call)
                    String toolResult = executeTool(
                            plan.toolName(),
                            plan.toolInput());

                    saveStep(AgentStep.createObserve(
                            agent.id(), userId,
                            currentStep,    // FIX: same step
                            plan.toolName(),
                            toolResult))
                            .block();

                    sink.next(AgentEvent.observe(
                            currentStep,    // FIX: same step
                            plan.toolName(),
                            toolResult));

                    history.append("OBSERVATION: ")
                            .append(toolResult)
                            .append("\n\n");
                }

                // ── Handle planner ERROR ──────────────
                if (plan.isError()) {
                    sink.next(AgentEvent.error(
                            plan.error()));
                    sink.complete();
                    return;
                }

                // Increment ONCE per ReACT iteration.
                // Not once per event emitted.
                // One iteration = THINK + (FINAL or ACT+OBSERVE)
                stepIndex++;
            }

            // Reached MAX_STEPS without completing
            log.warn(
                    "Agent hit max steps: "
                            + "id={} steps={}",
                    agent.id(), MAX_STEPS);
            sink.next(AgentEvent.error(
                    "Maximum steps ("
                            + MAX_STEPS
                            + ") reached without completing"));
            sink.complete();

        } catch (Exception error) {
            log.error(
                    "Agent loop failed: "
                            + "id={} error={}",
                    agent.id(),
                    error.getMessage());
            sink.error(error);
        }
    }

    // ── Private: Tool Execution ───────────────────

    /**
     * Execute a tool by name with given input.
     *
     * Scans registered JarvisTool beans for @Tool
     * annotated methods matching the tool name.
     *
     * Exact case-insensitive matching only.
     * Previous substring match was dangerous:
     * - "webSearch" matched "search" (wrong tool)
     * - "please calculate this" matched "calculate" (wrong context)
     * - Tool dispatch depended on reflection ordering
     *
     * Now: methodName.equalsIgnoreCase(toolName.trim())
     * AI must return the exact method name from the tool list.
     * This is reliable because the system prompt shows exact names.
     *
     * Never throws — returns error string on failure.
     * Agent loop continues even if one tool fails.
     *
     * @param toolName exact method name from AI response
     * @param input    input string for the tool method
     * @return tool result as string, never null
     */
    private String executeTool(
            String toolName, String input) {

        long startMs = System.currentTimeMillis();

        try {
            for (JarvisTool tool :
                    toolRegistry.getAll()) {

                for (Method method :
                        tool.getClass()
                                .getDeclaredMethods()) {

                    org.springframework.ai
                            .tool.annotation.Tool ann =
                            method.getAnnotation(
                                    org.springframework.ai
                                            .tool.annotation
                                            .Tool.class);

                    if (ann == null) continue;

                    String methodName = method.getName();

                    // FIX Issue 3: Exact match only.
                    // Substring matching caused wrong tool dispatch:
                    // "webSearch".contains("search") → true (wrong!)
                    // "calculate this".contains("calculate") → true (wrong!)
                    boolean matches =
                            methodName.equalsIgnoreCase(
                                    toolName.trim());

                    if (matches) {
                        Object result =
                                method.invoke(tool, input);

                        long duration =
                                System.currentTimeMillis()
                                        - startMs;

                        log.info(
                                "Tool executed: "
                                        + "{}({}) in {}ms",
                                methodName,
                                input.length(),
                                duration);

                        return result != null
                                ? result.toString()
                                : "No result returned";
                    }
                }
            }

            log.warn(
                    "Tool not found: '{}' — "
                            + "available: {}",
                    toolName,
                    buildToolNames());

            return "Tool '" + toolName
                    + "' not found. "
                    + "Available tools: "
                    + buildToolNames();

        } catch (Exception e) {
            log.error(
                    "Tool execution failed: "
                            + "{} → {}",
                    toolName, e.getMessage());
            return "Tool error: " + e.getMessage();
        }
    }

    // ── Private: Tool Discovery ───────────────────

    /**
     * Build formatted tool list for planner system prompt.
     *
     * Scans all JarvisTool beans and collects @Tool
     * method names + descriptions. New tools added via
     * @Component are automatically included.
     *
     * @return formatted "- methodName: description\n..."
     */
    private String buildToolList() {
        Map<String, String> tools = new LinkedHashMap<>();

        for (JarvisTool tool : toolRegistry.getAll()) {
            for (Method method :
                    tool.getClass().getDeclaredMethods()) {

                org.springframework.ai
                        .tool.annotation.Tool ann =
                        method.getAnnotation(
                                org.springframework.ai
                                        .tool.annotation
                                        .Tool.class);

                if (ann != null) {
                    tools.put(
                            method.getName(),
                            ann.description());
                }
            }
        }

        return planner.formatToolList(tools);
    }

    /**
     * Comma-separated list of all @Tool method names.
     * Shown in error message when requested tool not found.
     * Helps AI correct its tool name in the next step.
     *
     * @return "getWeather, calculate, search, ..."
     */
    private String buildToolNames() {
        StringBuilder sb = new StringBuilder();

        for (JarvisTool tool : toolRegistry.getAll()) {
            for (Method method :
                    tool.getClass().getDeclaredMethods()) {

                if (method.isAnnotationPresent(
                        org.springframework.ai
                                .tool.annotation
                                .Tool.class)) {
                    if (!sb.isEmpty()) sb.append(", ");
                    sb.append(method.getName());
                }
            }
        }

        return sb.toString();
    }

    /**
     * Persist an agent step to DB.
     *
     * Errors logged but never propagated.
     * Step persistence failure must not stop execution —
     * the agent's task is more important than the audit trail.
     *
     * @param step the AgentStep to persist
     * @return Mono<AgentStep> the saved step
     */
    private Mono<AgentStep> saveStep(AgentStep step) {
        return r2dbcEntityTemplate
                .insert(step)
                .doOnSuccess(saved ->
                        log.debug(
                                "Step saved: type={} "
                                        + "step={}",
                                saved.stepType(),
                                saved.stepIndex()))
                .onErrorResume(error -> {
                    log.warn(
                            "Step save failed: {}",
                            error.getMessage());
                    // Return original — agent continues
                    return Mono.just(step);
                });
    }
}