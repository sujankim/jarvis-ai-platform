package ai.jarvis.observability;

import java.time.Instant;
import java.util.List;

/**
 * Holds results of a single benchmark run.
 * Designed for research paper data collection.
 *
 * STATISTICAL FIELDS:
 * Multiple runs are aggregated here.
 * P50/P95/P99 for latency distribution.
 * Standard deviation for reproducibility claims.
 */
public record BenchmarkResult(

        // Benchmark identification
        String benchmarkType,
        String provider,
        String model,
        Instant timestamp,

        // Run configuration
        int totalRuns,
        String testPrompt,

        // Latency metrics (milliseconds)
        long minLatencyMs,
        long maxLatencyMs,
        double avgLatencyMs,
        long p50LatencyMs,
        long p95LatencyMs,
        long p99LatencyMs,
        double stdDevMs,

        // Token metrics
        double avgTokensPerSecond,
        double minTokensPerSecond,
        double maxTokensPerSecond,
        int avgTokensGenerated,

        // Time to first token (ms)
        long avgTimeToFirstToken,
        long minTimeToFirstToken,
        long maxTimeToFirstToken,

        // Individual run data (for paper appendix)
        List<RunData> runs,

        // System context at time of benchmark
        String javaVersion,
        String springBootVersion,
        String springAiVersion,
        long availableMemoryMb,
        int availableCpuCores

) {
    /** Data from a single benchmark run. */
    public record RunData(
            int runNumber,
            long latencyMs,
            long timeToFirstTokenMs,
            double tokensPerSecond,
            int tokensGenerated,
            boolean success,
            String errorMessage
    ) {}
}