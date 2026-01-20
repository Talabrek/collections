---
phase: 02-concurrency-safety
plan: 01
subsystem: concurrency
tags: [race-condition, async-load, blocking-wait, player-data, gui-gating]

# Dependency graph
requires:
  - phase: 01-data-integrity-hardening
    provides: PlayerDataManager with async load/save, pendingLoads tracking
provides:
  - getProgressBlocking() method for thread-safe data access
  - GUI gating pattern with EntityScheduler retry
  - Race condition protection for recently joined players
affects: [02-02, 02-03, future-gui-work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "getProgressBlocking() fast-path cache check before blocking"
    - "EntityScheduler retry pattern for deferred GUI open"
    - "5-second timeout for blocking data waits"

key-files:
  created: []
  modified:
    - src/main/java/com/blockworlds/collections/manager/PlayerDataManager.java
    - src/main/java/com/blockworlds/collections/gui/CollectionMenuGUI.java
    - src/main/java/com/blockworlds/collections/gui/CollectionDetailGUI.java
    - src/main/java/com/blockworlds/collections/command/CollectionsCommand.java
    - src/main/java/com/blockworlds/collections/gui/ConfirmAddGUI.java

key-decisions:
  - "getProgressBlocking() with fast-path cache check - no blocking if data already loaded"
  - "5-second timeout for blocking waits - matches quit save timeout from Phase 1"
  - "EntityScheduler for retry - Folia-compatible, follows entity for scheduling"
  - "1-second retry delay (20 ticks) - balances responsiveness with load completion time"

patterns-established:
  - "GUI gate pattern: call getProgressBlocking() at start of open(), retry on null"
  - "Blocking method naming: getProgressBlocking vs getProgress distinguishes behavior"

# Metrics
duration: 8min
completed: 2026-01-21
---

# Phase 02 Plan 01: Fix Join Race Condition Summary

**getProgressBlocking() method with fast-path cache check, GUI gating with EntityScheduler retry for recently joined players**

## Performance

- **Duration:** 8 min
- **Started:** 2026-01-21T05:30:00Z
- **Completed:** 2026-01-21T05:38:00Z
- **Tasks:** 4
- **Files modified:** 5

## Accomplishments
- Added getProgressBlocking() to PlayerDataManager with fast-path and 5-second timeout
- CollectionMenuGUI and CollectionDetailGUI now gate on data load with retry
- CollectionsCommand list/stats show correct progress for recently joined players
- ConfirmAddGUI recipe unlock and completion checks work for fast players

## Task Commits

Each task was committed atomically:

1. **Task 1: Add getProgressBlocking() to PlayerDataManager** - `d44e647` (feat)
2. **Task 2: Gate GUI open() methods on data load** - `0c6072a` (feat)
3. **Task 3: Gate CollectionsCommand progress lookups** - `5d54dd0` (feat)
4. **Task 4: Gate ConfirmAddGUI progress lookups** - `117665d` (feat)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/manager/PlayerDataManager.java` - Added getProgressBlocking() method
- `src/main/java/com/blockworlds/collections/gui/CollectionMenuGUI.java` - Gate open() on data load
- `src/main/java/com/blockworlds/collections/gui/CollectionDetailGUI.java` - Gate open() on data load
- `src/main/java/com/blockworlds/collections/command/CollectionsCommand.java` - Use getProgressBlocking() in list/stats
- `src/main/java/com/blockworlds/collections/gui/ConfirmAddGUI.java` - Use getProgressBlocking() in confirmAdd/checkComplete

## Decisions Made
- **Fast-path cache check:** getProgressBlocking() checks cache first, returns immediately if data present - no blocking overhead for common case
- **5-second timeout:** Matches quit save timeout from Phase 1, provides consistency across blocking operations
- **EntityScheduler for retry:** Used player.getScheduler().runDelayed() instead of BukkitScheduler - Folia-compatible pattern
- **1-second retry delay:** 20 ticks provides good balance between responsiveness and giving async load time to complete

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all tasks completed without issues.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Race condition for join timing fixed
- Ready for Phase 02-02 (Collection modification thread safety)
- Pattern established for future GUI gating needs

---
*Phase: 02-concurrency-safety*
*Plan: 01*
*Completed: 2026-01-21*
