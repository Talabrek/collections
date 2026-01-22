package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.ExportFormat;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.storage.Storage;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages data export and import operations for the Collections plugin.
 * Uses Gson streaming to handle large datasets without OutOfMemoryError.
 */
public class DataMigrationManager {

    private final Collections plugin;
    private final Storage storage;
    private final PlayerDataManager playerDataManager;
    private final Path exportsDirectory;

    private static final DateTimeFormatter FILENAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC);

    /**
     * Result of an export operation.
     */
    public record ExportResult(boolean success, Path filePath, int playerCount, String errorMessage) {
        public ExportResult(boolean success, Path filePath, int playerCount) {
            this(success, filePath, playerCount, null);
        }

        public static ExportResult failure(String errorMessage) {
            return new ExportResult(false, null, 0, errorMessage);
        }
    }

    public DataMigrationManager(Collections plugin, Storage storage, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerDataManager = playerDataManager;
        this.exportsDirectory = plugin.getDataFolder().toPath().resolve("exports");
    }

    /**
     * Export a single player's data to a JSON file.
     *
     * @param playerId The UUID of the player to export
     * @param sender   The command sender to receive progress updates
     * @return CompletableFuture containing the export result
     */
    public CompletableFuture<ExportResult> exportPlayer(UUID playerId, CommandSender sender) {
        String executor = sender.getName();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create export directory if needed
                createExportDirectory();

                // Load player data
                PlayerProgress progress = storage.loadPlayer(playerId).join();
                if (progress == null) {
                    return ExportResult.failure("Player has no data");
                }

                // Generate filename
                Path filePath = generateFilePath(ExportFormat.EXPORT_TYPE_SINGLE, playerId);

                // Write export file
                try (Writer writer = Files.newBufferedWriter(filePath);
                     JsonWriter jsonWriter = new JsonWriter(writer)) {

                    jsonWriter.setIndent("  ");
                    jsonWriter.beginObject();

                    // Write header
                    writeExportHeader(jsonWriter, ExportFormat.EXPORT_TYPE_SINGLE);

                    // Write players array
                    jsonWriter.name("players");
                    jsonWriter.beginArray();
                    writePlayerProgress(jsonWriter, progress);
                    jsonWriter.endArray();

                    // Write footer
                    jsonWriter.name("totalPlayers").value(1);
                    jsonWriter.endObject();
                }

                // Log admin action
                plugin.getLogger().info("[EXPORT] SINGLE export by " + executor +
                        ": 1 player to " + filePath.getFileName());

                return new ExportResult(true, filePath, 1);

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to export player data", e);
                return ExportResult.failure("IO error: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error during export", e);
                return ExportResult.failure("Error: " + e.getMessage());
            }
        });
    }

    /**
     * Export all player data to a JSON file using streaming.
     * Progress feedback is sent every 100 players.
     *
     * @param sender The command sender to receive progress updates
     * @return CompletableFuture containing the export result
     */
    public CompletableFuture<ExportResult> exportAllPlayers(CommandSender sender) {
        String executor = sender.getName();

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create export directory if needed
                createExportDirectory();

                // Get all player UUIDs
                List<UUID> playerUuids = storage.getAllPlayerUuids().join();
                if (playerUuids.isEmpty()) {
                    return ExportResult.failure("No player data found");
                }

                // Generate filename
                Path filePath = generateFilePath(ExportFormat.EXPORT_TYPE_FULL, null);

                // Write export file with streaming
                int count = 0;
                try (Writer writer = Files.newBufferedWriter(filePath);
                     JsonWriter jsonWriter = new JsonWriter(writer)) {

                    jsonWriter.setIndent("  ");
                    jsonWriter.beginObject();

                    // Write header
                    writeExportHeader(jsonWriter, ExportFormat.EXPORT_TYPE_FULL);

                    // Write players array - stream one at a time
                    jsonWriter.name("players");
                    jsonWriter.beginArray();

                    for (UUID uuid : playerUuids) {
                        PlayerProgress progress = storage.loadPlayer(uuid).join();
                        if (progress != null) {
                            writePlayerProgress(jsonWriter, progress);
                            count++;

                            // Progress feedback every 100 players
                            if (count % 100 == 0) {
                                final int current = count;
                                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                    sender.sendMessage(Component.text(
                                            "Exported " + current + " players...", NamedTextColor.GRAY));
                                });
                            }
                        }
                    }

                    jsonWriter.endArray();

                    // Write footer
                    jsonWriter.name("totalPlayers").value(count);
                    jsonWriter.endObject();
                }

                // Log admin action
                plugin.getLogger().info("[EXPORT] FULL export by " + executor +
                        ": " + count + " players to " + filePath.getFileName());

                return new ExportResult(true, filePath, count);

            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to export all player data", e);
                return ExportResult.failure("IO error: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error during full export", e);
                return ExportResult.failure("Error: " + e.getMessage());
            }
        });
    }

    /**
     * Create the exports directory if it doesn't exist.
     */
    private void createExportDirectory() throws IOException {
        Files.createDirectories(exportsDirectory);
    }

    /**
     * Generate a unique file path for an export.
     *
     * @param exportType The type of export (SINGLE or FULL)
     * @param playerId   The player UUID for single exports, or null for full exports
     * @return The generated file path
     */
    private Path generateFilePath(String exportType, UUID playerId) {
        String timestamp = FILENAME_FORMATTER.format(Instant.now());
        String filename;

        if (ExportFormat.EXPORT_TYPE_SINGLE.equals(exportType) && playerId != null) {
            filename = "player_" + playerId + "_" + timestamp + ".json";
        } else {
            filename = "all_" + timestamp + ".json";
        }

        return exportsDirectory.resolve(filename);
    }

    /**
     * Write the export header metadata.
     */
    private void writeExportHeader(JsonWriter writer, String exportType) throws IOException {
        writer.name("formatVersion").value(ExportFormat.FORMAT_VERSION);
        writer.name("exportDate").value(Instant.now().toString());
        writer.name("pluginVersion").value(plugin.getPluginMeta().getVersion());
        writer.name("exportType").value(exportType);
    }

    /**
     * Write a single player's progress to the JSON stream.
     */
    private void writePlayerProgress(JsonWriter writer, PlayerProgress progress) throws IOException {
        writer.beginObject();

        // Player UUID
        writer.name("uuid").value(progress.getPlayerId().toString());

        // Stats object
        writer.name("stats");
        writer.beginObject();
        writer.name("totalCollectiblesCollected").value(progress.getTotalCollectiblesCollected());
        writer.name("totalCollectionsCompleted").value(progress.getTotalCollectionsCompleted());
        writer.name("firstCollectionDate").value(progress.getFirstCollectionDate());
        writer.name("lastActivityDate").value(progress.getLastActivityDate());
        writer.endObject();

        // Collections object
        writer.name("collections");
        writer.beginObject();
        for (var entry : progress.getAllProgress().entrySet()) {
            String collectionId = entry.getKey();
            PlayerProgress.CollectionProgress colProgress = entry.getValue();
            writeCollectionProgress(writer, collectionId, colProgress);
        }
        writer.endObject();

        writer.endObject();
    }

    /**
     * Write a single collection's progress to the JSON stream.
     */
    private void writeCollectionProgress(JsonWriter writer, String collectionId,
                                         PlayerProgress.CollectionProgress progress) throws IOException {
        writer.name(collectionId);
        writer.beginObject();

        // Items array
        writer.name("items");
        writer.beginArray();
        Set<String> items = progress.getCollectedItems();
        for (String item : items) {
            writer.value(item);
        }
        writer.endArray();

        // Status fields
        writer.name("complete").value(progress.isComplete());
        writer.name("rewardClaimed").value(progress.isRewardClaimed());
        writer.name("completedDate").value(progress.getCompletedDate());

        writer.endObject();
    }

    /**
     * Get the exports directory path.
     */
    public Path getExportsDirectory() {
        return exportsDirectory;
    }
}
