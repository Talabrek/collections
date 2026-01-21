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

import java.util.UUID;
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
    private MockStorage storage;
    private PlayerDataManager manager;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        storage = new MockStorage();
        manager = new PlayerDataManager(plugin, storage);
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

        assertFalse(manager.isLoaded(playerId), "Player should not be loaded initially");

        PlayerProgress progress = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertNotNull(progress, "Progress should not be null");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
        assertTrue(manager.isLoaded(playerId), "Player should be loaded after loadPlayer");
        assertEquals(1, storage.getLoadCount(), "Storage should have been called once");
    }

    @Test
    @DisplayName("loadPlayer returns cached data on second call")
    void testLoadPlayerReturnsCachedOnSecondCall() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        PlayerProgress first = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress second = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertSame(first, second, "Second call should return same cached instance");
        assertEquals(1, storage.getLoadCount(), "Storage should only be called once");
    }

    @Test
    @DisplayName("getProgress returns null before load")
    void testGetProgressReturnsNullBeforeLoad() {
        UUID playerId = UUID.randomUUID();

        PlayerProgress progress = manager.getProgress(playerId);

        assertNull(progress, "getProgress should return null for unloaded player");
    }

    @Test
    @DisplayName("getProgress returns data after load")
    void testGetProgressReturnsDataAfterLoad() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress progress = manager.getProgress(playerId);

        assertNotNull(progress, "getProgress should return data after load");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
    }

    @Test
    @DisplayName("isLoaded reflects cache state")
    void testIsLoadedReflectsCacheState() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        assertFalse(manager.isLoaded(playerId), "Initially not loaded");

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertTrue(manager.isLoaded(playerId), "Loaded after loadPlayer");

        manager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);
        assertFalse(manager.isLoaded(playerId), "Not loaded after saveAndUnload");
    }

    // ===== Save and Unload Tests =====

    @Test
    @DisplayName("savePlayer calls storage")
    void testSavePlayerCallsStorage() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertEquals(0, storage.getSaveCount(), "No saves before savePlayer");

        manager.savePlayer(playerId).get(5, TimeUnit.SECONDS);

        assertEquals(1, storage.getSaveCount(), "Storage.savePlayer should be called");
        assertTrue(manager.isLoaded(playerId), "Player should still be in cache after save");
    }

    @Test
    @DisplayName("saveAndUnload removes from cache")
    void testSaveAndUnloadRemovesFromCache() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertTrue(manager.isLoaded(playerId), "Player should be loaded");

        manager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);

        assertFalse(manager.isLoaded(playerId), "Player should be unloaded after saveAndUnload");
        assertNull(manager.getProgress(playerId), "getProgress should return null after unload");
        assertEquals(1, storage.getSaveCount(), "Storage.savePlayer should be called");
    }

    @Test
    @DisplayName("clearCache removes all data")
    void testClearCacheRemovesAllData() throws Exception {
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        Player player1 = createMockPlayer(playerId1);
        Player player2 = createMockPlayer(playerId2);

        manager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        manager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        assertEquals(2, manager.getCacheSize(), "Two players should be cached");

        manager.clearCache();

        assertEquals(0, manager.getCacheSize(), "Cache should be empty after clear");
        assertFalse(manager.isLoaded(playerId1), "Player 1 should not be loaded");
        assertFalse(manager.isLoaded(playerId2), "Player 2 should not be loaded");
    }

    // ===== Lifecycle Tests =====

    @Test
    @DisplayName("getCacheSize reflects loaded players")
    void testGetCacheSizeReflectsLoadedPlayers() throws Exception {
        assertEquals(0, manager.getCacheSize(), "Initially empty");

        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        Player player1 = createMockPlayer(playerId1);
        Player player2 = createMockPlayer(playerId2);

        manager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        assertEquals(1, manager.getCacheSize(), "One player loaded");

        manager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        assertEquals(2, manager.getCacheSize(), "Two players loaded");

        manager.saveAndUnload(playerId1).get(5, TimeUnit.SECONDS);
        assertEquals(1, manager.getCacheSize(), "One player after unload");
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

        PlayerProgress progress1 = manager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        PlayerProgress progress2 = manager.loadPlayer(player2).get(5, TimeUnit.SECONDS);
        PlayerProgress progress3 = manager.loadPlayer(player3).get(5, TimeUnit.SECONDS);

        assertEquals(3, manager.getCacheSize(), "Three players should be cached");
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

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        boolean added = manager.addItem(playerId, "test_collection", "test_item");

        assertTrue(added, "Item should be newly added");
        assertTrue(manager.hasItem(playerId, "test_collection", "test_item"), "Cache should reflect item");
        // Note: saveCollectedItem is async, we check the call was made
        Thread.sleep(100); // Give async call time to complete
        assertEquals(1, storage.getSaveItemCount(), "Storage.saveCollectedItem should be called");
    }

    @Test
    @DisplayName("addItem returns false for duplicate")
    void testAddItemReturnsFalseForDuplicate() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        manager.addItem(playerId, "test_collection", "test_item");

        boolean addedAgain = manager.addItem(playerId, "test_collection", "test_item");

        assertFalse(addedAgain, "Duplicate item should return false");
    }

    @Test
    @DisplayName("hasItem returns correct value")
    void testHasItemReturnsCorrectValue() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(manager.hasItem(playerId, "col1", "item1"), "Should not have item initially");

        manager.addItem(playerId, "col1", "item1");

        assertTrue(manager.hasItem(playerId, "col1", "item1"), "Should have item after add");
        assertFalse(manager.hasItem(playerId, "col1", "item2"), "Should not have different item");
        assertFalse(manager.hasItem(playerId, "col2", "item1"), "Should not have item in different collection");
    }

    @Test
    @DisplayName("markComplete and hasCompleted work correctly")
    void testMarkCompleteAndHasCompleted() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(manager.hasCompleted(playerId, "test_collection"), "Not complete initially");

        manager.markComplete(playerId, "test_collection");

        assertTrue(manager.hasCompleted(playerId, "test_collection"), "Complete after markComplete");
        Thread.sleep(100); // Give async call time to complete
        assertEquals(1, storage.getUpdateStatusCount(), "Storage.updateCollectionStatus should be called");
    }

    @Test
    @DisplayName("claimReward and hasClaimedReward work correctly")
    void testClaimRewardAndHasClaimedReward() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertFalse(manager.hasClaimedReward(playerId, "test_collection"), "Not claimed initially");

        manager.claimReward(playerId, "test_collection");

        assertTrue(manager.hasClaimedReward(playerId, "test_collection"), "Claimed after claimReward");
    }

    // ===== Edge Cases =====

    @Test
    @DisplayName("Operations on unloaded player return correctly")
    void testOperationsOnUnloadedPlayer() {
        UUID playerId = UUID.randomUUID();

        assertFalse(manager.hasItem(playerId, "col", "item"), "hasItem returns false for unloaded");
        assertFalse(manager.hasCompleted(playerId, "col"), "hasCompleted returns false for unloaded");
        assertFalse(manager.hasClaimedReward(playerId, "col"), "hasClaimedReward returns false for unloaded");
        assertFalse(manager.addItem(playerId, "col", "item"), "addItem returns false for unloaded");
    }

    @Test
    @DisplayName("getProgressBlocking returns null for unloaded player")
    void testGetProgressBlockingReturnsNullForUnloaded() {
        UUID playerId = UUID.randomUUID();

        PlayerProgress progress = manager.getProgressBlocking(playerId);

        assertNull(progress, "getProgressBlocking should return null for unloaded player");
    }

    @Test
    @DisplayName("getProgressBlocking returns cached data")
    void testGetProgressBlockingReturnsCached() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress progress = manager.getProgressBlocking(playerId);

        assertNotNull(progress, "getProgressBlocking should return cached data");
        assertEquals(playerId, progress.getPlayerId(), "Progress should have correct player ID");
    }

    @Test
    @DisplayName("savePlayer for unloaded player returns immediately")
    void testSavePlayerForUnloadedReturnsImmediately() throws Exception {
        UUID playerId = UUID.randomUUID();

        // Should not throw and complete immediately
        manager.savePlayer(playerId).get(5, TimeUnit.SECONDS);

        assertEquals(0, storage.getSaveCount(), "Storage.savePlayer should not be called for unloaded player");
    }

    @Test
    @DisplayName("saveAll saves all cached players")
    void testSaveAllSavesAllCachedPlayers() throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = createMockPlayer(playerId);

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertEquals(1, manager.getCacheSize(), "One player cached");

        manager.saveAll().get(5, TimeUnit.SECONDS);

        assertEquals(1, storage.getSaveCount(), "Storage.savePlayer should be called for cached player");
    }
}
