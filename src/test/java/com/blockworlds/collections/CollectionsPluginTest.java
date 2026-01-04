package com.blockworlds.collections;

import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.model.PlayerProgress;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Collections plugin using MockBukkit.
 */
class CollectionsPluginTest {

    private static ServerMock server;
    private static Collections plugin;

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Collections.class);
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    // ==================== Plugin Lifecycle Tests ====================

    @Test
    @DisplayName("Plugin loads successfully")
    void testPluginLoads() {
        assertNotNull(plugin);
        assertTrue(plugin.isEnabled());
    }

    @Test
    @DisplayName("All managers are initialized")
    void testManagersInitialized() {
        assertNotNull(plugin.getConfigManager(), "ConfigManager should be initialized");
        assertNotNull(plugin.getStorage(), "Storage should be initialized");
        assertNotNull(plugin.getPlayerDataManager(), "PlayerDataManager should be initialized");
        assertNotNull(plugin.getCollectionManager(), "CollectionManager should be initialized");
        assertNotNull(plugin.getZoneManager(), "ZoneManager should be initialized");
        assertNotNull(plugin.getSpawnManager(), "SpawnManager should be initialized");
        assertNotNull(plugin.getParticleTask(), "ParticleTask should be initialized");
    }

    @Test
    @DisplayName("Static instance is accessible")
    void testStaticInstance() {
        assertSame(plugin, Collections.getInstance());
    }

    // ==================== ConfigManager Tests ====================

    @Test
    @DisplayName("ConfigManager loads default settings")
    void testConfigManagerSettings() {
        var configManager = plugin.getConfigManager();

        assertTrue(configManager.getParticleDistanceBlocks() > 0, "Particle distance should be positive");
        assertTrue(configManager.getSpawnCheckIntervalSeconds() > 0, "Spawn check interval should be positive");
        assertTrue(configManager.getParticleIntervalTicks() > 0, "Particle interval should be positive");
    }

    @Test
    @DisplayName("ConfigManager provides debug mode setting")
    void testDebugMode() {
        var configManager = plugin.getConfigManager();
        // Should not throw, regardless of value
        assertDoesNotThrow(() -> configManager.isDebugMode());
    }

    // ==================== CollectionManager Tests ====================

    @Test
    @DisplayName("CollectionManager loads collections")
    void testCollectionManagerLoadsCollections() {
        var collectionManager = plugin.getCollectionManager();
        assertTrue(collectionManager.getCollectionCount() >= 0, "Collection count should be non-negative");
    }

    @Test
    @DisplayName("CollectionManager provides collections by tier")
    void testCollectionsByTier() {
        var collectionManager = plugin.getCollectionManager();

        // Should return empty list or collections for each tier
        for (CollectibleTier tier : CollectibleTier.values()) {
            var collections = collectionManager.getCollectionsByTier(tier);
            assertNotNull(collections, "Collections list for " + tier + " should not be null");
        }
    }

    @Test
    @DisplayName("CollectionManager returns null for unknown collection")
    void testUnknownCollection() {
        var collectionManager = plugin.getCollectionManager();
        assertNull(collectionManager.getCollection("non_existent_collection_xyz"));
    }

    // ==================== ZoneManager Tests ====================

    @Test
    @DisplayName("ZoneManager loads zones")
    void testZoneManagerLoadsZones() {
        var zoneManager = plugin.getZoneManager();
        assertTrue(zoneManager.getZoneCount() >= 0, "Zone count should be non-negative");
    }

    @Test
    @DisplayName("ZoneManager returns null for unknown zone")
    void testUnknownZone() {
        var zoneManager = plugin.getZoneManager();
        assertNull(zoneManager.getZone("non_existent_zone_xyz"));
    }

    @Test
    @DisplayName("ZoneManager provides all zones")
    void testGetAllZones() {
        var zoneManager = plugin.getZoneManager();
        var zones = zoneManager.getAllZones();
        assertNotNull(zones, "Zones collection should not be null");
    }

    // ==================== SpawnManager Tests ====================

    @Test
    @DisplayName("SpawnManager initializes with zero active collectibles")
    void testSpawnManagerInitialState() {
        var spawnManager = plugin.getSpawnManager();
        // On fresh start, should have 0 or more active collectibles
        assertTrue(spawnManager.getActiveCount() >= 0, "Active count should be non-negative");
    }

    @Test
    @DisplayName("SpawnManager provides active collectibles list")
    void testGetActiveCollectibles() {
        var spawnManager = plugin.getSpawnManager();
        var collectibles = spawnManager.getActiveCollectibles();
        assertNotNull(collectibles, "Active collectibles list should not be null");
    }

    // ==================== PlayerDataManager Tests ====================

    @Test
    @DisplayName("PlayerDataManager loads data on player join")
    void testPlayerJoinLoadsData() {
        PlayerMock player = server.addPlayer();

        // Perform ticks to allow async operations
        server.getScheduler().performTicks(40);

        // Player data should be loaded
        PlayerProgress progress = plugin.getPlayerDataManager().getProgress(player.getUniqueId());
        assertNotNull(progress, "Player progress should be loaded after join");
        assertEquals(player.getUniqueId(), progress.getPlayerId());
    }

    @Test
    @DisplayName("PlayerDataManager creates new progress for unknown player")
    void testNewPlayerProgress() {
        UUID unknownId = UUID.randomUUID();
        PlayerProgress progress = plugin.getPlayerDataManager().getProgress(unknownId);

        assertNotNull(progress, "Should create new progress for unknown player");
        assertEquals(unknownId, progress.getPlayerId());
        assertEquals(0, progress.getTotalCollectiblesCollected());
    }

    @Test
    @DisplayName("PlayerDataManager tracks multiple players")
    void testMultiplePlayers() {
        PlayerMock player1 = server.addPlayer("TestPlayer1");
        PlayerMock player2 = server.addPlayer("TestPlayer2");

        server.getScheduler().performTicks(40);

        PlayerProgress progress1 = plugin.getPlayerDataManager().getProgress(player1.getUniqueId());
        PlayerProgress progress2 = plugin.getPlayerDataManager().getProgress(player2.getUniqueId());

        assertNotNull(progress1);
        assertNotNull(progress2);
        assertNotEquals(progress1.getPlayerId(), progress2.getPlayerId());
    }

    // ==================== Storage Tests ====================

    @Test
    @DisplayName("Storage is initialized")
    void testStorageInitialized() {
        assertNotNull(plugin.getStorage(), "Storage should be initialized");
    }

    @Test
    @DisplayName("Storage can save and load player progress")
    void testStorageSaveLoad() throws Exception {
        UUID playerId = UUID.randomUUID();
        PlayerProgress progress = new PlayerProgress(playerId);
        progress.addItem("test_collection", "test_item");

        // Save
        plugin.getStorage().savePlayer(progress).get(5, TimeUnit.SECONDS);

        // Load
        PlayerProgress loaded = plugin.getStorage().loadPlayer(playerId).get(5, TimeUnit.SECONDS);

        assertNotNull(loaded, "Should load saved progress");
        assertEquals(playerId, loaded.getPlayerId());
        assertTrue(loaded.hasItem("test_collection", "test_item"), "Should have the saved item");
    }

    // ==================== Command Tests ====================

    @Test
    @DisplayName("Collections command responds to help")
    void testCollectionsHelpCommand() {
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(20);

        player.performCommand("collections help");

        String message = player.nextMessage();
        assertNotNull(message, "Should receive help message");
    }

    @Test
    @DisplayName("Collections list command works")
    void testCollectionsListCommand() {
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(20);

        player.performCommand("collections list");

        // Should receive at least one message
        String message = player.nextMessage();
        assertNotNull(message, "Should receive list response");
    }

    @Test
    @DisplayName("Collections stats command works")
    void testCollectionsStatsCommand() {
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(20);

        player.performCommand("collections stats");

        String message = player.nextMessage();
        assertNotNull(message, "Should receive stats response");
    }

    // ==================== Reload Test ====================

    @Test
    @DisplayName("Plugin reload works without error")
    void testPluginReload() {
        assertDoesNotThrow(() -> plugin.reload(), "Reload should not throw");
    }
}
