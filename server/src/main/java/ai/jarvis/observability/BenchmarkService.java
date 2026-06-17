package ai.jarvis.observability;

import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.GeminiProvider;
import ai.jarvis.ai.provider.OllamaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs performance benchmarks for research paper data.
 *
 * METHODOLOGY:
 * - Warm-up run before measurement (model loading)
 * - N=10 runs per benchmark (configurable)
 * - Same standardized prompts for reproducibility
 * - P50/P95/P99 latency percentiles
 * - Time to first token (TTFT) — most important metric
 *
 * PAPER CITATION FORMAT:
 * "Benchmarks run on [hardware] with [model].
 *  N=10 runs, warm-up excluded.
 *  Measurements in milliseconds."
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final OllamaProvider ollamaProvider;
    private final AiProvider geminiProvider;

    // Standardized prompts for reproducibility
    // SAME prompts used across all providers
    private static final String SHORT_PROMPT =
            "Reply with exactly: 'Benchmark test OK'";

    private static final String MEDIUM_PROMPT =
            "Explain what Java is in exactly 3 sentences.";

    private static final String LONG_PROMPT =
            "List 5 key features of Spring Boot 4. "
                    + "For each feature, write one sentence "
                    + "explaining why it matters.";

    private static final int DEFAULT_RUNS = 10;
    private static final int WARMUP_RUNS = 2;

    /**
     * Run latency benchmark for a specific provider.
     * Measures: latency, TTFT, tokens/second.
     *
     * @param providerName "ollama" or "gemini"
     * @param runs         number of measurement runs
     */
    public Mono<BenchmarkResult> benchmarkLatency(
            String providerName,
            int runs) {

        return Mono.fromCallable(() -> {
                    AiProvider provider =
                            resolveProvider(providerName);

                    log.info(
                            "Starting latency benchmark: "
                                    + "provider={} runs={}",
                            providerName, runs);

                    List<BenchmarkResult.RunData> allRuns =
                            new ArrayList<>();

                    // Warm-up run (excluded from results)
                    log.info("Running warm-up...");
                    runSingle(provider, MEDIUM_PROMPT, 0, true);

                    // Measurement runs
                    for (int i = 1; i <= runs; i++) {
                        log.info(
                                "Run {}/{}", i, runs);
                        BenchmarkResult.RunData run =
                                runSingle(provider,
                                        MEDIUM_PROMPT, i, false);
                        allRuns.add(run);

                        // Brief pause between runs
                        Thread.sleep(500);
                    }

                    return calculateResult(
                            "LATENCY",
                            providerName,
                            provider.getModelName(),
                            MEDIUM_PROMPT,
                            allRuns
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    log.error("Benchmark failed: {}",
                            error.getMessage());
                    return Mono.error(error);
                });
    }

    /**
     * Run throughput benchmark.
     * Tests multiple prompt lengths for comparison.
     */
    public Mono<BenchmarkResult> benchmarkThroughput(
            String providerName) {

        return Mono.fromCallable(() -> {
                    AiProvider provider =
                            resolveProvider(providerName);

                    log.info(
                            "Starting throughput benchmark: "
                                    + "provider={}",
                            providerName);

                    List<BenchmarkResult.RunData> allRuns =
                            new ArrayList<>();

                    // Warm-up
                    runSingle(provider, SHORT_PROMPT, 0, true);

                    // Test with different prompt lengths
                    String[] prompts = {
                            SHORT_PROMPT, MEDIUM_PROMPT, LONG_PROMPT,
                            MEDIUM_PROMPT, SHORT_PROMPT
                    };

                    for (int i = 0; i < prompts.length; i++) {
                        BenchmarkResult.RunData run =
                                runSingle(provider,
                                        prompts[i], i + 1, false);
                        allRuns.add(run);
                        Thread.sleep(300);
                    }

                    return calculateResult(
                            "THROUGHPUT",
                            providerName,
                            provider.getModelName(),
                            "MIXED_PROMPTS",
                            allRuns
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Collect system resource metrics.
     * Hardware context for paper reproducibility.
     */
    public Mono<SystemBenchmarkResult> benchmarkSystem() {
        return Mono.fromCallable(() -> {
                    Runtime runtime = Runtime.getRuntime();

                    long totalMemory =
                            runtime.totalMemory() / (1024 * 1024);
                    long freeMemory =
                            runtime.freeMemory() / (1024 * 1024);
                    long maxMemory =
                            runtime.maxMemory() / (1024 * 1024);
                    long usedMemory = totalMemory - freeMemory;
                    int cpuCores =
                            runtime.availableProcessors();

                    // Ollama availability
                    boolean ollamaAvailable =
                            ollamaProvider.isAvailable()
                                    .block() == Boolean.TRUE;

                    return new SystemBenchmarkResult(
                            System.getProperty("java.version"),
                            "Spring Boot 4.0.6",
                            "Spring AI 2.0.0-M8",
                            totalMemory,
                            freeMemory,
                            maxMemory,
                            usedMemory,
                            cpuCores,
                            ollamaProvider.getModelName(),
                            ollamaAvailable,
                            Instant.now()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ── Private Helpers ───────────────────────────

    /**
     * Run a single benchmark measurement.
     * Captures: latency, TTFT, tokens/second.
     */
    private BenchmarkResult.RunData runSingle(
            AiProvider provider,
            String promptText,
            int runNumber,
            boolean isWarmup) {

        long startMs = System.currentTimeMillis();
        AtomicLong firstTokenMs = new AtomicLong(-1);
        StringBuilder response = new StringBuilder();
        int[] tokenCount = {0};

        try {
            Prompt prompt = new Prompt(
                    List.of(new UserMessage(promptText)));

            provider.streamChat(prompt)
                    .doOnNext(token -> {
                        // Record time to first token
                        if (firstTokenMs.get() == -1) {
                            firstTokenMs.set(
                                    System.currentTimeMillis()
                                            - startMs);
                        }
                        response.append(token);
                        tokenCount[0]++;
                    })
                    .blockLast();

            long totalMs =
                    System.currentTimeMillis() - startMs;
            double tps = tokenCount[0] > 0
                    ? tokenCount[0] * 1000.0 / totalMs
                    : 0;

            if (!isWarmup) {
                log.debug(
                        "Run {}: {}ms TTFT={}ms "
                                + "tokens={} tps={:.1f}",
                        runNumber, totalMs,
                        firstTokenMs.get(),
                        tokenCount[0], tps);
            }

            return new BenchmarkResult.RunData(
                    runNumber,
                    totalMs,
                    firstTokenMs.get() == -1
                            ? 0 : firstTokenMs.get(),
                    tps,
                    tokenCount[0],
                    true,
                    null
            );

        } catch (Exception e) {
            long totalMs =
                    System.currentTimeMillis() - startMs;
            return new BenchmarkResult.RunData(
                    runNumber, totalMs, 0, 0, 0,
                    false, e.getMessage()
            );
        }
    }

    /**
     * Calculate aggregate statistics from run data.
     */
    private BenchmarkResult calculateResult(
            String type,
            String providerName,
            String modelName,
            String prompt,
            List<BenchmarkResult.RunData> runs) {

        // Filter successful runs only
        List<BenchmarkResult.RunData> successful =
                runs.stream()
                        .filter(BenchmarkResult.RunData::success)
                        .toList();

        if (successful.isEmpty()) {
            throw new RuntimeException(
                    "All benchmark runs failed");
        }

        // Latency statistics
        List<Long> latencies = successful.stream()
                .map(BenchmarkResult.RunData::latencyMs)
                .sorted()
                .toList();

        LongSummaryStatistics latencyStats =
                latencies.stream()
                        .mapToLong(Long::longValue)
                        .summaryStatistics();

        long p50 = percentile(latencies, 50);
        long p95 = percentile(latencies, 95);
        long p99 = percentile(latencies, 99);

        double avgLatency = latencyStats.getAverage();
        double stdDev = calculateStdDev(
                latencies, avgLatency);

        // Token metrics
        double avgTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData::tokensPerSecond)
                .average().orElse(0);

        double minTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData::tokensPerSecond)
                .min().orElse(0);

        double maxTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData::tokensPerSecond)
                .max().orElse(0);

        int avgTokens = (int) successful.stream()
                .mapToInt(
                        BenchmarkResult.RunData::tokensGenerated)
                .average().orElse(0);

        // TTFT statistics
        List<Long> ttfts = successful.stream()
                .map(BenchmarkResult.RunData::timeToFirstTokenMs)
                .filter(t -> t > 0)
                .sorted()
                .toList();

        long avgTtft = ttfts.isEmpty() ? 0
                : (long) ttfts.stream()
                .mapToLong(Long::longValue)
                .average().orElse(0);
        long minTtft = ttfts.isEmpty()
                ? 0 : ttfts.getFirst();
        long maxTtft = ttfts.isEmpty()
                ? 0 : ttfts.getLast();

        // ── Stable runs (last 70% of successful runs) ──
        // This gives a better view of steady-state performance
        int stableFrom = (int) Math.ceil(successful.size() * 0.3);
        List<BenchmarkResult.RunData> stableRuns =
                new ArrayList<>(successful.subList(stableFrom, successful.size()));

        if (!stableRuns.isEmpty()) {
            double stableAvgLatency = stableRuns.stream()
                    .mapToLong(BenchmarkResult.RunData::latencyMs)
                    .average().orElse(0);
            double stableAvgTps = stableRuns.stream()
                    .mapToDouble(BenchmarkResult.RunData::tokensPerSecond)
                    .average().orElse(0);
            log.info(
                    "Stable runs ({}-{}): avg={:.2f}ms tps={:.2f}",
                    stableFrom + 1,
                    successful.size(),
                    stableAvgLatency,
                    stableAvgTps
            );
        }

        Runtime runtime = Runtime.getRuntime();

        return new BenchmarkResult(
                type, providerName, modelName,
                Instant.now(),
                successful.size(), prompt,
                latencyStats.getMin(),
                latencyStats.getMax(),
                avgLatency,
                p50, p95, p99, stdDev,
                avgTps, minTps, maxTps, avgTokens,
                avgTtft, minTtft, maxTtft,
                runs,
                System.getProperty("java.version"),
                "Spring Boot 4.0.6",
                "Spring AI 2.0.0-M8",
                runtime.freeMemory() / (1024 * 1024),
                runtime.availableProcessors()
        );
    }

    private long percentile(
            List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(
                pct / 100.0 * sorted.size()) - 1;
        return sorted.get(
                Math.max(0, Math.min(
                        index, sorted.size() - 1)));
    }

    private double calculateStdDev(
            List<Long> values, double mean) {
        if (values.size() < 2) return 0;
        double variance = values.stream()
                .mapToDouble(v ->
                        Math.pow(v - mean, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private AiProvider resolveProvider(String name) {
        return switch (name.toLowerCase()) {
            case "ollama" -> ollamaProvider;
            case "gemini" -> geminiProvider;
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + name
                            + ". Use 'ollama' or 'gemini'");
        };
    }

    /**
     * Check if a named provider is currently available.
     * Used by CLI to give friendly error before benchmark.
     */
    public boolean isProviderAvailable(String name) {
        try {
            AiProvider provider = resolveProvider(name);
            return provider.isAvailable().block()
                    == Boolean.TRUE;
        } catch (Exception e) {
            return false;
        }
    }

    // ── System Result Record ──────────────────────

    public record SystemBenchmarkResult(
            String javaVersion,
            String springBootVersion,
            String springAiVersion,
            long totalMemoryMb,
            long freeMemoryMb,
            long maxMemoryMb,
            long usedMemoryMb,
            int cpuCores,
            String ollamaModel,
            boolean ollamaAvailable,
            Instant timestamp) {}
}