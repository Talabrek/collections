# Architecture Research: v1.1 Operational Features

**Researched:** 2026-01-22
**Domain:** Minecraft Paper plugin operational tooling

## Summary

The v1.1 operational features (data export/import, progress notifications, admin force-complete, and metrics) integrate cleanly with the existing Collections plugin architecture. The plugin follows a well-structured manager-based service layer pattern with async database operations via CompletableFuture, making it straightforward to add these features without architectural changes. Export/import extends the existing Storage interface; notifications hook into existing event flow in listeners; admin commands extend CollectionsCommand; and metrics integrate at manager layer boundaries.

## Integration Points

### Export/Import Commands

**Existing components affected:**
- `Storage.java` — Add export/import method signatures to interface
- `SQLiteStorage.java` — Implement SQLite export logic
- `MySQLStorage.java` — Implement MySQL export logic
- `CollectionsCommand.java` — Add `/collections export` and `/collections import` subcommands
- `PlayerDataManager.java` — May need `getAllCachedPlayerIds()` for bulk operations

**New components needed:**
- `ExportFormat.java` (model) — Data transfer object for portable format (JSON or YAML)
- `DataMigrationManager.java` (manager) — Orchestrates export/import with validation

**Data flow:**
```
Export flow:
1. Admin runs /collections export [player] [file]
2. CollectionsCommand delegates to DataMigrationManager
3. DataMigrationManager calls Storage.exportPlayer() or Storage.exportAll()
4. Storage queries database, returns PlayerProgress records
5. DataMigrationManager serializes to JSON/YAML file in plugin folder
6. Success message sent to admin

Import flow:
1. Admin runs /collections import [file]
2. CollectionsCommand delegates to DataMigrationManager
3. DataMigrationManager reads and validates file format
4. For each player: PlayerDataManager.loadPlayer() to cache
5. Apply imported data, merge or replace based on strategy
6. Storage.savePlayer() persists changes
7. Success/failure summary sent to admin
```

**Design considerations:**
- Export format must be storage-agnostic (works for SQLite-to-MySQL migration)
- Include version header for forward compatibility
- Async operations with progress feedback for large exports
- Conflict resolution strategy for imports (overwrite vs merge)

---

### Progress Notifications

**Existing components affected:**
- `PlayerDataManager.addItem()` — Hook point for notification trigger
- `CollectibleInteractListener.processCollection()` — After item given, check progress
- `ItemUseListener` — After item added to journal, send notification
- `ConfigManager` — Add notification message templates and toggle

**New components needed:**
- `NotificationManager.java` (manager) — Centralized notification logic with rate limiting
- Optional: `NotificationStyle.java` (enum) — CHAT, ACTION_BAR, TITLE, BOSS_BAR

**Data flow:**
```
Collection notification flow:
1. Player collects item (CollectibleInteractListener) or adds to journal (ItemUseListener)
2. PlayerDataManager.addItem() returns true (newly added)
3. Get current progress: PlayerProgress.getCollectedCount(collectionId)
4. Get total: CollectionManager.getCollection().items().size()
5. NotificationManager.sendProgress(player, collectionName, current, total)
6. NotificationManager formats message using ConfigManager templates
7. Displays via configured style (action bar recommended for non-intrusive)
```

**Design considerations:**
- Rate limiting to avoid spam (one notification per collection per interval)
- Configurable enable/disable per notification type
- Different messages for milestones (first item, halfway, completion)
- Use Adventure API components (already in place via ConfigManager)

---

### Admin Force-Complete Command

**Existing components affected:**
- `CollectionsCommand.java` — Already has `/collections complete <player> <collection>` that adds all items
- `PlayerDataManager.addItem()` — Called for each item
- `PlayerDataManager.markComplete()` — Called when all items added

**New components needed:**
- None — Existing `/collections complete` command already does this

**Current implementation review:**
```java
// CollectionsCommand.java lines 787-825
private int completeCollection(CommandContext<CommandSourceStack> ctx) {
    // ...
    // Add all items to the player's progress
    for (CollectionItem item : collection.items()) {
        playerDataManager.addItem(target.getUniqueId(), collectionId, item.id());
    }
    // ...
}
```

**Gap analysis:**
The existing command adds all items but does NOT:
1. Explicitly call `markComplete()` (completion detection relies on natural flow)
2. Award rewards automatically
3. Work for offline players

**Required modifications:**
- Add explicit `playerDataManager.markComplete()` call
- Add optional `--with-rewards` flag to also call `RewardManager.giveRewards()`
- Add offline player support via UUID lookup

---

### Metrics Collection

**Existing components affected:**
- `SpawnManager` — Track spawn success/failure rates
- `PlayerDataManager` — Track collection completion events
- `Storage` — Already has `getTotalCollectiblesCollected()` and `getTotalCollectionsCompleted()`
- `Collections.java` (main) — Initialize and expose metrics manager

**New components needed:**
- `MetricsManager.java` (manager) — Aggregates and exposes metrics
- Optional: Prometheus/bStats integration classes

**Data flow:**
```
Metrics collection flow:
1. Events occur (spawn, collect, complete, database operation)
2. Manager increments counter in MetricsManager
3. MetricsManager stores in-memory counters (ConcurrentHashMap<String, AtomicLong>)
4. Admin runs /collections metrics to view current stats
5. Optional: Background task exports to external system (Prometheus, bStats)

Key metrics to track:
- spawn_attempts_total (counter)
- spawn_success_total (counter)
- spawn_failures_by_reason (counter with labels)
- items_collected_total (counter)
- collections_completed_total (counter)
- players_active (gauge)
- database_operations_total (counter by operation type)
- database_operation_duration_ms (histogram)
```

