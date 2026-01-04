package com.blockworlds.collections.spawn;

import org.bukkit.Location;

/**
 * Result of a spawn location search attempt.
 * Contains either a valid location or failure statistics.
 */
public record SpawnResult(
        boolean success,
        Location location,
        SpawnFailureStats stats,
        boolean relaxedConditions
) {

    /**
     * Create a successful result with a spawn location.
     *
     * @param location The valid spawn location
     * @param stats    Statistics from the search
     * @return A successful SpawnResult
     */
    public static SpawnResult success(Location location, SpawnFailureStats stats) {
        return new SpawnResult(true, location, stats, false);
    }

    /**
     * Create a successful result with relaxed conditions.
     *
     * @param location The valid spawn location
     * @param stats    Statistics from the search
     * @return A successful SpawnResult with relaxed conditions
     */
    public static SpawnResult successRelaxed(Location location, SpawnFailureStats stats) {
        return new SpawnResult(true, location, stats, true);
    }

    /**
     * Create a failure result.
     *
     * @param stats Statistics from the failed search
     * @return A failed SpawnResult
     */
    public static SpawnResult failure(SpawnFailureStats stats) {
        return new SpawnResult(false, null, stats, false);
    }

    /**
     * Get a human-readable message about this result.
     *
     * @return Description of the result
     */
    public String getMessage() {
        if (success) {
            String msg = "Found spawn location at " + formatLocation();
            if (relaxedConditions) {
                msg += " (with relaxed conditions)";
            }
            return msg;
        } else {
            return "Failed to find spawn location after " + stats.getTotalAttempts() +
                    " attempts. Top failure reason: " + stats.getTopReason();
        }
    }

    /**
     * Format the location as a readable string.
     */
    private String formatLocation() {
        if (location == null) {
            return "null";
        }
        return String.format("(%d, %d, %d in %s)",
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                location.getWorld() != null ? location.getWorld().getName() : "unknown");
    }
}
