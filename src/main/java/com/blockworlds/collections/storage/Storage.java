package com.blockworlds.collections.storage;

import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.PlayerProgress;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for persistent storage of player data and active collectibles.
 */
public interface Storage {

    /**
     * Initialize the storage (create tables, etc).
     */
    void initialize();

    /**
     * Shutdown the storage connection.
     */
    void shutdown();

    // Player Data Operations

    /**
     * Load player progress data.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture containing the player's progress, or a new empty progress if none exists
     */
    CompletableFuture<PlayerProgress> loadPlayer(UUID playerId);

    /**
     * Save player progress data.
     *
     * @param progress The player's progress to save
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> savePlayer(PlayerProgress progress);

    /**
     * Save a single item collection to a player's progress.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param itemId       The item ID
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> saveCollectedItem(UUID playerId, String collectionId, String itemId);

    /**
     * Update collection completion status.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID
     * @param complete     Whether the collection is complete
     * @param rewardClaimed Whether rewards have been claimed
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> updateCollectionStatus(UUID playerId, String collectionId, boolean complete, boolean rewardClaimed);

    // Collectible Operations

    /**
     * Save an active collectible to the database.
     *
     * @param collectible The collectible to save
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> saveCollectible(Collectible collectible);

    /**
     * Remove a collectible from the database.
     *
     * @param collectibleId The collectible's UUID
     * @return CompletableFuture that completes when removal is done
     */
    CompletableFuture<Void> removeCollectible(UUID collectibleId);

    /**
     * Load all active collectibles.
     *
     * @return CompletableFuture containing all collectibles
     */
    CompletableFuture<List<Collectible>> loadAllCollectibles();

    /**
     * Load collectibles in a specific chunk.
     *
     * @param worldName The world name
     * @param chunkX    The chunk X coordinate
     * @param chunkZ    The chunk Z coordinate
     * @return CompletableFuture containing collectibles in the chunk
     */
    CompletableFuture<List<Collectible>> loadCollectiblesInChunk(String worldName, int chunkX, int chunkZ);

    /**
     * Clear all collectibles from the database.
     *
     * @return CompletableFuture that completes when clearing is done
     */
    CompletableFuture<Void> clearAllCollectibles();

    /**
     * Clear collectibles in a specific zone.
     *
     * @param zoneId The zone ID
     * @return CompletableFuture that completes when clearing is done
     */
    CompletableFuture<Void> clearCollectiblesInZone(String zoneId);

    // Statistics

    /**
     * Get the total number of collectibles collected by all players.
     *
     * @return CompletableFuture containing the count
     */
    CompletableFuture<Integer> getTotalCollectiblesCollected();

    /**
     * Get the total number of collections completed by all players.
     *
     * @return CompletableFuture containing the count
     */
    CompletableFuture<Integer> getTotalCollectionsCompleted();

    /**
     * Backup player data before a destructive operation.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when backup is done
     */
    CompletableFuture<Void> backupPlayerData(UUID playerId);

    /**
     * Reset all progress for a player.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture that completes when reset is done
     */
    CompletableFuture<Void> resetPlayer(UUID playerId);

    /**
     * Reset progress for a specific collection for a player.
     *
     * @param playerId     The player's UUID
     * @param collectionId The collection ID to reset
     * @return CompletableFuture that completes when reset is done
     */
    CompletableFuture<Void> resetPlayerCollection(UUID playerId, String collectionId);
}