**Design considerations:**
- Use AtomicLong counters for thread-safety
- Minimal overhead (no blocking, no complex calculations on hot path)
- bStats integration for public metrics (community visibility)
- Prometheus exposition format for network monitoring stacks
- In-game `/collections metrics` command for operators

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Collections Plugin                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                     Command Layer                                ││
│  │  CollectionsCommand                                              ││
│  │    ├─ /collections export [player] [file]     [NEW]             ││
│  │    ├─ /collections import [file]               [NEW]             ││
│  │    ├─ /collections complete (modify: add markComplete)  [MOD]   ││
│  │    └─ /collections metrics                    [NEW]             ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              │                                       │
│  ┌───────────────────────────┼─────────────────────────────────────┐│
│  │                    Manager Layer                                 ││
│  │                           │                                      ││
│  │  ┌────────────────────────┴────────────────────────────────┐    ││
│  │  │                                                         │    ││
│  │  │  PlayerDataManager ──────┬──► NotificationManager [NEW] │    ││
│  │  │      │                   │                              │    ││
│  │  │      │                   └──► MetricsManager [NEW]      │    ││
│  │  │      │                                                  │    ││
│  │  │      └──────────────────────► DataMigrationManager [NEW]│    ││
│  │  │                                                         │    ││
│  │  └─────────────────────────────────────────────────────────┘    ││
│  │                           │                                      ││
│  │  SpawnManager ────────────┴──────► MetricsManager [NEW]         ││
│  │                                                                  ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              │                                       │
│  ┌───────────────────────────┼─────────────────────────────────────┐│
│  │                    Storage Layer                                 ││
│  │                           │                                      ││
│  │  Storage (interface)                                             ││
│  │    ├─ exportPlayer(UUID): CompletableFuture<ExportData> [NEW]   ││
│  │    ├─ exportAll(): CompletableFuture<List<ExportData>> [NEW]    ││
│  │    └─ importPlayer(ExportData): CompletableFuture<Void> [NEW]   ││
│  │                           │                                      ││
│  │  SQLiteStorage ───────────┴─── implements new methods           ││
│  │  MySQLStorage ────────────────  implements new methods           ││
│  │                                                                  ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                      Model Layer                                 ││
│  │                                                                  ││
│  │  ExportFormat.java [NEW] — Portable data transfer format        ││
│  │  NotificationStyle.java [NEW] — CHAT, ACTION_BAR, etc.          ││
│  │                                                                  ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Suggested Build Order

1. **NotificationManager + Progress Notifications** — Low complexity, high player visibility
   - Rationale: Immediate UX improvement, touches minimal existing code, can be disabled via config
   - Components: NotificationManager, ConfigManager additions, hook in ItemUseListener
   - Risk: LOW

2. **Admin force-complete enhancement** — Minimal work, existing command
   - Rationale: Already 90% implemented, just add markComplete() call and reward flag
   - Components: CollectionsCommand modification only
   - Risk: LOW

3. **MetricsManager + /collections metrics** — Foundational for observability
   - Rationale: Needed before external integrations, provides operator visibility
   - Components: MetricsManager, hook points in managers, new command
   - Risk: LOW

4. **Export/Import Commands** — Most complex, touches storage layer
   - Rationale: Requires careful format design, validation, and error handling
   - Components: ExportFormat, DataMigrationManager, Storage interface extension
   - Risk: MEDIUM (data corruption risk if format is wrong)

5. **External Metrics Integration** (optional) — bStats/Prometheus
   - Rationale: Only after in-game metrics verified working
   - Components: bStats adapter, optional Prometheus exporter
   - Risk: LOW (additive, no existing code changes)

---

## New vs Modified Components Summary

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| NotificationManager | NEW | manager/ | Progress notifications, rate limiting |
| MetricsManager | NEW | manager/ | Counter aggregation, metrics endpoint |
| DataMigrationManager | NEW | manager/ | Export/import orchestration |
| ExportFormat | NEW | model/ | JSON/YAML portable data format |
| NotificationStyle | NEW | model/ | Enum for notification display types |
| CollectionsCommand | MODIFY | command/ | Add export, import, metrics subcommands; fix complete |
| Storage | MODIFY | storage/ | Add export/import method signatures |
| SQLiteStorage | MODIFY | storage/ | Implement export/import methods |
| MySQLStorage | MODIFY | storage/ | Implement export/import methods |
| ConfigManager | MODIFY | config/ | Add notification settings and messages |
| ItemUseListener | MODIFY | listener/ | Hook for progress notification after journal add |
| PlayerDataManager | MODIFY | manager/ | Add getAllCachedPlayerIds() if needed |

---

## Confidence

**HIGH** — The existing architecture is well-documented, follows clear patterns, and the new features fit naturally into established extension points:

- Storage interface pattern already proven with SQLite/MySQL
- Manager layer provides clean separation for new NotificationManager and MetricsManager
- Command layer uses Brigadier with established patterns for new subcommands
- ConfigManager already handles MiniMessage templates for notifications
- All async patterns use CompletableFuture consistently

The main risk is the export/import format design, which requires careful consideration for version compatibility and data integrity during import. This should be the last feature implemented to allow time for format specification review.

---

## Sources

- Codebase analysis: Collections.java, PlayerDataManager.java, Storage.java, CollectionsCommand.java
- Existing documentation: .planning/codebase/ARCHITECTURE.md
- Project context: .planning/PROJECT.md (v1.1 requirements)
