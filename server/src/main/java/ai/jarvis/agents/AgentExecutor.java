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
 * Flux.generate() allows only ONE sink.next() per generator call.
 * When AI returns THOUGHT + FINAL_ANSWER in one response, we need
 * to emit two events (THINK + FINAL) from the same iteration.
 * Flux.generate() throws "More than one call to onNext" in this case.
 *
 * Flux.create() allows unlimited sink.next() calls, so a single
 * AI response can produce THINK + FINAL or THINK + ACT + OBSERVE
 * all in one loop iteration without errors.
 *
 * The entire loop runs on boundedElastic because:
 * - planNextStep() calls Ollama (blocking HTTP)
 * - executeTool() calls @Tool methods (may be blocking)
 * - saveStep() calls .block() on R2DBC (blocking)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentPlanner planner;
    private final ToolRegistry toolRegistry;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // Safety limits
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
     * This keeps the WebFlux event loop free.
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

        // Flux.create() replaces Flux.generate()
        // create() allows multiple sink.next() per iteration
        // generate() only allows ONE per iteration → crashes
        // when THINK + FINAL are emitted in same AI response
        return Flux.<AgentEvent>create(sink ->
                        runLoop(sink, agent, userId, toolList))
                .subscribeOn(
                        // Run blocking loop on separate thread
                        // never block WebFlux event loop
                        Schedulers.boundedElastic())
                .timeout(TOTAL_TIMEOUT)
                .onErrorResume(error -> {
                    log.error(
                            "Agent execution error: id={} "
                                    + "error={}",
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
     * Runs synchronously inside Flux.create() on
     * a boundedElastic thread. This is correct because:
     * - Each step depends on the previous result
     * - Sequential execution is required
     * - All operations are blocking anyway
     *
     * Multiple sink.next() calls per iteration are allowed
     * by Flux.create() — this is the key difference from
     * Flux.generate() which only allows one per iteration.
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

                // Ask planner for next step
                // STEP_TIMEOUT prevents hanging if Ollama slow
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
                // Multiple sink.next() allowed in Flux.create()
                // This would have crashed with Flux.generate()
                if (plan.thought() != null
                        && !plan.thought().isBlank()) {

                    saveStep(AgentStep.createThink(
                            agent.id(), userId,
                            stepIndex,
                            plan.thought()))
                            .block();

                    // sink.next() call #1 this iteration
                    sink.next(AgentEvent.think(
                            stepIndex,
                            plan.thought()));

                    history.append("THOUGHT: ")
                            .append(plan.thought())
                            .append("\n");
                    stepIndex++;
                }

                // ── Emit FINAL event ─────────────────
                // May happen in SAME iteration as THINK above
                // sink.next() call #2 — only works with create()
                if (plan.isFinal()) {

                    saveStep(AgentStep.createFinal(
                            agent.id(), userId,
                            stepIndex,
                            plan.finalAnswer()))
                            .block();

                    // sink.next() call #2 — WORKS with create()
                    // CRASHED with generate() (only 1 allowed)
                    sink.next(AgentEvent.finalAnswer(
                            stepIndex,
                            plan.finalAnswer()));

                    // Complete the stream — agent done
                    sink.complete();
                    return;
                }

                // ── Emit ACT + OBSERVE events ─────────
                // Three sink.next() calls possible:
                // THINK (above) + ACT + OBSERVE this iteration
                // All allowed by Flux.create()
                if (plan.isAction()) {

                    // Save and emit ACT step
                    saveStep(AgentStep.createAct(
                            agent.id(), userId,
                            stepIndex,
                            plan.toolName(),
                            plan.toolInput()))
                            .block();

                    sink.next(AgentEvent.act(
                            stepIndex,
                            plan.toolName(),
                            plan.toolInput()));

                    history.append("ACTION: ")
                            .append(plan.toolName())
                            .append("\nINPUT: ")
                            .append(plan.toolInput())
                            .append("\n");
                    stepIndex++;

                    // Execute the tool (blocking call)
                    String toolResult = executeTool(
                            plan.toolName(),
                            plan.toolInput());

                    // Save and emit OBSERVE step
                    saveStep(AgentStep.createObserve(
                            agent.id(), userId,
                            stepIndex,
                            plan.toolName(),
                            toolResult))
                            .block();

                    sink.next(AgentEvent.observe(
                            stepIndex,
                            plan.toolName(),
                            toolResult));

                    history.append("OBSERVATION: ")
                            .append(toolResult)
                            .append("\n\n");
                    stepIndex++;
                }

                // ── Handle ERROR from planner ─────────
                if (plan.isError()) {
                    sink.next(AgentEvent.error(
                            plan.error()));
                    sink.complete();
                    return;
                }
            }

            // Reached MAX_STEPS without FINAL
            log.warn(
                    "Agent hit max steps: id={} steps={}",
                    agent.id(), MAX_STEPS);
            sink.next(AgentEvent.error(
                    "Maximum steps (" + MAX_STEPS
                            + ") reached without completing"));
            sink.complete();

        } catch (Exception error) {
            // Any unhandled exception → error event
            log.error(
                    "Agent loop failed: id={} error={}",
                    agent.id(),
                    error.getMessage());
            sink.error(error);
        }
    }

    // ── Private: Tool Execution ───────────────────

    /**
     * Execute a tool by name with given input.
     *
     * Scans all registered JarvisTool beans for
     * @Tool annotated methods matching the tool name.
     * Case-insensitive matching handles AI variations.
     *
     * Never throws — returns error string on failure.
     * Agent loop continues even if one tool fails.
     *
     * @param toolName name returned by AI planner
     * @param input    input string for the tool
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

                    // Case-insensitive match
                    // Also handles partial matches from AI
                    boolean matches =
                            methodName
                                    .equalsIgnoreCase(toolName)
                                    || toolName.toLowerCase()
                                    .contains(
                                            methodName
                                                    .toLowerCase());

                    if (matches) {
                        Object result =
                                method.invoke(tool, input);

                        long duration =
                                System.currentTimeMillis()
                                        - startMs;

                        log.info(
                                "Tool executed: {}({}) "
                                        + "in {}ms",
                                methodName,
                                input.length(),
                                duration);

                        return result != null
                                ? result.toString()
                                : "No result returned";
                    }
                }
            }

            log.warn("Tool not found: {}", toolName);
            return "Tool '" + toolName
                    + "' not found. "
                    + "Available: "
                    + buildToolNames();

        } catch (Exception e) {
            log.error(
                    "Tool execution failed: {} → {}",
                    toolName, e.getMessage());
            return "Tool error: " + e.getMessage();
        }
    }

    // ── Private: Tool Discovery ───────────────────

    /**
     * Build formatted tool list for planner prompt.
     *
     * Scans all JarvisTool beans and extracts @Tool
     * method names + descriptions for the system prompt.
     * New tools are automatically included — no changes needed.
     *
     * @return formatted string: "- methodName: description\n..."
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
     * Comma-separated list of all tool method names.
     * Shown in error when requested tool is not found.
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
     * Save an agent step to DB.
     *
     * Errors are logged but never propagate.
     * Step persistence failure must not stop the agent
     * from completing its task — best-effort only.
     *
     * @param step the step to persist
     * @return Mono<AgentStep> the saved step
     */
    private Mono<AgentStep> saveStep(AgentStep step) {
        return r2dbcEntityTemplate
                .insert(step)
                .doOnSuccess(saved ->
                        log.debug(
                                "Step saved: type={} "
                                        + "index={}",
                                saved.stepType(),
                                saved.stepIndex()))
                .onErrorResume(error -> {
                    log.warn(
                            "Step save failed: {}",
                            error.getMessage());
                    // Return original step on failure
                    // Agent continues regardless
                    return Mono.just(step);
                });
    }
}