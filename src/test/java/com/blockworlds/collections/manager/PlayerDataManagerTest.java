package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.storage.MockStorage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayerDataManager lifecycle and cache behavior.
 *
 * Uses Mockito for Player mocking to avoid MockBukkit's event system
 * which can interfere with the async load completion timing.
 */
class PlayerDataManagerTest {

    private ServerMock server;
    private Plugin plugin;
    private MockStorage mockStorage;
    private PlayerDataManager playerDataManager;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        mockStorage = new MockStorage();
        playerDataManager = new PlayerDataManager(plugin, mockStorage);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Create a mock player with a specific UUID.
     * Uses Mockito to avoid MockBukkit's event triggering.
     */
    private Player createMockPlayer(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    // ===== Cache Behavior Tests =====

    @Test
    @DisplayName("loadPlayer caches data after loading")
    void testLoadPlayerCachesData() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        assertFalse(playerDataManager.isLoaded(playerId), "Player should not be loaded initially");

        PlayerProgress progress = playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertNotNull(progress, "Progress should not be null");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
        assertTrue(playerDataManager.isLoaded(playerId), "Player should be loaded after loadPlayer");
        assertEquals(1, mockStorage.getLoadCount(), "Storage should have been called once");
    }

    @Test
    @DisplayName("loadPlayer returns cached data on second call")
    void testLoadPlayerReturnsCachedOnSecondCall() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        PlayerProgress first = playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress second = playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertSame(first, second, "Second call should return same cached instance");
        assertEquals(1, mockStorage.getLoadCount(), "Storage should only be called once");
    }

    @Test
    @DisplayName("getProgress returns null before load")
    void testGetProgressReturnsNullBeforeLoad() {
        UUID playerId = UUID.randomUUID();

        PlayerProgress progress = playerDataManager.getProgress(playerId);

        assertNull(progress, "getProgress should return null for unloaded player");
    }

    @Test
    @DisplayName("getProgress returns data after load")
    void testGetProgressReturnsDataAfterLoad() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress progress = playerDataManager.getProgress(playerId);

        assertNotNull(progress, "getProgress should return data after load");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
    }

    @Test
    @DisplayName("isLoaded reflects cache state")
    void testIsLoadedReflectsCacheState() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        assertFalse(playerDataManager.isLoaded(playerId), "Initially not loaded");

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertTrue(playerDataManager.isLoaded(playerId), "Loaded after loadPlayer");

        playerDataManager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);
        assertFalse(playerDataManager.isLoaded(playerId), "Not loaded after saveAndUnload");
    }

    // ===== Save and Unload Tests =====

    @Test
    @DisplayName("savePlayer calls storage")
    void testSavePlayerCallsStorage() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertEquals(0, mockStorage.getSaveCount(), "No saves before savePlayer");

        playerDataManager.savePlayer(playerId).get(5, TimeUnit.SECONDS);

        assertEquals(1, mockStorage.getSaveCount(), "Storage.savePlayer should be called");
        assertTrue(playerDataManager.isLoaded(playerId), "Player should still be in cache after save");
    }

    @Test
    @DisplayName("saveAndUnload removes from cache")
    void testSaveAndUnloadRemovesFromCache() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertTrue(playerDataManager.isLoaded(playerId), "Player should be loaded");

        playerDataManager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);

        assertFalse(playerDataManager.isLoaded(playerId), "Player should be unloaded after saveAndUnload");
        assertNull(playerDataManager.getProgress(playerId), "getProgress should return null after unload");
        assertEquals(1, mockStorage.getSaveCount(), "Storage.savePlayer should be called");
    }

    @Test
    @DisplayName("clearCache removes all data")
    void testClearCacheRemovesAllData() throws Exception {
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        Player player1 = createMockPlayer(playerId1);
        Player player2 = createMockPlayer(playerId2);

        playerDataManager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        playerDataManager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        assertEquals(2, playerDataManager.getCacheSize(), "Two players should be cached");

        playerDataManager.clearCache();

        assertEquals(0, playerDataManager.getCacheSize(), "Cache should be empty after clear");
        assertFalse(playerDataManager.isLoaded(playerId1), "Player 1 should not be loaded");
        assertFalse(playerDataManager.isLoaded(playerId2), "Player 2 should not be loaded");
    }

    // ===== Lifecycle Tests =====

    @Test
    @DisplayName("getCacheSize reflects loaded players")
    void testGetCacheSizeReflectsLoadedPlayers() throws Exception {
        assertEquals(0, playerDataManager.getCacheSize(), "Initially empty");

        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        Player player1 = createMockPlayer(playerId1);
        Player player2 = createMockPlayer(playerId2);

        playerDataManager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        assertEquals(1, playerDataManager.getCacheSize(), "One player loaded");

        playerDataManager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        assertEquals(2, playerDataManager.getCacheSize(), "Two players loaded");

        playerDataManager.saveAndUnload(playerId1).get(5, TimeUnit.SECONDS);
        assertEquals(1, playerDataManager.getCacheSize(), "One player after unload");
    }

    @Test
    @DisplayName("Multiple players cached separately")
    void testLoadMultiplePlayers() throws Exception {
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        UUID playerId3 = UUID.randomUUID();
        Player player1 = createMockPlayer(playerId1);
        Player player2 = createMockPlayer(playerId2);
        Player player3 = createMockPlayer(playerId3);

        PlayerProgress progress1 = playerDataManager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        PlayerProgress progress2 = playerDataManager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        PlayerProgress progress3 = playerDataManager.loadPlayer(player3).get(5, TimeUnit.SECONDS);

        assertEquals(3, playerDataManager.getCacheSize(), "Three players should be cached");
        assertNotSame(progress1, progress2, "Different players have different progress");
        assertNotSame(progress2, progress3, "Different players have different progress");

        assertEquals(playerId1, progress1.getPlayerId(), "Progress 1 has correct ID");
        assertEquals(playerId2, progress2.getPlayerId(), "Progress 2 has correct ID");
        assertEquals(playerId3, progress3.getPlayerId(), "Progress 3 has correct ID");
    }

    // ===== Item and Collection Management Tests =====

    @Test
    @DisplayName("addItem updates cache and persists")
    void testAddItemUpdatesAndPersists() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        boolean added = playerDataManager.addItem(playerId, "test_collection", "test_item");

        assertTrue(added, "Item should be newly added");
        assertTrue(playerDataManager.hasItem(playerId, "test_collection", "test_item"), "Cache should reflect item");
        // Note: saveCollectedItem is async, we check the call was made
        Thread.sleep(100); // Give async call time to complete
        assertEquals(1, mockStorage.getSaveItemCount(), "Storage.saveCollectedItem should be called");
    }

    @Test
    @DisplayName("addItem returns false for duplicate")
    void testAddItemReturnsFalseForDuplicate() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        playerDataManager.addItem(playerId, "test_collection", "test_item");

        boolean addedAgain = playerDataManager.addItem(playerId, "test_collection", "test_item");

        assertFalse(addedAgain, "Duplicate item should return false");
    }

    @Test
    @DisplayName("hasItem returns correct value")
    void testHasItemReturnsCorrectValue() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(playerDataManager.hasItem(playerId, "col1", "item1"), "Should not have item initially");

        playerDataManager.addItem(playerId, "col1", "item1");

        assertTrue(playerDataManager.hasItem(playerId, "col1", "item1"), "Should have item after add");
        assertFalse(playerDataManager.hasItem(playerId, "col1", "item2"), "Should not have different item");
        assertFalse(playerDataManager.hasItem(playerId, "col2", "item1"), "Should not have item in different collection");
    }

    @Test
    @DisplayName("markComplete and hasCompleted work correctly")
    void testMarkCompleteAndHasCompleted() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(playerDataManager.hasCompleted(playerId, "test_collection"), "Not complete initially");

        playerDataManager.markComplete(playerId, "test_collection");

        assertTrue(playerDataManager.hasCompleted(playerId, "test_collection"), "Complete after markComplete");
        Thread.sleep(100); // Give async call time to complete
        assertEquals(1, mockStorage.getUpdateStatusCount(), "Storage.updateCollectionStatus should be called");
    }

    @Test
    @DisplayName("claimReward and hasClaimedReward work correctly")
    void testClaimRewardAndHasClaimedReward() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(playerDataManager.hasClaimedReward(playerId, "test_collection"), "Not claimed initially");

        playerDataManager.claimReward(playerId, "test_collection");

        assertTrue(playerDataManager.hasClaimedReward(playerId, "test_collection"), "Claimed after claimReward");
    }

    // ===== Edge Cases =====

    @Test
    @DisplayName("Operations on unloaded player return correctly")
    void testOperationsOnUnloadedPlayer() {
        UUID playerId = UUID.randomUUID();

        assertFalse(playerDataManager.hasItem(playerId, "col", "item"), "hasItem returns false for unloaded");
        assertFalse(playerDataManager.hasCompleted(playerId, "col"), "hasCompleted returns false for unloaded");
        assertFalse(playerDataManager.hasClaimedReward(playerId, "col"), "hasClaimedReward returns false for unloaded");
        assertFalse(playerDataManager.addItem(playerId, "col", "item"), "addItem returns false for unloaded");
    }

    @Test
    @DisplayName("getProgressBlocking returns null for unloaded player")
    void testGetProgressBlockingReturnsNullForUnloaded() {
        UUID playerId = UUID.randomUUID();

        PlayerProgress progress = playerDataManager.getProgressBlocking(playerId);

        assertNull(progress, "getProgressBlocking should return null for unloaded player");
    }

    @Test
    @DisplayName("getProgressBlocking returns cached data")
    void testGetProgressBlockingReturnsCached() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress progress = playerDataManager.getProgressBlocking(playerId);

        assertNotNull(progress, "getProgressBlocking should return cached data");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
    }

    @Test
    @DisplayName("savePlayer for unloaded player returns immediately")
    void testSavePlayerForUnloadedReturnsImmediately() throws Exception {
        UUID playerId = UUID.randomUUID();

        // Should not throw and complete immediately
        playerDataManager.savePlayer(playerId).get(5, TimeUnit.SECONDS);

        assertEquals(0, mockStorage.getSaveCount(), "Storage.savePlayer should not be called for unloaded player");
    }

    @Test
    @DisplayName("saveAll saves all cached players")
    void testSaveAllSavesAllCachedPlayers() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertEquals(1, playerDataManager.getCacheSize(), "One player cached");

        playerDataManager.saveAll().get(5, TimeUnit.SECONDS);

        assertEquals(1, mockStorage.getSaveCount(), "Storage.savePlayer should be called for cached player");
    }

    // ===== Offline Player Operation Tests =====

    @Test
    @DisplayName("getProgressOffline loads from storage for offline player")
    void testGetProgressOffline_LoadsFromStorage() throws Exception {
        // Setup: Player NOT in cache (offline)
        UUID offlinePlayerId = UUID.randomUUID();

        // Execute: Load progress for offline player
        CompletableFuture<PlayerProgress> future = playerDataManager.getProgressOffline(offlinePlayerId);
        PlayerProgress progress = future.get(5, TimeUnit.SECONDS);

        // Verify: Progress was loaded from storage
        assertNotNull(progress);
        assertEquals(offlinePlayerId, progress.getPlayerId());

        // Verify: NOT cached (to prevent memory leaks)
        assertNull(playerDataManager.getProgress(offlinePlayerId));
    }

    @Test
    @DisplayName("getProgressOffline returns cached data for online player")
    void testGetProgressOffline_ReturnsCachedForOnlinePlayer() throws Exception {
        // Setup: Load player into cache (simulating online)
        UUID playerId = UUID.randomUUID();
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        playerDataManager.loadPlayer(mockPlayer).get(5, TimeUnit.SECONDS);

        // Verify: Player is now cached
        assertNotNull(playerDataManager.getProgress(playerId));

        // Execute: Get progress for "offline" (but actually cached) player
        CompletableFuture<PlayerProgress> future = playerDataManager.getProgressOffline(playerId);
        PlayerProgress progress = future.get(5, TimeUnit.SECONDS);

        // Verify: Returns cached version immediately
        assertNotNull(progress);
        assertSame(playerDataManager.getProgress(playerId), progress);
    }

    @Test
    @DisplayName("addItemOffline adds item to offline player via storage")
    void testAddItemOffline_AddsItemToOfflinePlayer() throws Exception {
        // Setup: Offline player (not in cache)
        UUID offlinePlayerId = UUID.randomUUID();
        String collectionId = "test_collection";
        String itemId = "test_item";

        // Execute: Add item to offline player
        CompletableFuture<Boolean> future = playerDataManager.addItemOffline(offlinePlayerId, collectionId, itemId);
        Boolean added = future.get(5, TimeUnit.SECONDS);

        // Verify: Item was added
        assertTrue(added);

        // Verify: Progress was persisted to storage (load and check)
        PlayerProgress reloaded = mockStorage.loadPlayer(offlinePlayerId).get(5, TimeUnit.SECONDS);
        assertTrue(reloaded.hasItem(collectionId, itemId));

        // Verify: NOT cached (offline player)
        assertNull(playerDataManager.getProgress(offlinePlayerId));
    }

    @Test
    @DisplayName("addItemOffline returns false for duplicate item")
    void testAddItemOffline_ReturnsFalseForDuplicateItem() throws Exception {
        // Setup: Offline player with existing item
        UUID offlinePlayerId = UUID.randomUUID();
        String collectionId = "test_collection";
        String itemId = "test_item";

        // Pre-add the item
        playerDataManager.addItemOffline(offlinePlayerId, collectionId, itemId).get(5, TimeUnit.SECONDS);

        // Execute: Try to add same item again
        CompletableFuture<Boolean> future = playerDataManager.addItemOffline(offlinePlayerId, collectionId, itemId);
        Boolean added = future.get(5, TimeUnit.SECONDS);

        // Verify: Returns false (duplicate)
        assertFalse(added);
    }

    @Test
    @DisplayName("addItemOffline uses cache for online player")
    void testAddItemOffline_UsesCache_ForOnlinePlayer() throws Exception {
        // Setup: Online player (in cache)
        UUID playerId = UUID.randomUUID();
        Player mockPlayer = mock(Player.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        playerDataManager.loadPlayer(mockPlayer).get(5, TimeUnit.SECONDS);

        String collectionId = "test_collection";
        String itemId = "test_item";

        // Execute: Add item (should use cache path)
        CompletableFuture<Boolean> future = playerDataManager.addItemOffline(playerId, collectionId, itemId);
        Boolean added = future.get(5, TimeUnit.SECONDS);

        // Verify: Item added
        assertTrue(added);

        // Verify: Cache was updated
        PlayerProgress cached = playerDataManager.getProgress(playerId);
        assertNotNull(cached);
        assertTrue(cached.hasItem(collectionId, itemId));
    }
}
