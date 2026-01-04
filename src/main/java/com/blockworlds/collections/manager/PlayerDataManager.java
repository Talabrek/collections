package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Manages player collection data with async loading and caching.
 */
public class PlayerDataManager {

    private final Plugin plugin;
    private final Storage storage;
    private final Map<UUID, PlayerProgress> cache;
    private final Map<UUID, CompletableFuture<PlayerProgress>> pendingLoads;

    public PlayerDataManager(Plugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.cache = new ConcurrentHashMap<>();
        this.pendingLoads = new ConcurrentHashMap<>();
    }

    /**
     * Load player data asynchronously. Called on player join.
     *
     * @param player The player to load data for
     * @return CompletableFuture containing the player's progress
     */
    public CompletableFuture<PlayerProgress> loadPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        // Check cache first
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Use computeIfAbsent for atomic pending load handling
        // This prevents race conditions where load completes before put()
        return pendingLoads.computeIfAbsent(playerId, id -> {
            CompletableFuture<PlayerProgress> future = storage.loadPlayer(id)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenApply(progress -> {
                        cache.put(id, progress);
                        pendingLoads.remove(id);
                        return progress;
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to load player data for " + id, throwable);
                        pendingLoads.remove(id);
                        // Return new progress on failure
                        PlayerProgress newProgress = new PlayerProgress(id);
                        cache.put(id, newProgress);
                        return newProgress;
                    });
            return future;
        });
    }

    /**
     * Get cached player progress, or null if not loaded.
     *
     * @param playerId The player's UUID
     * @return The player's progress, or null if not cached
     */
    public PlayerProgress getProgress(UUID playerId) {
        return cache.get(playerId);
    }

    /**
     * Get cached player progress, loading if necessary.
     * This is a blocking operation if data isn't cached.
     *
     * @param player The player
     * @return The player's progress
     */
    public PlayerProgress getProgressOrLoad(Player player) {
        PlayerProgress cached = cache.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }

        try {
            return loadPlayer(player).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load player data synchronously for " + player.getUniqueId(), e);
            return new PlayerProgress(player.getUniqueId());
        }
    }

    /**
     * Save player data asynchronously. Called on player quit.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when save is done
     */
    public CompletableFuture<Void> savePlayer(UUID playerId) {
        PlayerProgress progress = cache.get(playerId);
        if (progress == null) {
            return CompletableFuture.completedFuture(null);
        }

        return storage.savePlayer(progress)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to save player data for " + playerId, throwable);
                    return null;
                });
    }

    /**
     * Save and unload player data. Called on player quit.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when save is done
     */
    public CompletableFuture<Void> saveAndUnload(UUID playerId) {
        return savePlayer(playerId)
                .thenRun(() -> {
                    cache.remove(playerId);
                    pendingLoads.remove(playerId);
                });
    }

    /**
     * Add an item to a player's collection.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param itemId       The item ID
     * @return true if the item was newly added, false if already had it
     */
    public boolean addItem(UUID playerId, String collectionId, String itemId) {
        PlayerProgress progress = cache.get(playerId);
        if (progress == null) {
            return false;
        }

        boolean added = progress.addItem(collectionId, itemId);
        if (added) {
            // Persist immediately
            storage.saveCollectedItem(playerId, collectionId, itemId)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to save collected item for " + playerId, throwable);
                        return null;
                    });
        }

        return added;
    }

    /**
     * Mark a collection as complete for a player.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     */
    public void markComplete(UUID playerId, String collectionId) {
        PlayerProgress progress = cache.get(playerId);
        if (progress == null) {
            return;
        }

        progress.markComplete(collectionId);

        // Persist immediately
        storage.updateCollectionStatus(playerId, collectionId, true, false)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to save collection completion for " + playerId, throwable);
                    return null;
                });
    }

    /**
     * Mark rewards as claimed for a collection.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     */
    public void claimReward(UUID playerId, String collectionId) {
        PlayerProgress progress = cache.get(playerId);
        if (progress == null) {
            return;
        }

        progress.claimReward(collectionId);

        // Persist immediately
        storage.updateCollectionStatus(playerId, collectionId,
                        progress.hasCompleted(collectionId), true)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to save reward claim for " + playerId, throwable);
                    return null;
                });
    }

    /**
     * Check if a player has collected a specific item.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param itemId       The item ID
     * @return true if the player has the item in their journal
     */
    public boolean hasItem(UUID playerId, String collectionId, String itemId) {
        PlayerProgress progress = cache.get(playerId);
        return progress != null && progress.hasItem(collectionId, itemId);
    }

    /**
     * Check if a player has completed a collection.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @return true if the collection is complete
     */
    public boolean hasCompleted(UUID playerId, String collectionId) {
        PlayerProgress progress = cache.get(playerId);
        return progress != null && progress.hasCompleted(collectionId);
    }

    /**
     * Check if a player has claimed rewards for a collection.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @return true if rewards have been claimed
     */
    public boolean hasClaimedReward(UUID playerId, String collectionId) {
        PlayerProgress progress = cache.get(playerId);
        return progress != null && progress.hasClaimedReward(collectionId);
    }

    /**
     * Save all cached player data. Called on plugin disable.
     *
     * @return CompletableFuture that completes when all saves are done
     */
    public CompletableFuture<Void> saveAll() {
        if (cache.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<?>[] futures = cache.keySet().stream()
                .map(this::savePlayer)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .orTimeout(60, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to save all player data on shutdown", throwable);
                    return null;
                });
    }

    /**
     * Get the number of cached players.
     *
     * @return The cache size
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Check if a player's data is loaded.
     *
     * @param playerId The player's UUID
     * @return true if the player's data is cached
     */
    public boolean isLoaded(UUID playerId) {
        return cache.containsKey(playerId);
    }

    /**
     * Clear all cached data. Used for testing and reload.
     */
    public void clearCache() {
        cache.clear();
        pendingLoads.clear();
    }

    /**
     * Reset all progress for a player.
     *
     * @param playerId The player's UUID
     */
    public void resetPlayer(UUID playerId) {
        // Remove from cache
        cache.remove(playerId);
        pendingLoads.remove(playerId);

        // Reset in storage
        storage.resetPlayer(playerId)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to reset player data for " + playerId, throwable);
                    return null;
                });
    }

    /**
     * Reset progress for a specific collection for a player.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID to reset
     */
    public void resetCollection(UUID playerId, String collectionId) {
        // Update cache if present
        PlayerProgress progress = cache.get(playerId);
        if (progress != null) {
            progress.resetCollection(collectionId);
        }

        // Reset in storage
        storage.resetPlayerCollection(playerId, collectionId)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to reset collection for " + playerId, throwable);
                    return null;
                });
    }
}
