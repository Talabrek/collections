# Phase 13: Data Export/Import - Research

**Researched:** 2026-01-22
**Domain:** JSON data serialization, streaming I/O, cache invalidation, command patterns
**Confidence:** HIGH

## Summary

Phase 13 implements data export/import functionality for server migration and backup purposes. The core challenge is handling potentially large datasets (10k+ players) without causing OutOfMemoryError, while ensuring imported data is immediately visible to online players without requiring them to rejoin.

Research confirms that Gson is already bundled with Paper servers (v2.8.9+), so no new dependencies are required beyond a `compileOnly` reference for IDE support. Gson provides `JsonWriter` and `JsonReader` classes that enable streaming JSON output and input, allowing incremental processing without loading entire datasets into memory. The existing `PlayerDataManager` cache can be invalidated for specific UUIDs, and `Storage.loadPlayer()` can reload fresh data from the database.

The admin command infrastructure from Phase 11 (using `ArgumentTypes.playerProfiles()` for offline player lookup) directly applies here. Export/import commands should use the same permission (`collections.admin`) and follow established patterns for async operations with progress feedback.

**Primary recommendation:** Implement streaming export using `JsonWriter` that fetches players in batches from the database and writes incrementally to file. Import should validate JSON structure before applying, support dry-run mode, and invalidate PlayerDataManager cache for affected online players after successful import.

## Standard Stack

### Core (Already Available)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Gson | 2.8.9+ (bundled) | JSON serialization | Bundled with Paper; streaming API for large files |
| HikariCP | 5.1.0 | Database pooling | Already used; provides JDBC cursors |
| Adventure API | (bundled) | Player feedback | Progress messages during long operations |

### Supporting (Already Available)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Brigadier | (bundled) | Command framework | Register export/import commands |
| java.nio | JDK 21 | File operations | Create export directory, write files |
| java.time | JDK 21 | Timestamps | Export metadata, ISO-8601 formatting |

### No New Dependencies Required

