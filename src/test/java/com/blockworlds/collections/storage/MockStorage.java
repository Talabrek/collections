package com.blockworlds.collections.storage;

import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.PlayerProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * In-memory mock implementation of Storage for testing.
 * Uses ConcurrentHashMap for thread-safe operations.
 *
 * Uses async execution to avoid recursive ConcurrentHashMap updates
 * when PlayerDataManager's computeIfAbsent completion callbacks execute.
 */
public class MockStorage implements Storage {

    private final Map<UUID, PlayerProgress> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Collectible> collectibles = new ConcurrentHashMap<>();
    private final Map<String, Long> metrics = new ConcurrentHashMap<>();

    // Executor for async operations - avoids recursive update in ConcurrentHashMap
    private final Executor executor = Executors.newCachedThreadPool();

    // Track method calls for verification
    private int loadCount = 0;
    private int saveCount = 0;
    private int saveItemCount = 0;
    private int updateStatusCount = 0;

    // Getters for verification
    public int getLoadCount() {
        return loadCount;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public int getSaveItemCount() {
        return saveItemCount;
    }

    public int getUpdateStatusCount() {
        return updateStatusCount;
    }

    /**
     * Reset all stored data and counters.
     */
    public void reset() {
        playerData.clear();
        collectibles.clear();
        loadCount = 0;
        saveCount = 0;
        saveItemCount = 0;
        updateStatusCount = 0;
    }

    /**
     * Pre-populate player data for testing.
     */
    public void setPlayerData(UUID playerId, PlayerProgress progress) {
        playerData.put(playerId, progress);
    }

    /**
     * Get stored player data directly for assertions.
     */
    public PlayerProgress getStoredPlayerData(UUID playerId) {
        return playerData.get(playerId);
    }

    @Override
    public void initialize() {
        // No-op for mock
    }

    @Override
    public void shutdown() {
        // No-op for mock
    }

    @Override
    public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
        loadCount++;
        // Use supplyAsync with a small delay to ensure the future doesn't complete
        // while still inside PlayerDataManager's computeIfAbsent operation
        // This avoids recursive ConcurrentHashMap update when completion callbacks execute
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Brief yield to ensure we exit computeIfAbsent before completing
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            PlayerProgress progress = playerData.get(playerId);
            if (progress == null) {
                progress = new PlayerProgress(playerId);
                playerData.put(playerId, progress);
            }
            return progress;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerProgress progress) {
        saveCount++;
        playerData.put(progress.getPlayerId(), progress);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveCollectedItem(UUID playerId, String collectionId, String itemId) {
        saveItemCount++;
        PlayerProgress progress = playerData.get(playerId);
        if (progress != null) {
            progress.addItem(collectionId, itemId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> updateCollectionStatus(UUID playerId, String collectionId,
                                                          boolean complete, boolean rewardClaimed) {
        updateStatusCount++;
        PlayerProgress progress = playerData.get(playerId);
        if (progress != null) {
            PlayerProgress.CollectionProgress colProgress = progress.getProgress(collectionId);
            colProgress.setComplete(complete);
            colProgress.setRewardClaimed(rewardClaimed);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveCollectible(Collectible collectible) {
        collectibles.put(collectible.id(), collectible);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeCollectible(UUID collectibleId) {
        collectibles.remove(collectibleId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Collectible>> loadAllCollectibles() {
        return CompletableFuture.completedFuture(new ArrayList<>(collectibles.values()));
    }

    @Override
    public CompletableFuture<List<Collectible>> loadCollectiblesInChunk(String worldName, int chunkX, int chunkZ) {
        List<Collectible> result = collectibles.values().stream()
                .filter(c -> worldName.equals(c.getWorldName())
                        && c.getChunkX() == chunkX
                        && c.getChunkZ() == chunkZ)
                .toList();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Void> clearAllCollectibles() {
        collectibles.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> clearCollectiblesInZone(String zoneId) {
        collectibles.entrySet().removeIf(entry -> zoneId.equals(entry.getValue().zoneId()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> getTotalCollectiblesCollected() {
        int total = playerData.values().stream()
                .mapToInt(PlayerProgress::getTotalCollectiblesCollected)
                .sum();
        return CompletableFuture.completedFuture(total);
    }

    @Override
    public CompletableFuture<Integer> getTotalCollectionsCompleted() {
        int total = playerData.values().stream()
                .mapToInt(PlayerProgress::getTotalCollectionsCompleted)
                .sum();
        return CompletableFuture.completedFuture(total);
    }

    @Override
    public CompletableFuture<Void> backupPlayerData(UUID playerId) {
        // No-op for mock - data is transient anyway
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> resetPlayer(UUID playerId) {
        playerData.remove(playerId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> resetPlayerCollection(UUID playerId, String collectionId) {
        PlayerProgress progress = playerData.get(playerId);
        if (progress != null) {
            progress.resetCollection(collectionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Long> getMetric(String key) {
        return CompletableFuture.completedFuture(metrics.getOrDefault(key, 0L));
    }

    @Override
    public CompletableFuture<Void> setMetric(String key, long value) {
        metrics.put(key, value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<String, Long>> getAllMetrics() {
        return CompletableFuture.completedFuture(new ConcurrentHashMap<>(metrics));
    }
}
