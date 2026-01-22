package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.ExportFormat;
import com.blockworlds.collections.model.ImportResult;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.model.ValidationResult;
import com.blockworlds.collections.storage.Storage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataMigrationManager export/import operations.
 * Uses Mockito for mocking dependencies and temp directory for file operations.
 */
class DataMigrationManagerTest {

    @TempDir
    Path tempDir;

    private Collections plugin;
    private Storage storage;
    private PlayerDataManager playerDataManager;
    private CommandSender sender;
    private DataMigrationManager migrationManager;
    private Logger mockLogger;
    private PluginMeta pluginMeta;

    @BeforeEach
    void setUp() {
        // Mock plugin and dependencies
        plugin = mock(Collections.class);
        storage = mock(Storage.class);
        playerDataManager = mock(PlayerDataManager.class);
        sender = mock(CommandSender.class);
        mockLogger = mock(Logger.class);
        pluginMeta = mock(PluginMeta.class);

        // Configure plugin mocks
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(mockLogger);
        when(plugin.getPluginMeta()).thenReturn(pluginMeta);
        when(pluginMeta.getVersion()).thenReturn("1.0.0-TEST");
        when(sender.getName()).thenReturn("TestAdmin");

        // Create the migration manager
        migrationManager = new DataMigrationManager(plugin, storage, playerDataManager);
    }

    // ========== Export Tests ==========

    @Nested
    @DisplayName("Export Single Player Tests")
    class ExportSinglePlayerTests {

        @Test
        @DisplayName("exportPlayer creates valid JSON file")
        void testExportSinglePlayer_createsValidJson() throws Exception {
            // Setup: Create test player progress
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = createTestPlayerProgress(playerId);

            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Execute
            DataMigrationManager.ExportResult result = migrationManager.exportPlayer(playerId, sender)
                    .get(5, TimeUnit.SECONDS);

            // Verify result
            assertTrue(result.success(), "Export should succeed");
            assertEquals(1, result.playerCount(), "Should export 1 player");
            assertNotNull(result.filePath(), "File path should not be null");
            assertTrue(Files.exists(result.filePath()), "Export file should exist");

            // Verify JSON structure
            String jsonContent = Files.readString(result.filePath());
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();

            assertEquals(ExportFormat.FORMAT_VERSION, json.get("formatVersion").getAsInt());
            assertNotNull(json.get("exportDate"));
            assertEquals("1.0.0-TEST", json.get("pluginVersion").getAsString());
            assertEquals("SINGLE", json.get("exportType").getAsString());

            JsonArray players = json.getAsJsonArray("players");
            assertEquals(1, players.size(), "Should have 1 player");

            JsonObject playerJson = players.get(0).getAsJsonObject();
            assertEquals(playerId.toString(), playerJson.get("uuid").getAsString());
        }

        @Test
        @DisplayName("exportPlayer includes all collections")
        void testExportSinglePlayer_includesAllCollections() throws Exception {
            // Setup: Create player with multiple collections
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = new PlayerProgress(playerId);

            // Add complete collection
            PlayerProgress.CollectionProgress col1 = progress.getProgress("forest_specimens");
            col1.addItemDirect("acorn");
            col1.addItemDirect("maple_leaf");
            col1.setComplete(true);
            col1.setRewardClaimed(true);
            col1.setCompletedDate(1705939200000L);

            // Add partial collection
            PlayerProgress.CollectionProgress col2 = progress.getProgress("ocean_treasures");
            col2.addItemDirect("pearl");

            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Execute
            DataMigrationManager.ExportResult result = migrationManager.exportPlayer(playerId, sender)
                    .get(5, TimeUnit.SECONDS);

            // Verify JSON content
            String jsonContent = Files.readString(result.filePath());
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject playerJson = json.getAsJsonArray("players").get(0).getAsJsonObject();
            JsonObject collections = playerJson.getAsJsonObject("collections");

            // Check complete collection
            JsonObject forestCol = collections.getAsJsonObject("forest_specimens");
            JsonArray forestItems = forestCol.getAsJsonArray("items");
            assertEquals(2, forestItems.size());
            assertTrue(forestCol.get("complete").getAsBoolean());
            assertTrue(forestCol.get("rewardClaimed").getAsBoolean());
            assertEquals(1705939200000L, forestCol.get("completedDate").getAsLong());

            // Check partial collection
            JsonObject oceanCol = collections.getAsJsonObject("ocean_treasures");
            JsonArray oceanItems = oceanCol.getAsJsonArray("items");
            assertEquals(1, oceanItems.size());
            assertFalse(oceanCol.get("complete").getAsBoolean());
            assertFalse(oceanCol.get("rewardClaimed").getAsBoolean());
        }

