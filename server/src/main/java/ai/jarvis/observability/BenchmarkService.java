package ai.jarvis.observability;

import ai.jarvis.ai.provider.AiProvider;
import ai.jarvis.ai.provider.OllamaProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs performance benchmarks for research paper data.
 *
 * FIX (CI Job #81961634832):
 * Added @Qualifier("gemini") to constructor parameter.
 * Same issue as ProviderRouter — Spring cannot resolve
 * which AiProvider bean to inject without explicit qualifier.
 *
 * In CI (no GEMINI_API_KEY):
 * → GeminiUnavailableProvider named "gemini" is created
 * → @Qualifier("gemini") injects it correctly ✅
 *
 * Locally (with GEMINI_API_KEY):
 * → GeminiProvider named "gemini" is created
 * → @Qualifier("gemini") injects it correctly ✅
 */
@Slf4j
@Service
public class BenchmarkService {

    private final OllamaProvider ollamaProvider;
    private final AiProvider geminiProvider;

    // Standardized prompts for reproducibility
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
     * FIX: @Qualifier("gemini") added.
     * Tells Spring to inject the bean named "gemini"
     * (either GeminiProvider or GeminiUnavailableProvider).
     * Removes @RequiredArgsConstructor — explicit constructor
     * needed to apply @Qualifier to a specific parameter.
     */
    public BenchmarkService(
            OllamaProvider ollamaProvider,
            @Qualifier("gemini")            // ← ADD this
            AiProvider geminiProvider) {
        this.ollamaProvider = ollamaProvider;
        this.geminiProvider = geminiProvider;
    }

    /**
     * Run latency benchmark for a specific provider.
     *
     * @param providerName "ollama" or "gemini"
     * @param runs         number of measurement runs
     */
    public reactor.core.publisher.Mono<BenchmarkResult>
    benchmarkLatency(
            String providerName,
            int runs) {

        return reactor.core.publisher.Mono.fromCallable(() -> {
                    AiProvider provider =
                            resolveProvider(providerName);

                    log.info(
                            "Starting latency benchmark: "
                                    + "provider={} runs={}",
                            providerName, runs);

                    List<BenchmarkResult.RunData> allRuns =
                            new ArrayList<>();

                    log.info("Running warm-up...");
                    runSingle(provider, MEDIUM_PROMPT, 0, true);

                    for (int i = 1; i <= runs; i++) {
                        log.info("Run {}/{}", i, runs);
                        BenchmarkResult.RunData run =
                                runSingle(provider,
                                        MEDIUM_PROMPT, i, false);
                        allRuns.add(run);
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
                .subscribeOn(
                        reactor.core.scheduler.Schedulers
                                .boundedElastic())
                .onErrorResume(error -> {
                    log.error("Benchmark failed: {}",
                            error.getMessage());
                    return reactor.core.publisher.Mono
                            .error(error);
                });
    }

    /**
     * Run throughput benchmark.
     */
    public reactor.core.publisher.Mono<BenchmarkResult>
    benchmarkThroughput(String providerName) {

        return reactor.core.publisher.Mono.fromCallable(() -> {
                    AiProvider provider =
                            resolveProvider(providerName);

                    log.info(
                            "Starting throughput benchmark: "
                                    + "provider={}",
                            providerName);

                    List<BenchmarkResult.RunData> allRuns =
                            new ArrayList<>();

                    runSingle(provider, SHORT_PROMPT, 0, true);

                    String[] prompts = {
                            SHORT_PROMPT, MEDIUM_PROMPT,
                            LONG_PROMPT,
                            MEDIUM_PROMPT, SHORT_PROMPT
                    };

                    for (int i = 0;
                         i < prompts.length; i++) {
                        BenchmarkResult.RunData run =
                                runSingle(provider,
                                        prompts[i],
                                        i + 1, false);
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
                .subscribeOn(
                        reactor.core.scheduler.Schedulers
                                .boundedElastic());
    }

    /**
     * Collect system resource metrics.
     */
    public reactor.core.publisher.Mono<SystemBenchmarkResult>
    benchmarkSystem() {
        return reactor.core.publisher.Mono.fromCallable(() -> {
                    Runtime runtime = Runtime.getRuntime();

                    long totalMemory =
                            runtime.totalMemory()
                                    / (1024 * 1024);
                    long freeMemory =
                            runtime.freeMemory()
                                    / (1024 * 1024);
                    long maxMemory =
                            runtime.maxMemory()
                                    / (1024 * 1024);
                    long usedMemory =
                            totalMemory - freeMemory;
                    int cpuCores =
                            runtime.availableProcessors();

                    boolean ollamaAvailable =
                            ollamaProvider.isAvailable()
                                    .block() == Boolean.TRUE;

                    return new SystemBenchmarkResult(
                            System.getProperty(
                                    "java.version"),
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
                .subscribeOn(
                        reactor.core.scheduler.Schedulers
                                .boundedElastic());
    }

    /**
     * Check if a named provider is currently available.
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

    // ── Private Helpers ───────────────────────────

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
                                + "tokens={} tps={}",
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

    private BenchmarkResult calculateResult(
            String type,
            String providerName,
            String modelName,
            String prompt,
            List<BenchmarkResult.RunData> runs) {

        List<BenchmarkResult.RunData> successful =
                runs.stream()
                        .filter(BenchmarkResult.RunData::success)
                        .toList();

        if (successful.isEmpty()) {
            throw new RuntimeException(
                    "All benchmark runs failed");
        }

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

        double avgTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData
                                ::tokensPerSecond)
                .average().orElse(0);

        double minTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData
                                ::tokensPerSecond)
                .min().orElse(0);

        double maxTps = successful.stream()
                .mapToDouble(
                        BenchmarkResult.RunData
                                ::tokensPerSecond)
                .max().orElse(0);

        int avgTokens = (int) successful.stream()
                .mapToInt(
                        BenchmarkResult.RunData
                                ::tokensGenerated)
                .average().orElse(0);

        List<Long> ttfts = successful.stream()
                .map(BenchmarkResult.RunData
                        ::timeToFirstTokenMs)
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

        // Stable runs (last 70%)
        int stableFrom = (int) Math.ceil(
                successful.size() * 0.3);
        List<BenchmarkResult.RunData> stableRuns =
                new ArrayList<>(successful.subList(
                        stableFrom, successful.size()));

        if (!stableRuns.isEmpty()) {
            double stableAvgLatency = stableRuns.stream()
                    .mapToLong(
                            BenchmarkResult.RunData
                                    ::latencyMs)
                    .average().orElse(0);
            double stableAvgTps = stableRuns.stream()
                    .mapToDouble(
                            BenchmarkResult.RunData
                                    ::tokensPerSecond)
                    .average().orElse(0);
            log.info(
                    "Stable runs ({}-{}): avg={}ms tps={}",
                    stableFrom + 1,
                    successful.size(),
                    String.format("%.2f", stableAvgLatency),
                    String.format("%.2f", stableAvgTps)
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
                .mapToDouble(v -> Math.pow(v - mean, 2))
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