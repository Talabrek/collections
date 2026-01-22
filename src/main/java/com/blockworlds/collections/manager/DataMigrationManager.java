package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.ExportFormat;
import com.blockworlds.collections.model.ImportResult;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.model.ValidationResult;
import com.blockworlds.collections.storage.Storage;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Stream;

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

    // ========== Import Operations ==========

    /**
     * Validate an import file without applying changes.
     * Uses streaming to handle large files efficiently.
     *
     * @param file The path to the import file
     * @return ValidationResult with errors or success info
     */
    public ValidationResult validateImportFile(Path file) {
        ValidationResult result = new ValidationResult();

        if (!Files.exists(file)) {
            result.addError("File not found: " + file.getFileName());
            return result;
        }

        try (Reader fileReader = Files.newBufferedReader(file);
             JsonReader reader = new JsonReader(fileReader)) {

            reader.beginObject();

            boolean hasFormatVersion = false;
            boolean hasPlayers = false;

            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case "formatVersion" -> {
                        int version = reader.nextInt();
                        result.setFormatVersion(version);
                        hasFormatVersion = true;

                        if (version > ExportFormat.FORMAT_VERSION) {
                            result.addError("Unsupported format version: " + version +
                                    " (max supported: " + ExportFormat.FORMAT_VERSION + ")");
                        }
                    }
                    case "players" -> {
                        hasPlayers = true;
                        int playerCount = validatePlayersArray(reader, result);
                        result.setPlayerCount(playerCount);
                    }
                    default -> reader.skipValue();
                }
            }

            reader.endObject();

            // Check required fields
            if (!hasFormatVersion) {
                result.addError("Missing required field: formatVersion");
            }
            if (!hasPlayers) {
                result.addError("Missing required field: players");
            }

        } catch (IOException e) {
            result.addError("Failed to read file: " + e.getMessage());
        } catch (Exception e) {
            result.addError("Invalid JSON: " + e.getMessage());
        }

        return result;
    }

    /**
     * Validate the players array in an import file.
     *
     * @param reader The JSON reader positioned at the players array
     * @param result The validation result to add errors to
     * @return The number of valid players found
     */
    private int validatePlayersArray(JsonReader reader, ValidationResult result) throws IOException {
        int playerCount = 0;
        int playerIndex = 0;

        reader.beginArray();
        while (reader.hasNext()) {
            playerIndex++;
            if (validatePlayerEntry(reader, result, playerIndex)) {
                playerCount++;
            }
        }
        reader.endArray();

        return playerCount;
    }

    /**
     * Validate a single player entry in the import file.
     *
     * @param reader      The JSON reader positioned at a player object
     * @param result      The validation result to add errors to
     * @param playerIndex The index of this player (for error messages)
     * @return true if the player entry is valid
     */
    private boolean validatePlayerEntry(JsonReader reader, ValidationResult result, int playerIndex) throws IOException {
        boolean hasUuid = false;
        boolean hasCollections = false;
        boolean valid = true;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            switch (name) {
                case "uuid" -> {
                    String uuidStr = reader.nextString();
                    try {
                        UUID.fromString(uuidStr);
                        hasUuid = true;
                    } catch (IllegalArgumentException e) {
                        result.addError("Player " + playerIndex + ": Invalid UUID format: " + uuidStr);
                        valid = false;
                    }
                }
                case "collections" -> {
                    hasCollections = true;
                    reader.skipValue(); // Collections format is flexible
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (!hasUuid) {
            result.addError("Player " + playerIndex + ": Missing required field 'uuid'");
            valid = false;
        }
        if (!hasCollections) {
            result.addError("Player " + playerIndex + ": Missing required field 'collections'");
            valid = false;
        }

        return valid;
    }

    /**
     * Import player data from a JSON file.
     *
     * @param file   The path to the import file
     * @param dryRun If true, validate only without applying changes
     * @param sender The command sender to receive progress updates
     * @return CompletableFuture containing the import result
     */
    public CompletableFuture<ImportResult> importPlayers(Path file, boolean dryRun, CommandSender sender) {
        String executor = sender.getName();

        return CompletableFuture.supplyAsync(() -> {
            // First validate the file
            ValidationResult validation = validateImportFile(file);
            if (!validation.isValid()) {
                String errors = String.join("; ", validation.getErrors());
                return ImportResult.failure("Validation failed: " + errors);
            }

            if (dryRun) {
                // Count online players that would be affected
                List<UUID> onlineAffected = countAffectedOnlinePlayers(file);

                // Log dry-run
                plugin.getLogger().info("[IMPORT] Dry-run by " + executor + ": " +
                        validation.getPlayerCount() + " players would be imported from " + file.getFileName());

                return new ImportResult(validation.getPlayerCount(), 0, onlineAffected);
            }

            // Perform the actual import
            return performImport(file, sender, executor);
        });
    }

    /**
     * Count online players that would be affected by an import.
     */
    private List<UUID> countAffectedOnlinePlayers(Path file) {
        List<UUID> affected = new ArrayList<>();

        try (Reader fileReader = Files.newBufferedReader(file);
             JsonReader reader = new JsonReader(fileReader)) {

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("players".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        UUID uuid = extractUuidFromPlayer(reader);
                        if (uuid != null && Bukkit.getPlayer(uuid) != null) {
                            affected.add(uuid);
                        }
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

        } catch (IOException ignored) {
            // Already validated, shouldn't fail
        }

        return affected;
    }

    /**
     * Extract UUID from a player entry without parsing everything.
     */
    private UUID extractUuidFromPlayer(JsonReader reader) throws IOException {
        UUID uuid = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("uuid".equals(name)) {
                try {
                    uuid = UUID.fromString(reader.nextString());
                } catch (IllegalArgumentException ignored) {
                    // Invalid UUID, skip
                }
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return uuid;
    }

    /**
     * Perform the actual import operation.
     */
    private ImportResult performImport(Path file, CommandSender sender, String executor) {
        List<UUID> affectedOnlinePlayers = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        try (Reader fileReader = Files.newBufferedReader(file);
             JsonReader reader = new JsonReader(fileReader)) {

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("players".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        PlayerProgress progress = readPlayerProgress(reader);
                        if (progress != null) {
                            // Save to storage
                            storage.savePlayer(progress).join();
                            imported++;

                            // Track if player is online
                            if (Bukkit.getPlayer(progress.getPlayerId()) != null) {
                                affectedOnlinePlayers.add(progress.getPlayerId());
                            }

                            // Progress feedback every 100 players
                            if (imported % 100 == 0) {
                                final int current = imported;
                                Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                                    sender.sendMessage(Component.text(
                                            "Imported " + current + " players...", NamedTextColor.GRAY));
                                });
                            }
                        } else {
                            skipped++;
                        }
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();

            // Invalidate cache for all affected online players
            for (UUID playerId : affectedOnlinePlayers) {
                playerDataManager.invalidateCacheAndReload(playerId).join();
            }

            // Log admin action
            plugin.getLogger().info("[IMPORT] Import by " + executor + ": " +
                    imported + " players from " + file.getFileName() +
                    " (" + affectedOnlinePlayers.size() + " online players refreshed)");

            return new ImportResult(imported, skipped, affectedOnlinePlayers);

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to import player data", e);
            return ImportResult.failure("IO error: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error during import", e);
            return ImportResult.failure("Error: " + e.getMessage());
        }
    }

    /**
     * Read a single player's progress from the JSON stream.
     */
    private PlayerProgress readPlayerProgress(JsonReader reader) throws IOException {
        UUID playerId = null;
        int totalCollectiblesCollected = 0;
        int totalCollectionsCompleted = 0;
        long firstCollectionDate = 0;
        long lastActivityDate = 0;
        List<CollectionData> collections = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            switch (name) {
                case "uuid" -> {
                    try {
                        playerId = UUID.fromString(reader.nextString());
                    } catch (IllegalArgumentException e) {
                        reader.skipValue();
                        return null;
                    }
                }
                case "stats" -> {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String statName = reader.nextName();
                        switch (statName) {
                            case "totalCollectiblesCollected" -> totalCollectiblesCollected = reader.nextInt();
                            case "totalCollectionsCompleted" -> totalCollectionsCompleted = reader.nextInt();
                            case "firstCollectionDate" -> firstCollectionDate = reader.nextLong();
                            case "lastActivityDate" -> lastActivityDate = reader.nextLong();
                            default -> reader.skipValue();
                        }
                    }
                    reader.endObject();
                }
                case "collections" -> {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String collectionId = reader.nextName();
                        CollectionData colData = readCollectionData(reader, collectionId);
                        if (colData != null) {
                            collections.add(colData);
                        }
                    }
                    reader.endObject();
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (playerId == null) {
            return null;
        }

        // Build PlayerProgress object
        PlayerProgress progress = new PlayerProgress(playerId);
        progress.setTotalCollectiblesCollected(totalCollectiblesCollected);
        progress.setTotalCollectionsCompleted(totalCollectionsCompleted);
        progress.setFirstCollectionDate(firstCollectionDate);
        progress.setLastActivityDate(lastActivityDate);

        // Add collection progress
        for (CollectionData colData : collections) {
            PlayerProgress.CollectionProgress colProgress = progress.getProgress(colData.id);
            for (String item : colData.items) {
                colProgress.addItemDirect(item);
            }
            colProgress.setComplete(colData.complete);
            colProgress.setRewardClaimed(colData.rewardClaimed);
            colProgress.setCompletedDate(colData.completedDate);
        }

        return progress;
    }

    /**
     * Read collection data from the JSON stream.
     */
    private CollectionData readCollectionData(JsonReader reader, String collectionId) throws IOException {
        List<String> items = new ArrayList<>();
        boolean complete = false;
        boolean rewardClaimed = false;
        long completedDate = 0;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            switch (name) {
                case "items" -> {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        items.add(reader.nextString());
                    }
                    reader.endArray();
                }
                case "complete" -> complete = reader.nextBoolean();
                case "rewardClaimed" -> rewardClaimed = reader.nextBoolean();
                case "completedDate" -> completedDate = reader.nextLong();
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        return new CollectionData(collectionId, items, complete, rewardClaimed, completedDate);
    }

    /**
     * Temporary data holder for collection import.
     */
    private record CollectionData(
            String id,
            List<String> items,
            boolean complete,
            boolean rewardClaimed,
            long completedDate
    ) {}

    /**
     * List JSON files in the exports directory for tab completion.
     *
     * @return List of JSON filenames in the exports directory
     */
    public List<String> suggestExportFiles() {
        if (!Files.exists(exportsDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(exportsDirectory)) {
            return stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