Gson is confirmed bundled with Paper servers per [SpigotMC Wiki](https://www.spigotmc.org/wiki/included-libraries-in-spigot/) and [PaperMC Gson Serializer docs](https://docs.papermc.io/adventure/serializer/gson/). Add for IDE support only:

```kotlin
compileOnly("com.google.gson:gson:2.10.1")  // IDE support; runtime provided by Paper
```

**DO NOT shade Gson** - it causes classloader conflicts with Paper's bundled version.

## Architecture Patterns

### Recommended Project Structure

```
src/main/java/com/blockworlds/collections/
├── command/
│   └── CollectionsCommand.java      # Add export/import subcommands
├── manager/
│   └── DataMigrationManager.java    # NEW: Export/import orchestration
├── model/
│   └── ExportFormat.java            # NEW: JSON data transfer object
└── storage/
    ├── Storage.java                 # Add getAllPlayerUuids() method
    └── SQLiteStorage.java           # Implement streaming query
```

### Pattern 1: Streaming Export with JsonWriter

**What:** Write JSON incrementally using Gson's streaming API instead of building entire object graph in memory.

**When to use:** Exporting all player data (EXPORT-02) to avoid OOM on large datasets.

**Example:**
```java
// Source: https://attacomsian.com/blog/gson-read-write-json-stream
public void exportAllPlayersStreaming(Path outputFile) throws IOException {
    try (Writer writer = Files.newBufferedWriter(outputFile);
         JsonWriter jsonWriter = new JsonWriter(writer)) {

        jsonWriter.setIndent("  ");  // Pretty print
        jsonWriter.beginObject();

        // Metadata
        jsonWriter.name("formatVersion").value(1);
        jsonWriter.name("exportDate").value(Instant.now().toString());
        jsonWriter.name("pluginVersion").value(plugin.getDescription().getVersion());

        // Stream players array
        jsonWriter.name("players");
        jsonWriter.beginArray();

        int count = 0;
        for (UUID uuid : storage.getAllPlayerUuids()) {
            PlayerProgress progress = storage.loadPlayer(uuid).join();
            writePlayerProgress(jsonWriter, progress);
            count++;

            // Progress feedback every 100 players
            if (count % 100 == 0) {
                sender.sendMessage(Component.text("Exported " + count + " players...", NamedTextColor.GRAY));
            }
        }

        jsonWriter.endArray();
        jsonWriter.name("totalPlayers").value(count);
        jsonWriter.endObject();
    }
}
```

### Pattern 2: Streaming Import with JsonReader

**What:** Read JSON token by token, processing each player record without loading the full file.

**When to use:** Importing data files that may be large (EXPORT-03).

**Example:**
```java
// Source: https://www.amitph.com/java-parse-large-json-files/
public ImportResult importPlayersStreaming(Path inputFile, boolean dryRun) throws IOException {
    ImportResult result = new ImportResult();

    try (Reader reader = Files.newBufferedReader(inputFile);
         JsonReader jsonReader = new JsonReader(reader)) {

        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            switch (name) {
                case "formatVersion" -> {
                    int version = jsonReader.nextInt();
                    if (version > CURRENT_FORMAT_VERSION) {
                        throw new IllegalArgumentException("Unsupported format version: " + version);
                    }
                }
                case "players" -> {
                    jsonReader.beginArray();
                    while (jsonReader.hasNext()) {
                        PlayerProgress progress = readPlayerProgress(jsonReader);
                        if (dryRun) {
                            result.addPreview(progress);
                        } else {
                            importSinglePlayer(progress);
                            result.incrementImported();
                        }
                    }
                    jsonReader.endArray();
                }
                default -> jsonReader.skipValue();
            }
        }
        jsonReader.endObject();
    }

    return result;
}
```

### Pattern 3: Cache Invalidation for Online Players

**What:** After import, refresh cached data for any online players whose data was modified.

**When to use:** EXPORT-06 - online players see updated progress immediately.

**Example:**
```java
public void invalidateCacheAndReload(UUID playerId) {
    // Check if player is online and cached
    Player onlinePlayer = Bukkit.getPlayer(playerId);
    if (onlinePlayer != null && playerDataManager.isLoaded(playerId)) {
        // Remove stale cache entry
        playerDataManager.cache.remove(playerId);
        playerDataManager.pendingLoads.remove(playerId);

        // Reload from database
        playerDataManager.loadPlayer(onlinePlayer).thenAccept(newProgress -> {
            // Optionally notify player
            if (!silent) {
                onlinePlayer.sendMessage(Component.text(
                    "Your collection progress has been updated.", NamedTextColor.GREEN));
            }
        });
    }
}
```

### Anti-Patterns to Avoid

- **Loading entire dataset into List:** `List<PlayerProgress> all = storage.loadAllPlayers()` will OOM on large servers. Use streaming/cursor-based iteration instead.

- **Import without validation:** Always validate JSON structure BEFORE writing to database. Use dry-run mode to preview changes.

- **Import for online players without cache invalidation:** Imported data will be overwritten when player quits because PlayerDataManager saves cached (stale) version. Must invalidate cache first.

- **Blocking main thread during export:** All I/O must run async via `Bukkit.getAsyncScheduler()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON serialization | Custom string building | Gson JsonWriter/JsonReader | Handles escaping, Unicode, nested structures |
| UUID parsing | Manual regex | `UUID.fromString()` with try-catch | Built-in validation |
| File path handling | String concatenation | `java.nio.file.Path` | Cross-platform, resolves `..` correctly |
| Timestamp formatting | SimpleDateFormat | `Instant.toString()` (ISO-8601) | Thread-safe, unambiguous |
| Progress feedback | Flooding chat | Rate-limited updates (every 100 records) | Better UX |

**Key insight:** Gson streaming is specifically designed for large JSON. The tutorial at [amitph.com](https://www.amitph.com/java-parse-large-json-files/) demonstrates parsing a 400MB JSON file with only 150MB heap usage using this pattern.

## Common Pitfalls

### Pitfall 1: OutOfMemoryError on Large Export

**What goes wrong:** Building entire `List<PlayerProgress>` before serializing causes heap exhaustion with 10k+ players.

**Why it happens:** Natural instinct is to collect all data first, then serialize. Each PlayerProgress with 50+ collected items uses ~10KB, so 10k players = 100MB+ just for the list.

**How to avoid:**
1. Use database cursor with `setFetchSize(100)` for batch fetching
2. Use `JsonWriter` to stream directly to file
3. Process one player at a time, letting GC reclaim memory

**Warning signs:** Export fails silently or with OOM after several seconds; heap usage spikes during export.

### Pitfall 2: Import Overwrites Online Player Data

**What goes wrong:** Import writes to database, but online player's cached data is saved on quit, overwriting the import.

**Why it happens:** `PlayerDataManager.saveAndUnload()` on quit saves the in-memory cache to database. If import updated database but not cache, the stale cache wins.

**How to avoid:**
1. Check if player is online before import: `Bukkit.getPlayer(uuid) != null`
2. If online, invalidate their cache entry: `playerDataManager.cache.remove(uuid)`
3. Force reload: `playerDataManager.loadPlayer(player)`
4. Alternative: Refuse import for online players, require them to be offline

**Warning signs:** Imported data "disappears" after player relogs.

### Pitfall 3: Notification Spam During Bulk Import

**What goes wrong:** Importing 50 items triggers 50 "item added" notifications per player.

**Why it happens:** Import calls same code path as normal item collection, which triggers NotificationManager.

**How to avoid:**
1. Add `silent` or `suppressNotifications` parameter to import operations
2. Use batch import method that skips per-item notifications
3. Send single summary notification after import completes

**Warning signs:** Server chat flooded during import; online players complain about notification spam.

### Pitfall 4: Invalid JSON Causes Partial Import

**What goes wrong:** Import processes half the file, then hits a parse error, leaving database in inconsistent state.

**Why it happens:** Streaming parsers fail at point of error, but prior writes already committed.

**How to avoid:**
1. Validate entire file structure BEFORE any database writes
2. Use database transaction for atomic import
3. Implement dry-run mode (EXPORT-05) that parses without writing

**Warning signs:** Player count mismatch between export and import; some players have data, others don't.

### Pitfall 5: Export Directory Doesn't Exist

**What goes wrong:** Export command fails with `NoSuchFileException`.

**Why it happens:** `plugins/Collections/exports/` doesn't exist on first run.

**How to avoid:**
```java
Path exportsDir = plugin.getDataFolder().toPath().resolve("exports");
Files.createDirectories(exportsDir);  // Creates if not exists, no error if exists
```

**Warning signs:** First export attempt fails; works after manual directory creation.

## Code Examples

### Export Format Schema (v1)

```json
{
  "formatVersion": 1,
  "exportDate": "2026-01-22T15:30:00Z",
  "pluginVersion": "1.1.0",
  "exportType": "SINGLE",  // or "FULL"
  "players": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "stats": {
        "totalCollectiblesCollected": 42,
        "totalCollectionsCompleted": 3,
        "firstCollectionDate": 1705852800000,
        "lastActivityDate": 1705939200000
      },
      "collections": {
        "forest_specimens": {
          "items": ["acorn", "maple_leaf", "fern_frond"],
          "complete": true,
          "rewardClaimed": false,
          "completedDate": 1705939200000
        },
        "ocean_treasures": {
          "items": ["sea_glass"],
          "complete": false,
          "rewardClaimed": false,
          "completedDate": 0
        }
      }
    }
  ],
  "totalPlayers": 1
}
```

### Export Command Registration (Brigadier)

```java
// Source: Existing CollectionsCommand patterns
.then(Commands.literal("export")
    .requires(src -> src.getSender().hasPermission("collections.admin"))
    // /collections export <player>
    .then(Commands.argument("target", ArgumentTypes.playerProfiles())
        .executes(this::exportPlayer))
    // /collections export all
    .then(Commands.literal("all")
        .executes(this::exportAll)))
