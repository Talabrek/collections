package com.blockworlds.collections.spawn;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tracks failure reasons during spawn location attempts.
 * Provides detailed statistics for debugging spawn issues.
 */
public class SpawnFailureStats {

    private final Map<String, Integer> failureCounts = new HashMap<>();
    private int totalAttempts = 0;

    /**
     * Record a spawn attempt failure with a specific reason.
     *
     * @param reason The failure reason (e.g., "biome", "time", "light", "sky-access")
     */
    public void recordFailure(String reason) {
        failureCounts.merge(reason, 1, Integer::sum);
        totalAttempts++;
    }

    /**
     * Record a successful spawn attempt.
     */
    public void recordSuccess() {
        totalAttempts++;
    }

    /**
     * Get the total number of attempts made.
     */
    public int getTotalAttempts() {
        return totalAttempts;
    }

    /**
     * Get the failure count for a specific reason.
     */
    public int getFailureCount(String reason) {
        return failureCounts.getOrDefault(reason, 0);
    }

    /**
     * Get all failure counts.
     */
    public Map<String, Integer> getFailureCounts() {
        return Map.copyOf(failureCounts);
    }

    /**
     * Get the most common failure reason.
     *
     * @return The failure reason with the highest count, or "unknown" if no failures
     */
    public String getTopReason() {
        return failureCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    /**
     * Get a human-readable summary of failure statistics.
     * Format: "biome: 45, time: 30, light: 10"
     */
    public String getSummary() {
        if (failureCounts.isEmpty()) {
            return "No attempts made";
        }

        return failureCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Check if any attempts were made.
     */
    public boolean hasAttempts() {
        return totalAttempts > 0;
    }

    /**
     * Check if all attempts failed.
     */
    public boolean allFailed() {
        int totalFailures = failureCounts.values().stream().mapToInt(Integer::intValue).sum();
        return totalFailures == totalAttempts;
    }

    @Override
    public String toString() {
        return "SpawnFailureStats{attempts=" + totalAttempts + ", failures=" + getSummary() + "}";
    }
}
