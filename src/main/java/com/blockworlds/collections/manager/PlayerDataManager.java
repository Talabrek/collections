package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.storage.Storage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
     * Get player progress, blocking if load is in progress.
     * Fast path: returns instantly if data is cached.
     * Slow path: waits up to 5 seconds if async load is pending.
     *
     * @param playerId The player's UUID
     * @return The player's progress, or null if not loaded/timed out
     */
    public PlayerProgress getProgressBlocking(UUID playerId) {
        // Fast path: check cache first - no blocking if already loaded
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Check if load is pending
        CompletableFuture<PlayerProgress> pending = pendingLoads.get(playerId);
        if (pending != null) {
            try {
                // Block-wait for pending load to complete (up to 5 seconds)
                return pending.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Timed out waiting for player data load: " + playerId);
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error waiting for player data load: " + playerId, e.getCause());
                return null;
            }
        }

        // Not cached and not pending - player data never loaded
        return null;
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
                        plugin.getLogger().log(Level.SEVERE,
                                "CRITICAL: Failed to persist collected item '" + itemId +
                                "' for collection '" + collectionId + "' for player " + playerId, throwable);
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
                    plugin.getLogger().log(Level.SEVERE,
                            "CRITICAL: Failed to persist collection completion for '" + collectionId +
                            "' for player " + playerId, throwable);
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
                    plugin.getLogger().log(Level.SEVERE,
                            "CRITICAL: Failed to persist reward claim for '" + collectionId +
                            "' for player " + playerId, throwable);
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

    // === Offline Player Operations for Admin Commands ===

    /**
     * Load player data by UUID without requiring a Player object.
     * If player is cached (online), returns cached version.
     * Otherwise loads from storage but does NOT cache (to avoid memory leaks for offline players).
     *
     * @param playerId The player's UUID
     * @return CompletableFuture containing the player's progress
     */
    public CompletableFuture<PlayerProgress> loadPlayerByUuid(UUID playerId) {
        // Check cache first (player is online)
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Check for pending load
        CompletableFuture<PlayerProgress> pending = pendingLoads.get(playerId);
        if (pending != null) {
            return pending;
        }

        // Load from storage without caching (offline player)
        return storage.loadPlayer(playerId)
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to load offline player data for " + playerId, throwable);
                    // Return new progress on failure
                    return new PlayerProgress(playerId);
                });
    }

    /**
     * Get progress snapshot for offline player inspection.
     * If player is cached (online), returns cached immediately.
     * Otherwise loads from storage (does not persist to cache).
     *
     * @param playerId The player's UUID
     * @return CompletableFuture containing the player's progress
     */
    public CompletableFuture<PlayerProgress> getProgressOffline(UUID playerId) {
        return loadPlayerByUuid(playerId);
    }

    /**
     * Add an item to an offline player's collection.
     * Uses load-then-modify-then-save pattern for offline players.
     * If player is cached (online), delegates to existing addItem().
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param itemId       The item ID
     * @return CompletableFuture with true if item was newly added, false if already had it
     */
    public CompletableFuture<Boolean> addItemOffline(UUID playerId, String collectionId, String itemId) {
        // Check cache first (player is online)
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            // Use existing sync method for online players
            boolean added = addItem(playerId, collectionId, itemId);
            return CompletableFuture.completedFuture(added);
        }

        // Offline player: load -> modify -> save pattern
        return storage.loadPlayer(playerId)
                .orTimeout(30, TimeUnit.SECONDS)
                .thenCompose(progress -> {
                    boolean added = progress.addItem(collectionId, itemId);
                    if (added) {
                        // Save the modified progress
                        return storage.savePlayer(progress)
                                .orTimeout(30, TimeUnit.SECONDS)
                                .thenApply(v -> true);
                    }
                    // Item already existed, no need to save
                    return CompletableFuture.completedFuture(false);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to add item offline for player " + playerId, throwable);
                    return false;
                });
    }

    /**
     * Force-complete a collection for an offline player.
     * Adds all specified items and marks the collection complete.
     * If player is cached (online), modifies cache directly then persists.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param itemIds      The item IDs to add
     * @return CompletableFuture that completes when operation is done
     */
    public CompletableFuture<Void> completeCollectionOffline(UUID playerId, String collectionId, List<String> itemIds) {
        // Check cache first (player is online)
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            // Modify cache directly for online players
            for (String itemId : itemIds) {
                cached.addItem(collectionId, itemId);
            }
            cached.markComplete(collectionId);
            // Persist the full progress
            return storage.savePlayer(cached)
                    .orTimeout(30, TimeUnit.SECONDS)
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.SEVERE,
                                "Failed to persist force-complete for player " + playerId, throwable);
                        return null;
                    });
        }

        // Offline player: load -> modify -> save pattern
        return storage.loadPlayer(playerId)
                .orTimeout(30, TimeUnit.SECONDS)
                .thenCompose(progress -> {
                    // Add all items
                    for (String itemId : itemIds) {
                        progress.addItem(collectionId, itemId);
                    }
                    // Mark complete
                    progress.markComplete(collectionId);
                    // Save the modified progress
                    return storage.savePlayer(progress)
                            .orTimeout(30, TimeUnit.SECONDS);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to complete collection offline for player " + playerId, throwable);
                    return null;
                });
    }

    // === Cache Invalidation ===

    /**
     * Invalidate cache for a player and reload from database.
     * Used after import to ensure online players see updated data immediately.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when reload is done
     */
    public CompletableFuture<Void> invalidateCacheAndReload(UUID playerId) {
        // Remove stale cache entry
        cache.remove(playerId);
        pendingLoads.remove(playerId);

        // Check if player is online
        Player onlinePlayer = org.bukkit.Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            // Reload from database
            return loadPlayer(onlinePlayer).thenAccept(progress -> {
                plugin.getLogger().fine("Reloaded cache for online player: " + playerId);
            });
        }

        return CompletableFuture.completedFuture(null);
    }

    // === Admin Action Logging ===

    /**
     * Log an admin action for audit trail.
     * Format: [ADMIN] {action} executed by {executor} on player {target}: {details}
     *
     * @param action     The action type (INSPECT, FORCE_COMPLETE, RESET, etc.)
     * @param executor   The command executor name (player name or "CONSOLE")
     * @param targetId   The target player's UUID
     * @param targetName The target player's name (may be null for never-joined)
     * @param details    Additional details about the action
     */
    public void logAdminAction(String action, String executor, UUID targetId, String targetName, String details) {
        String target = targetName != null ? targetName : targetId.toString();
        plugin.getLogger().info(String.format(
                "[ADMIN] %s executed by %s on player %s: %s",
                action, executor, target, details
        ));
    }
}
