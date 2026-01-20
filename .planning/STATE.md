# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-20)

**Core value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.
**Current focus:** Phase 4 - Memory Management

## Current Position

Phase: 4 of 9 (Memory Management)
Plan: 0 of 3 in current phase
Status: Ready to plan
Last activity: 2026-01-21 - Completed Phase 3 (GUI Safety)

Progress: [████░░░░░░] 33%

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: 7 min
- Total execution time: 60 min

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-data-integrity-hardening | 3 | 32 min | 11 min |
| 02-concurrency-safety | 3 | 21 min | 7 min |
| 03-gui-safety | 3 | 7 min | 2 min |

**Recent Trend:**
- Last 5 plans: 10 min, 3 min, 1 min, 2 min, 4 min
- Trend: Fast execution

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
Stopped at: Completed Phase 3 (GUI Safety)
Resume file: None