        @Test
        @DisplayName("exportPlayer logs admin action")
        void testExportSinglePlayer_logsAdminAction() throws Exception {
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = new PlayerProgress(playerId);
            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            migrationManager.exportPlayer(playerId, sender).get(5, TimeUnit.SECONDS);

            verify(mockLogger).info(argThat((String msg) ->
                    msg.contains("[EXPORT]") && msg.contains("SINGLE") && msg.contains("TestAdmin")));
        }
    }

    @Nested
    @DisplayName("Export All Players Tests")
    class ExportAllPlayersTests {

        @Test
        @DisplayName("exportAllPlayers creates exports directory if missing")
        void testExportAllPlayers_createsExportsDirectoryIfMissing() throws Exception {
            // Setup: 1 player
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = new PlayerProgress(playerId);

            when(storage.getAllPlayerUuids()).thenReturn(CompletableFuture.completedFuture(List.of(playerId)));
            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Verify directory doesn't exist yet
            Path exportsDir = tempDir.resolve("exports");
            assertFalse(Files.exists(exportsDir), "Exports directory should not exist initially");

            // Execute
            DataMigrationManager.ExportResult result = migrationManager.exportAllPlayers(sender)
                    .get(5, TimeUnit.SECONDS);

            // Verify directory was created
            assertTrue(Files.exists(exportsDir), "Exports directory should be created");
            assertTrue(result.success(), "Export should succeed");
            assertTrue(Files.exists(result.filePath()), "Export file should exist");
        }

        @Test
        @DisplayName("exportAllPlayers streams without loading all at once")
        void testExportAllPlayers_streamingDoesNotLoadAllAtOnce() throws Exception {
            // Setup: 5 players
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            UUID id4 = UUID.randomUUID();
            UUID id5 = UUID.randomUUID();
            List<UUID> playerIds = List.of(id1, id2, id3, id4, id5);

            when(storage.getAllPlayerUuids()).thenReturn(CompletableFuture.completedFuture(playerIds));

            // Each loadPlayer returns a unique progress
            for (UUID id : playerIds) {
                when(storage.loadPlayer(id)).thenReturn(
                        CompletableFuture.completedFuture(new PlayerProgress(id)));
            }

            // Execute
            DataMigrationManager.ExportResult result = migrationManager.exportAllPlayers(sender)
                    .get(5, TimeUnit.SECONDS);

            // Verify: loadPlayer called once per UUID (streaming behavior)
            for (UUID id : playerIds) {
                verify(storage, times(1)).loadPlayer(id);
            }

            // Verify JSON contains all 5 players
            String jsonContent = Files.readString(result.filePath());
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonArray players = json.getAsJsonArray("players");
            assertEquals(5, players.size(), "Should have 5 players in export");
            assertEquals(5, json.get("totalPlayers").getAsInt());
        }

        @Test
        @DisplayName("exportAllPlayers handles empty database")
        void testExportEmptyDatabase_returnsFailure() throws Exception {
            when(storage.getAllPlayerUuids()).thenReturn(CompletableFuture.completedFuture(List.of()));

            DataMigrationManager.ExportResult result = migrationManager.exportAllPlayers(sender)
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.success(), "Export should fail for empty database");
            assertEquals("No player data found", result.errorMessage());
        }
    }

    // ========== Helper Methods ==========

    private PlayerProgress createTestPlayerProgress(UUID playerId) {
        PlayerProgress progress = new PlayerProgress(playerId);
        progress.setTotalCollectiblesCollected(42);
        progress.setTotalCollectionsCompleted(3);
        progress.setFirstCollectionDate(1705852800000L);
        progress.setLastActivityDate(1705939200000L);

        PlayerProgress.CollectionProgress colProgress = progress.getProgress("test_collection");
        colProgress.addItemDirect("item1");
        colProgress.addItemDirect("item2");

        return progress;
    }
}
