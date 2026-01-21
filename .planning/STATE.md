# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-20)

**Core value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.
**Current focus:** Phase 8 - MySQL Implementation

## Current Position

Phase: 8 of 9 (MySQL Implementation)
Plan: 3 of 4 in current phase
Status: In progress
Last activity: 2026-01-21 - Completed 08-03-PLAN.md (Config Documentation)

Progress: [█████████░] 91%

## Performance Metrics

**Velocity:**
- Total plans completed: 21
- Average duration: 5 min
- Total execution time: 105 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-data-integrity-hardening | 3 | 32 min | 11 min |
| 02-concurrency-safety | 3 | 21 min | 7 min |
| 03-gui-safety | 3 | 7 min | 2 min |
| 04-memory-management | 2 | 7 min | 3.5 min |
| 05-entity-management | 2 | 10 min | 5 min |
| 06-performance-optimization | 3 | 7 min | 2.3 min |
| 07-code-quality | 3 | 10 min | 3.3 min |
| 08-mysql-implementation | 3 | 11 min | 3.7 min |

**Recent Trend:**
- Last 5 plans: 4 min (07-03), 3 min (08-01), 3 min (08-02), 5 min (08-03)
- Trend: Consistent fast execution

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- **5-second timeout for quit saves:** Chosen as safety margin (normal saves <100ms) - prevents indefinite blocking
- **HIGHEST priority for quit handler:** Ensures save runs before other plugins' handlers
- **Blocking .get() pattern:** Use CompletableFuture.get(timeout) for critical saves on quit
- **SQLite WAL mode:** Enables concurrent readers during writes
- **30-second busy_timeout:** Prevents SQLITE_BUSY errors under concurrent access
- **NORMAL synchronous mode:** Balances durability with performance (FULL excessive for game data)
- **SEVERE logging for database errors:** Critical data loss events need operator visibility
- **Transaction wrapping for savePlayer:** All-or-nothing saves prevent inconsistent state
- **SEVERE for player data mutations, WARNING for reads/admin/collectibles:** Exception handling policy
- **CRITICAL: prefix in log messages:** For grep-able log filtering
- **Propagate SQLException as RuntimeException:** Surface exceptions in CompletableFuture chain
- **ConcurrentHashMap for PlayerProgress.collections:** Atomic computeIfAbsent, better concurrent read performance
- **ConcurrentHashMap.newKeySet() for CollectionProgress.collectedItems:** Thread-safe add/contains operations
- **EntityScheduler over RegionScheduler for players:** Follows entity across regions, handles validity via retired callback
- **Null retired callback pattern:** Task silently cancels when entity gone - correct behavior for player logout
- **Cancel event before routing:** Ensures all click types blocked regardless of slot position
- **rawSlot bounds checking:** Use `rawSlot < topSize` to distinguish GUI from player inventory
- **Check all drag slots:** Iterate `getRawSlots()` to catch cross-inventory drags
- **getProgressBlocking() for GUI mutations:** Re-fetch fresh progress data before reward claims, not cached data
- **No inventory pre-check for claims:** Let RewardManager handle overflow by dropping items at feet
- **AtomicBoolean claiming lock with try-finally:** Prevents double-click exploits via compareAndSet pattern
- **State snapshot on open for staleness detection:** Capture completion state in constructor, compare before claim
- **MEM-01 is FALSE POSITIVE:** collectLocks is transient (keyed by collectible UUID), not per-player memory leak
- **Defensive collectLocks.clear() on shutdown:** Even though entries are removed in finally block, clearing handles edge cases
- **Player object retention in GUI acceptable:** Short-lived GUIs with proper cleanup on close/quit
- **Dual-index tracking:** entityToCollectible ConcurrentHashMap provides O(1) reverse lookup
- **MONITOR priority for EntityRemoveListener:** Observe without interfering with other handlers
- **PDC fast-fail check:** Check PersistentDataContainer before map lookup for non-collectible entities
- **UNLOAD vs permanent removal:** UNLOAD marks unspawned but keeps tracking, other causes trigger full despawn
- **Re-fetch before action pattern:** In delayed tasks, re-fetch entity state since it may have changed
- **Intentional dual chunk unload handling:** Both ChunkListener and EntityRemoveListener handle unload for robustness
- **Bukkit.getEntity() in validity task:** Acceptable due to infrequent execution (minutes, not seconds)
- **Safety net validation:** Periodic check catches what events miss (orphaned tracking entries)
- **Lazy iterator pattern:** Generate grid coordinates on-demand via Iterator, not pre-allocated List
- **Reservoir sampling:** Select random subset from iterator without materializing full collection
- **int[] over Location:** Use lightweight int[] for coordinates, Location only when needed for API calls
- **ThreadLocalRandom:** Thread-safe random with better performance than shared Random
- **Bit-packed chunk key:** high 32 bits = chunkX, low 32 bits = chunkZ for unique long key
- **Keep unspawned collectibles in chunk index:** For chunk load lookup without re-indexing
- **Unindex only on permanent removal:** despawn with removeFromDatabase=true triggers unindex
- **PARTICLE_CHUNK_RADIUS = 2:** 5x5 chunk grid (25 lookups) covers 80x80 block area
- **Pattern ^[a-z][a-z0-9_]*$ for valid IDs:** Lowercase letter start, alphanumeric/underscore only
- **ValidationUtils centralized validation:** isValidId() for checking, requireValidId() for fail-fast validation
- **JarFile enumeration for dynamic resource discovery:** Enumerate JAR entries to find bundled YAML files
- **plugin.saveResource() preserves user modifications:** Check existence before extraction
- **Use parseOrNull() for CollectionManager, parse() for ZoneManager:** Different null semantics per caller
- **Mark ZoneManager.parseSpawnConditions() as @Deprecated:** Direct to utility class while maintaining backward compat
- **Fixed pool size for MySQL:** Set minimumIdle = maximumPoolSize for predictable connection behavior
- **30-minute maxLifetime for MySQL:** Less than MySQL wait_timeout (8h) prevents stale connections
- **MySQL performance properties:** cachePrepStmts, useServerPrepStmts, rewriteBatchedStatements
- **Exclude protobuf from mysql-connector-j:** X DevAPI not needed, reduces JAR size
- **Factory pattern for Storage creation:** StorageFactory.createStorage(plugin) based on config type
- **Fallback with warning for unknown DB types:** Unknown database.type defaults to SQLite with warning
- **Separate sqlite:/mysql: config subsections:** Cleaner structure with dedicated section per backend
- **Migration warning in config:** No automatic SQLite to MySQL transfer, operators must handle manually

### Pending Todos

None yet.

### Blockers/Concerns

From research (see .planning/research/SUMMARY.md):
- Multi-server deployment requires MySQL (Phase 8)
- ~~Fire-and-forget saves are CRITICAL data loss vector (Phase 1 priority)~~ FIXED in 01-01

New from execution:
- Pre-existing MockBukkit test failure (IncompatibleClassChangeError) - does not affect functionality, only test suite

## Session Continuity

Last session: 2026-01-21
Stopped at: Completed 08-03-PLAN.md (Config Documentation)
Resume file: None
