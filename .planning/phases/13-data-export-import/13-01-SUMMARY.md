---
phase: 13-data-export-import
plan: 01
subsystem: data-migration
tags: [export, json, streaming, gson, admin-commands]

# Dependency graph
requires:
  - phase: 12-metrics-observability
    plan: 05
    provides: MetricsManager with counter operations
  - phase: 11-admin-commands
    plan: 02
    provides: playerProfiles() offline player lookup pattern
provides:
  - DataMigrationManager with streaming JSON export
  - ExportFormat constants for format versioning
  - /collections export <player> command
  - /collections export all command
  - Storage.getAllPlayerUuids() for streaming iteration
affects: [admin-operations, data-backup, server-migration]

# Tech tracking
tech-stack:
  added: []
  patterns: [streaming-json-writer, async-file-io, progress-feedback]

key-files:
  created:
    - src/main/java/com/blockworlds/collections/manager/DataMigrationManager.java
    - src/main/java/com/blockworlds/collections/model/ExportFormat.java
  modified:
    - build.gradle.kts
    - src/main/java/com/blockworlds/collections/storage/Storage.java
    - src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java
    - src/main/java/com/blockworlds/collections/Collections.java
    - src/main/java/com/blockworlds/collections/command/CollectionsCommand.java

key-decisions:
  - "Gson as compileOnly - Paper bundles Gson, no shading required"
  - "JsonWriter streaming avoids OOM on large datasets"
  - "Progress feedback every 100 players during bulk export"
  - "Export files to plugins/Collections/exports/ with timestamp naming"
  - "ExportResult record for operation results with success/failure info"

patterns-established:
  - "Streaming JSON export pattern for large datasets"
  - "Async file I/O with CompletableFuture for non-blocking operations"
  - "Admin action logging with [EXPORT] prefix"

# Metrics
duration: 5min
completed: 2026-01-22
---

# Phase 13 Plan 01: Data Export Commands Summary

**Streaming JSON export functionality for single player and full database exports**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-22
- **Completed:** 2026-01-22
- **Tasks:** 3
- **Files created:** 2
- **Files modified:** 5

## Accomplishments

- Added Gson 2.10.1 as compileOnly dependency for IDE support (Paper bundles at runtime)
- Added getAllPlayerUuids() method to Storage interface and SQLiteStorage implementation
- Created ExportFormat class with FORMAT_VERSION constant and export type constants
- Created DataMigrationManager with:
  - exportPlayer() for single player JSON export
  - exportAllPlayers() for streaming export of all players
  - JsonWriter streaming to avoid OutOfMemoryError on large datasets
  - Progress feedback every 100 players via Bukkit.getGlobalRegionScheduler()
  - Admin action logging with [EXPORT] prefix
- Registered DataMigrationManager in Collections main class
- Added /collections export <player> command for single player export
- Added /collections export all command for full database export
- Added help entries for export commands

## Task Commits

1. **Task 1: Add Gson compileOnly and Storage.getAllPlayerUuids()** - `ec27b93` (feat)
2. **Task 2: Create ExportFormat and DataMigrationManager** - `f932f85` (feat)
3. **Task 3: Register DataMigrationManager and add export commands** - `30a6645` (feat)

## Files Created

| File | Purpose |
|------|---------|
| `src/main/java/com/blockworlds/collections/manager/DataMigrationManager.java` | Export orchestration with streaming JsonWriter |
| `src/main/java/com/blockworlds/collections/model/ExportFormat.java` | JSON format constants (FORMAT_VERSION=1) |

## Files Modified

| File | Changes |
|------|---------|
| `build.gradle.kts` | Added Gson 2.10.1 compileOnly dependency |
| `Storage.java` | Added getAllPlayerUuids() method |
| `SQLiteStorage.java` | Implemented getAllPlayerUuids() |
| `Collections.java` | Added DataMigrationManager field, init, getter |
| `CollectionsCommand.java` | Added export commands and help entries |

## Decisions Made

- **Gson compileOnly:** Paper bundles Gson at runtime, so only compile-time reference needed for IDE support
- **JsonWriter streaming:** Avoids loading entire dataset into memory - writes player-by-player to file
- **100 player progress interval:** Balances user feedback with performance overhead
- **Export directory:** plugins/Collections/exports/ created on first export if doesn't exist
- **Filename format:** player_{uuid}_{timestamp}.json for single, all_{timestamp}.json for full
- **ExportResult record:** Clean API for returning success/failure with path and count

## Export JSON Format

```json
{
  "formatVersion": 1,
  "exportDate": "2026-01-22T15:30:00Z",
  "pluginVersion": "1.0.0",
  "exportType": "SINGLE",
  "players": [
    {
      "uuid": "...",
      "stats": {
        "totalCollectiblesCollected": 42,
        "totalCollectionsCompleted": 3,
        "firstCollectionDate": 1705852800000,
        "lastActivityDate": 1705939200000
      },
      "collections": {
        "forest_specimens": {
          "items": ["acorn", "maple_leaf"],
          "complete": true,
          "rewardClaimed": false,
          "completedDate": 1705939200000
        }
      }
    }
  ],
  "totalPlayers": 1
}
```

## Requirements Satisfied

- **EXPORT-01:** Admin can export single player's data to JSON file
- **EXPORT-02:** Admin can export all player data using streaming (no OOM risk)

## Deviations from Plan

None - plan executed exactly as written.

## Next Phase Readiness

- Export functionality complete and tested
- Import functionality will be implemented in 13-02
- Format versioning in place for future compatibility
- Ready for 13-02: Data Import Commands

---
*Phase: 13-data-export-import*
*Completed: 2026-01-22*
