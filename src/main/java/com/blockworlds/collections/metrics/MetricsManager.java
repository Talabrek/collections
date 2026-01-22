package com.blockworlds.collections.metrics;

import com.blockworlds.collections.Collections;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages plugin metrics including thread-safe counters and bStats integration.
 *
 * <p>Counters are tracked using AtomicLong for thread-safe increment operations
 * from async event handlers. bStats provides anonymous community analytics
 * including server count and custom charts.</p>
 */
public class MetricsManager {

    private static final int DEFAULT_BSTATS_ID = 12345; // Replace after registration at https://bstats.org/

    // Thread-safe counters
    private final AtomicLong itemsCollected = new AtomicLong(0);
    private final AtomicLong collectionsCompleted = new AtomicLong(0);
    private final AtomicLong spawnAttempts = new AtomicLong(0);
    private final AtomicLong spawnSuccesses = new AtomicLong(0);
    private final AtomicLong spawnFailures = new AtomicLong(0);

    private final Collections plugin;
    private Metrics metrics;

    /**
     * Create a new MetricsManager.
     *
     * @param plugin The plugin instance
     */
    public MetricsManager(Collections plugin) {
        this.plugin = plugin;

        boolean metricsEnabled = plugin.getConfigManager().getBoolean("metrics.enabled", true);
        if (metricsEnabled) {
            int pluginId = plugin.getConfigManager().getInt("metrics.bstats-id", DEFAULT_BSTATS_ID);
            this.metrics = new Metrics(plugin, pluginId);
            setupCustomCharts();
            plugin.getLogger().info("bStats metrics enabled with plugin ID: " + pluginId);
        } else {
            plugin.getLogger().info("bStats metrics disabled in config");
        }
    }

    /**
     * Set up custom bStats charts for plugin analytics.
     */
    private void setupCustomCharts() {
        if (metrics == null) {
            return;
        }

        // Storage type chart (sqlite/mysql)
        metrics.addCustomChart(new SimplePie("storage_type", () ->
            plugin.getConfigManager().getDatabaseType().toLowerCase()
        ));

        // Collection count chart (buckets)
        metrics.addCustomChart(new SimplePie("collection_count", () -> {
            int count = plugin.getCollectionManager().getCollectionCount();
            if (count <= 5) {
                return "1-5";
            } else if (count <= 10) {
                return "6-10";
            } else if (count <= 20) {
                return "11-20";
            } else {
                return "20+";
            }
        }));

        // Spawn success rate chart (buckets)
        metrics.addCustomChart(new SimplePie("spawn_success_rate", () -> {
            double rate = getSpawnSuccessRate();
            if (rate >= 90.0) {
                return "90-100%";
            } else if (rate >= 70.0) {
                return "70-89%";
            } else if (rate >= 50.0) {
                return "50-69%";
            } else {
                return "Below 50%";
            }
        }));
    }

    // ========== Counter Increment Methods ==========

    /**
     * Record an item being collected (added to journal).
     */
    public void recordItemCollected() {
        itemsCollected.incrementAndGet();
    }

    /**
     * Record a collection being completed.
     */
    public void recordCollectionCompleted() {
        collectionsCompleted.incrementAndGet();
    }

    /**
     * Record a spawn attempt with its result.
     *
     * @param success true if spawn succeeded, false if failed
     */
    public void recordSpawnAttempt(boolean success) {
        spawnAttempts.incrementAndGet();
        if (success) {
            spawnSuccesses.incrementAndGet();
        } else {
            spawnFailures.incrementAndGet();
        }
    }

    // ========== Counter Getter Methods ==========

    /**
     * Get the total number of items collected since server start.
     *
     * @return Total items collected
     */
    public long getItemsCollected() {
        return itemsCollected.get();
    }

    /**
     * Get the total number of collections completed since server start.
     *
     * @return Total collections completed
     */
    public long getCollectionsCompleted() {
        return collectionsCompleted.get();
    }

    /**
     * Get the total number of spawn attempts since server start.
     *
     * @return Total spawn attempts
     */
    public long getSpawnAttempts() {
        return spawnAttempts.get();
    }

    /**
     * Get the total number of successful spawns since server start.
     *
     * @return Total successful spawns
     */
    public long getSpawnSuccesses() {
        return spawnSuccesses.get();
    }

    /**
     * Get the total number of failed spawns since server start.
     *
     * @return Total failed spawns
     */
    public long getSpawnFailures() {
        return spawnFailures.get();
    }

    /**
     * Get the spawn success rate as a percentage.
     *
     * @return Success rate 0-100, or 100.0 if no attempts
     */
    public double getSpawnSuccessRate() {
        long attempts = spawnAttempts.get();
        if (attempts == 0) {
            return 100.0; // No attempts = default to 100%
        }
        return (spawnSuccesses.get() * 100.0) / attempts;
    }

    /**
     * Check if bStats metrics are enabled.
     *
     * @return true if bStats is active
     */
    public boolean isEnabled() {
        return metrics != null;
    }
}
