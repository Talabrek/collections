package com.blockworlds.collections.metrics;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.storage.Storage;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private final Storage storage;
    private Metrics metrics;
    private ScheduledTask saveTask;

    /**
     * Create a new MetricsManager.
     *
     * @param plugin The plugin instance
     */
    public MetricsManager(Collections plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();

        boolean metricsEnabled = plugin.getConfigManager().getBoolean("metrics.enabled", true);
        if (metricsEnabled) {
            int pluginId = plugin.getConfigManager().getInt("metrics.bstats-id", DEFAULT_BSTATS_ID);
            this.metrics = new Metrics(plugin, pluginId);
            setupCustomCharts();
            plugin.getLogger().info("bStats metrics enabled with plugin ID: " + pluginId);
        } else {
            plugin.getLogger().info("bStats metrics disabled in config");
        }

        // Load persisted counters from database
        loadCounters();

        // Start periodic save task (every 5 minutes)
        startPeriodicSave();
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

    // ========== Persistence Methods ==========

    /**
     * Load counter values from database.
     * Called during construction to restore state from previous session.
     */
    private void loadCounters() {
        storage.getAllMetrics().thenAccept(metrics -> {
            itemsCollected.set(metrics.getOrDefault("items_collected", 0L));
            collectionsCompleted.set(metrics.getOrDefault("collections_completed", 0L));
            spawnAttempts.set(metrics.getOrDefault("spawn_attempts", 0L));
            spawnSuccesses.set(metrics.getOrDefault("spawn_successes", 0L));
            spawnFailures.set(metrics.getOrDefault("spawn_failures", 0L));
            plugin.getLogger().info("Loaded metrics counters from database");
        }).exceptionally(e -> {
            plugin.getLogger().warning("Failed to load metrics counters: " + e.getMessage());
            // Counters start at 0, which is fine as default
            return null;
        });
    }

    /**
     * Save all counter values to database.
     *
     * @return CompletableFuture that completes when all saves are done
     */
    public CompletableFuture<Void> saveCounters() {
        return CompletableFuture.allOf(
            storage.setMetric("items_collected", itemsCollected.get()),
            storage.setMetric("collections_completed", collectionsCompleted.get()),
            storage.setMetric("spawn_attempts", spawnAttempts.get()),
            storage.setMetric("spawn_successes", spawnSuccesses.get()),
            storage.setMetric("spawn_failures", spawnFailures.get())
        );
    }

    /**
     * Start periodic save task to protect against data loss from crashes.
     * Saves every 5 minutes.
     */
    private void startPeriodicSave() {
        // Save every 5 minutes (5 * 60 * 20 = 6000 ticks)
        int intervalTicks = 5 * 60 * 20;
        saveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            saveCounters();
        }, intervalTicks, intervalTicks);
    }

    /**
     * Shutdown the metrics manager.
     * Cancels periodic save task and performs final blocking save.
     */
    public void shutdown() {
        // Cancel periodic save task
        if (saveTask != null) {
            saveTask.cancel();
        }

        // Final blocking save to ensure counters are persisted
        try {
            saveCounters().get(10, TimeUnit.SECONDS);
            plugin.getLogger().info("Metrics counters saved to database");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save metrics counters on shutdown: " + e.getMessage());
        }
    }
}
