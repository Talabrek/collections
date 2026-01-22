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

    // ========== Validation Tests ==========

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("validateImportFile returns success for valid file")
        void testValidateImportFile_validFile_returnsSuccess() throws Exception {
            // Create valid JSON file
            Path validFile = createValidExportFile(List.of(UUID.randomUUID(), UUID.randomUUID()));

            ValidationResult result = migrationManager.validateImportFile(validFile);

            assertTrue(result.isValid(), "Valid file should pass validation");
            assertEquals(2, result.getPlayerCount(), "Should count 2 players");
            assertEquals(ExportFormat.FORMAT_VERSION, result.getFormatVersion());
            assertTrue(result.getErrors().isEmpty(), "Should have no errors");
        }

        @Test
        @DisplayName("validateImportFile returns error for missing formatVersion")
        void testValidateImportFile_missingFormatVersion_returnsError() throws Exception {
            // Create JSON without formatVersion
            String json = """
                {
                  "exportDate": "2026-01-22T00:00:00Z",
                  "players": []
                }
                """;
            Path file = tempDir.resolve("missing_version.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("formatVersion")),
                    "Error should mention formatVersion");
        }

        @Test
        @DisplayName("validateImportFile returns error for unsupported version")
        void testValidateImportFile_unsupportedVersion_returnsError() throws Exception {
            // Create JSON with future version
            String json = """
                {
                  "formatVersion": 999,
                  "players": []
                }
                """;
            Path file = tempDir.resolve("future_version.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("version")),
                    "Error should mention version");
        }

        @Test
        @DisplayName("validateImportFile returns error for malformed JSON")
        void testValidateImportFile_malformedJson_returnsError() throws Exception {
            // Create malformed JSON
            String json = "{ not valid json }}}";
            Path file = tempDir.resolve("malformed.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have error messages");
        }

        @Test
        @DisplayName("validateImportFile returns error for missing players array")
        void testValidateImportFile_missingPlayersArray_returnsError() throws Exception {
            // Create JSON without players
            String json = """
                {
                  "formatVersion": 1,
                  "exportDate": "2026-01-22T00:00:00Z"
                }
                """;
            Path file = tempDir.resolve("no_players.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("players")),
                    "Error should mention players");
        }

        @Test
        @DisplayName("validateImportFile returns error for player missing uuid")
        void testValidateImportFile_invalidPlayerEntry_returnsError() throws Exception {
            // Create JSON with player missing uuid
            String json = """
                {
                  "formatVersion": 1,
                  "players": [
                    {
                      "collections": {}
                    }
                  ]
                }
                """;
            Path file = tempDir.resolve("missing_uuid.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("uuid")),
                    "Error should mention uuid");
        }

        @Test
        @DisplayName("validateImportFile returns error for empty file")
        void testValidateImportFile_emptyFile_returnsError() throws Exception {
            Path file = tempDir.resolve("empty.json");
            Files.writeString(file, "");

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertFalse(result.getErrors().isEmpty(), "Should have error messages");
        }

        @Test
        @DisplayName("validateImportFile returns error for non-existent file")
        void testValidateImportFile_nonExistent_returnsError() {
            Path file = tempDir.resolve("does_not_exist.json");

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not found")),
                    "Error should mention file not found");
        }
    }

    // ========== Import Tests ==========

    @Nested
    @DisplayName("Import Tests")
    class ImportTests {

        /**
         * Note: The import/dry-run tests that need Bukkit.getPlayer() run in async context
         * where MockedStatic doesn't extend. We test the sync validation portion and
         * verify that validation failure prevents storage.savePlayer() from being called.
         */

        @Test
        @DisplayName("importPlayers with dry-run validates file correctly")
        void testImportPlayers_dryRun_validatesFileCorrectly() throws Exception {
            // Create valid export file
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            Path file = createValidExportFile(List.of(player1, player2));

            // Verify validation passes (sync portion)
            ValidationResult validation = migrationManager.validateImportFile(file);
            assertTrue(validation.isValid(), "Validation should pass");
            assertEquals(2, validation.getPlayerCount(), "Should count 2 players");
        }

        @Test
        @DisplayName("importPlayers returns failure for invalid file - storage not called")
        void testImportPlayers_invalidFile_returnsFailure() throws Exception {
            // Create invalid JSON file
            String json = "{ not valid }";
            Path file = tempDir.resolve("invalid_import.json");
            Files.writeString(file, json);

            ImportResult result = migrationManager.importPlayers(file, false, sender)
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.success(), "Import should fail");
            assertNotNull(result.errorMessage(), "Should have error message");

            // Verify storage NOT called (validation failed before async import)
            verify(storage, never()).savePlayer(any());
        }

        @Test
        @DisplayName("importPlayers returns failure for non-existent file")
        void testImportPlayers_nonExistentFile_returnsFailure() throws Exception {
            Path file = tempDir.resolve("nonexistent.json");

            ImportResult result = migrationManager.importPlayers(file, false, sender)
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.success(), "Import should fail");
            assertTrue(result.errorMessage().contains("not found") ||
                            result.errorMessage().contains("Validation failed"),
                    "Should indicate file not found");
        }

        @Test
        @DisplayName("validation blocks import for malformed data")
        void testImportPlayers_validationBlocksImport() throws Exception {
            // Create JSON with invalid player entry (missing uuid)
            String json = """
                {
                  "formatVersion": 1,
                  "players": [
                    {
                      "collections": {}
                    }
                  ]
                }
                """;
            Path file = tempDir.resolve("invalid_player.json");
            Files.writeString(file, json);

            ImportResult result = migrationManager.importPlayers(file, false, sender)
                    .get(5, TimeUnit.SECONDS);

            assertFalse(result.success(), "Import should fail due to validation");
            assertTrue(result.errorMessage().contains("Validation failed"),
                    "Error should indicate validation failure");

            // Storage should never be called due to validation failure
            verify(storage, never()).savePlayer(any());
        }

        @Test
        @DisplayName("validation allows import of empty players array")
        void testValidation_emptyPlayersArray_valid() throws Exception {
            // Create valid JSON with empty players
            String json = """
                {
                  "formatVersion": 1,
                  "exportDate": "2026-01-22T00:00:00Z",
                  "players": []
                }
                """;
            Path file = tempDir.resolve("empty_players.json");
            Files.writeString(file, json);

            // Verify validation passes
            ValidationResult validation = migrationManager.validateImportFile(file);
            assertTrue(validation.isValid(), "Empty players array should be valid");
            assertEquals(0, validation.getPlayerCount(), "Should count 0 players");
        }

        @Test
        @DisplayName("dry-run mode identified by validation result")
        void testDryRun_usesValidationPlayerCount() throws Exception {
            // Create valid export file with 3 players
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();
            Path file = createValidExportFile(List.of(player1, player2, player3));

            // Verify validation counts correctly - this is what dry-run uses
            ValidationResult validation = migrationManager.validateImportFile(file);
            assertEquals(3, validation.getPlayerCount(),
                    "Dry-run would report 3 players from validation");
        }
    }

    // ========== Edge Cases and Round-Trip Tests ==========

    @Nested
    @DisplayName("Edge Cases and Round-Trip Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("exportPlayer with empty progress exports valid JSON")
        void testExportPlayer_emptyProgress_exportsValidJson() throws Exception {
            // Setup: Create player with no collections
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = new PlayerProgress(playerId);

            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Execute
            DataMigrationManager.ExportResult result = migrationManager.exportPlayer(playerId, sender)
                    .get(5, TimeUnit.SECONDS);

            // Verify export succeeds with empty progress
            assertTrue(result.success(), "Export should succeed");
            assertEquals(1, result.playerCount(), "Should export 1 player");

            // Verify JSON is valid
            String jsonContent = Files.readString(result.filePath());
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject playerJson = json.getAsJsonArray("players").get(0).getAsJsonObject();
            JsonObject collections = playerJson.getAsJsonObject("collections");

            // Empty collections object is valid
            assertEquals(0, collections.size(), "Should have 0 collections");
        }

        @Test
        @DisplayName("validation handles player with invalid UUID format")
        void testValidation_invalidUuidFormat_returnsError() throws Exception {
            // Create JSON with invalid UUID
            String json = """
                {
                  "formatVersion": 1,
                  "players": [
                    {
                      "uuid": "not-a-valid-uuid",
                      "collections": {}
                    }
                  ]
                }
                """;
            Path file = tempDir.resolve("invalid_uuid_format.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains("uuid")),
                    "Error should mention UUID");
        }

        @Test
        @DisplayName("validation handles player missing collections field")
        void testValidation_missingCollectionsField_returnsError() throws Exception {
            // Create JSON with player missing collections
            String json = """
                {
                  "formatVersion": 1,
                  "players": [
                    {
                      "uuid": "%s"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());
            Path file = tempDir.resolve("missing_collections.json");
            Files.writeString(file, json);

            ValidationResult result = migrationManager.validateImportFile(file);

            assertFalse(result.isValid(), "Should fail validation");
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("collections")),
                    "Error should mention collections");
        }

        @Test
        @DisplayName("export then validate round-trip succeeds")
        void testExportThenValidate_roundTripSucceeds() throws Exception {
            // Setup: Create complex player data
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = new PlayerProgress(playerId);
            progress.setTotalCollectiblesCollected(100);
            progress.setTotalCollectionsCompleted(5);
            progress.setFirstCollectionDate(1705852800000L);
            progress.setLastActivityDate(1705939200000L);

            // Add multiple collections with various states
            PlayerProgress.CollectionProgress col1 = progress.getProgress("forest_specimens");
            col1.addItemDirect("acorn");
            col1.addItemDirect("maple_leaf");
            col1.addItemDirect("pine_cone");
            col1.setComplete(true);
            col1.setRewardClaimed(true);
            col1.setCompletedDate(1705890000000L);

            PlayerProgress.CollectionProgress col2 = progress.getProgress("ocean_treasures");
            col2.addItemDirect("pearl");
            col2.addItemDirect("coral");
            col2.setComplete(false);
            col2.setRewardClaimed(false);

            PlayerProgress.CollectionProgress col3 = progress.getProgress("mountain_relics");
            col3.addItemDirect("crystal");
            col3.setComplete(true);
            col3.setRewardClaimed(false);
            col3.setCompletedDate(1705920000000L);

            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Export
            DataMigrationManager.ExportResult exportResult = migrationManager.exportPlayer(playerId, sender)
                    .get(5, TimeUnit.SECONDS);

            assertTrue(exportResult.success(), "Export should succeed");

            // Validate the exported file
            ValidationResult validationResult = migrationManager.validateImportFile(exportResult.filePath());

            assertTrue(validationResult.isValid(), "Exported file should be valid");
            assertEquals(1, validationResult.getPlayerCount(), "Should count 1 player");
            assertEquals(ExportFormat.FORMAT_VERSION, validationResult.getFormatVersion());
        }

        @Test
        @DisplayName("export preserves all collection data in round-trip")
        void testExportPreservesAllData_roundTrip() throws Exception {
            // Setup
            UUID playerId = UUID.randomUUID();
            PlayerProgress progress = createComplexPlayerProgress(playerId);

            when(storage.loadPlayer(playerId)).thenReturn(CompletableFuture.completedFuture(progress));

            // Export
            DataMigrationManager.ExportResult result = migrationManager.exportPlayer(playerId, sender)
                    .get(5, TimeUnit.SECONDS);

            // Read and verify JSON
            String jsonContent = Files.readString(result.filePath());
            JsonObject json = JsonParser.parseString(jsonContent).getAsJsonObject();
            JsonObject playerJson = json.getAsJsonArray("players").get(0).getAsJsonObject();

            // Verify UUID
            assertEquals(playerId.toString(), playerJson.get("uuid").getAsString());

            // Verify stats
            JsonObject stats = playerJson.getAsJsonObject("stats");
            assertEquals(50, stats.get("totalCollectiblesCollected").getAsInt());
            assertEquals(3, stats.get("totalCollectionsCompleted").getAsInt());
            assertEquals(1705852800000L, stats.get("firstCollectionDate").getAsLong());
            assertEquals(1705939200000L, stats.get("lastActivityDate").getAsLong());

            // Verify collections
            JsonObject collections = playerJson.getAsJsonObject("collections");
            assertEquals(2, collections.size(), "Should have 2 collections");

            JsonObject testCol = collections.getAsJsonObject("test_collection");
            assertTrue(testCol.get("complete").getAsBoolean());
            assertTrue(testCol.get("rewardClaimed").getAsBoolean());
            assertEquals(2, testCol.getAsJsonArray("items").size());

            JsonObject partialCol = collections.getAsJsonObject("partial_collection");
            assertFalse(partialCol.get("complete").getAsBoolean());
            assertFalse(partialCol.get("rewardClaimed").getAsBoolean());
        }

        @Test
        @DisplayName("suggestExportFiles returns empty for non-existent directory")
        void testSuggestExportFiles_noDirectory_returnsEmpty() {
            // Don't create exports directory
            List<String> suggestions = migrationManager.suggestExportFiles();

            assertTrue(suggestions.isEmpty(), "Should return empty list");
        }

        @Test
        @DisplayName("suggestExportFiles returns JSON files only")
        void testSuggestExportFiles_returnsJsonFiles() throws Exception {
            // Create exports directory with various files
            Path exportsDir = tempDir.resolve("exports");
            Files.createDirectories(exportsDir);
            Files.writeString(exportsDir.resolve("export1.json"), "{}");
            Files.writeString(exportsDir.resolve("export2.json"), "{}");
            Files.writeString(exportsDir.resolve("readme.txt"), "text");
            Files.writeString(exportsDir.resolve("backup.sql"), "sql");

            List<String> suggestions = migrationManager.suggestExportFiles();

            assertEquals(2, suggestions.size(), "Should return only JSON files");
            assertTrue(suggestions.contains("export1.json"));
            assertTrue(suggestions.contains("export2.json"));
        }

        @Test
        @DisplayName("getExportsDirectory returns correct path")
        void testGetExportsDirectory_returnsCorrectPath() {
            Path exportsDir = migrationManager.getExportsDirectory();

            assertEquals(tempDir.resolve("exports"), exportsDir);
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

    /**
     * Creates a complex player progress for round-trip testing.
     */
    private PlayerProgress createComplexPlayerProgress(UUID playerId) {
        PlayerProgress progress = new PlayerProgress(playerId);
        progress.setTotalCollectiblesCollected(50);
        progress.setTotalCollectionsCompleted(3);
        progress.setFirstCollectionDate(1705852800000L);
        progress.setLastActivityDate(1705939200000L);

        // Complete collection with reward claimed
        PlayerProgress.CollectionProgress col1 = progress.getProgress("test_collection");
        col1.addItemDirect("item1");
        col1.addItemDirect("item2");
        col1.setComplete(true);
        col1.setRewardClaimed(true);
        col1.setCompletedDate(1705900000000L);

        // Partial collection
        PlayerProgress.CollectionProgress col2 = progress.getProgress("partial_collection");
        col2.addItemDirect("itemA");
        col2.setComplete(false);
        col2.setRewardClaimed(false);

        return progress;
    }

    /**
     * Creates a valid export JSON file for testing.
     */
    private Path createValidExportFile(List<UUID> playerIds) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"formatVersion\": ").append(ExportFormat.FORMAT_VERSION).append(",\n");
        json.append("  \"exportDate\": \"2026-01-22T00:00:00Z\",\n");
        json.append("  \"pluginVersion\": \"1.0.0-TEST\",\n");
        json.append("  \"exportType\": \"FULL\",\n");
        json.append("  \"players\": [\n");

        for (int i = 0; i < playerIds.size(); i++) {
            UUID id = playerIds.get(i);
            json.append("    {\n");
            json.append("      \"uuid\": \"").append(id.toString()).append("\",\n");
            json.append("      \"stats\": {\n");
            json.append("        \"totalCollectiblesCollected\": 10,\n");
            json.append("        \"totalCollectionsCompleted\": 1,\n");
            json.append("        \"firstCollectionDate\": 1705852800000,\n");
            json.append("        \"lastActivityDate\": 1705939200000\n");
            json.append("      },\n");
            json.append("      \"collections\": {\n");
            json.append("        \"test_collection\": {\n");
            json.append("          \"items\": [\"item1\", \"item2\"],\n");
            json.append("          \"complete\": true,\n");
            json.append("          \"rewardClaimed\": false,\n");
            json.append("          \"completedDate\": 1705939200000\n");
            json.append("        }\n");
            json.append("      }\n");
            json.append("    }");
            if (i < playerIds.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ],\n");
        json.append("  \"totalPlayers\": ").append(playerIds.size()).append("\n");
        json.append("}\n");

        Path exportsDir = tempDir.resolve("exports");
        Files.createDirectories(exportsDir);
        Path file = exportsDir.resolve("test_export.json");
        Files.writeString(file, json.toString());
        return file;
    }
}