```

### Import Command with Dry-Run

```java
// /collections import <file> [--dry-run]
.then(Commands.literal("import")
    .requires(src -> src.getSender().hasPermission("collections.admin"))
    .then(Commands.argument("file", StringArgumentType.word())
        .suggests(this::suggestExportFiles)
        .executes(ctx -> importData(ctx, false))
        .then(Commands.literal("--dry-run")
            .executes(ctx -> importData(ctx, true)))))
```

### Validation Before Import

```java
// Source: Best practice for JSON import safety
public ValidationResult validateImportFile(Path file) {
    ValidationResult result = new ValidationResult();

    try (JsonReader reader = new JsonReader(Files.newBufferedReader(file))) {
        reader.beginObject();

        boolean hasVersion = false;
        boolean hasPlayers = false;

        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "formatVersion" -> {
                    int version = reader.nextInt();
                    hasVersion = true;
                    if (version > CURRENT_FORMAT_VERSION) {
                        result.addError("Unsupported format version: " + version +
                            " (max supported: " + CURRENT_FORMAT_VERSION + ")");
                    }
                }
                case "players" -> {
                    hasPlayers = true;
                    reader.beginArray();
                    int playerCount = 0;
                    while (reader.hasNext()) {
                        try {
                            validatePlayerEntry(reader);
                            playerCount++;
                        } catch (Exception e) {
                            result.addError("Invalid player entry at index " + playerCount + ": " + e.getMessage());
                        }
                    }
                    reader.endArray();
                    result.setPlayerCount(playerCount);
                }
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        if (!hasVersion) result.addError("Missing required field: formatVersion");
        if (!hasPlayers) result.addError("Missing required field: players");

    } catch (IOException e) {
        result.addError("Failed to parse JSON: " + e.getMessage());
    }

    return result;
}
```

### Progress Feedback During Long Operations

```java
// Async export with progress updates
public CompletableFuture<ExportResult> exportAllAsync(CommandSender sender) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            Path file = createExportFile("all");
            AtomicInteger count = new AtomicInteger(0);

            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(file))) {
                writeExportHeader(writer);
                writer.name("players").beginArray();

                for (UUID uuid : storage.getAllPlayerUuids()) {
                    PlayerProgress progress = storage.loadPlayer(uuid).join();
                    writePlayerProgress(writer, progress);

                    int current = count.incrementAndGet();
                    if (current % 100 == 0) {
                        // Update on main thread
                        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                            sender.sendMessage(Component.text(
                                "Exported " + current + " players...", NamedTextColor.GRAY));
                        });
                    }
                }

                writer.endArray();
                writeExportFooter(writer, count.get());
            }

            return new ExportResult(true, file, count.get());

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Export failed", e);
            return new ExportResult(false, null, 0);
        }
    }, Bukkit.getAsyncScheduler().createDelayedTask(plugin, t -> {}, Duration.ZERO).getExecutor());
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Gson.toJson(object)` for all sizes | `JsonWriter` streaming for large data | Always recommended for >1MB | Prevents OOM |
| Blocking file I/O | Async with CompletableFuture | Java 8+ | Non-blocking server |
| Manual JSON escaping | Gson handles automatically | Always | Correct handling of special chars |

**Deprecated/outdated:**
- `SimpleDateFormat` for timestamps - Use `Instant.toString()` or `DateTimeFormatter` (thread-safe)
- `File` class for paths - Use `java.nio.file.Path` (better API)

## Open Questions

1. **Multi-server import coordination:**
   - What we know: MySQL storage allows multi-server deployment; import writes to shared database
   - What's unclear: Should import broadcast cache invalidation to other servers via plugin messaging?
   - Recommendation: For v1.1, document that import should run when target players are offline on ALL servers. Plugin messaging coordination deferred to v1.2.

2. **Incremental/differential export:**
   - What we know: Full export can be large; incremental would export only changes since last export
   - What's unclear: How to track "last export date" per player; complexity vs benefit
   - Recommendation: For v1.1, implement full export only. Differential export adds significant complexity with limited benefit for typical server migration use case.

3. **Export file naming:**
   - What we know: Need unique filenames to prevent overwrites
   - What's unclear: Best naming scheme
   - Recommendation: Use pattern `{type}_{timestamp}.json` e.g., `player_550e8400_2026-01-22T15-30-00.json` or `all_2026-01-22T15-30-00.json`

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `PlayerDataManager.java`, `Storage.java`, `SQLiteStorage.java`, `CollectionsCommand.java`
- [Gson Streaming Guide](https://attacomsian.com/blog/gson-read-write-json-stream) - JsonWriter/JsonReader patterns
- [Parse Large JSON with Gson](https://www.amitph.com/java-parse-large-json-files/) - Memory-efficient streaming (400MB test)
- [PaperMC Gson docs](https://docs.papermc.io/adventure/serializer/gson/) - Confirms Gson bundled

### Secondary (MEDIUM confidence)
- [SpigotMC Wiki: Included Libraries](https://www.spigotmc.org/wiki/included-libraries-in-spigot/) - Confirms Gson 2.8.9
- [Fixing JSON OOM with Streaming](https://blog.jakubholy.net/fixing-json-oom-with-streaming-and-mapdb/) - Real-world OOM case study
- v1.1 Research Summary: `.planning/research/v1.1/SUMMARY.md` - Prior stack research

### Tertiary (LOW confidence)
- [SpigotMC forum: Save/Load Data Files](https://www.spigotmc.org/wiki/save-load-data-files/) - General patterns
- [GitHub Paper Issue #6370](https://github.com/PaperMC/Paper/issues/6370) - Gson availability discussion

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Gson bundled confirmed by multiple sources; no new deps needed
- Architecture: HIGH - Direct extension of existing Storage/PlayerDataManager patterns
- Pitfalls: HIGH - Memory/cache issues well-documented in prior v1.1 research

**Research date:** 2026-01-22
**Valid until:** 60 days (stable APIs, no expected changes)
