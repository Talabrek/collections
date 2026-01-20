---
phase: 02-concurrency-safety
plan: 02
subsystem: scheduling
tags: [folia, entityscheduler, concurrency, paper-api]

# Dependency graph
requires:
  - phase: 01-data-integrity-hardening
    provides: "Safe async database operations with blocking quit saves"
provides:
  - "Folia-compatible entity scheduling in PlayerListener"
  - "Folia-compatible firework scheduling in RewardManager"
  - "Folia-compatible armor change handling in ArmorChangeListener"
affects: [03-memory-optimization, future-folia-support]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "EntityScheduler for player-scoped operations"
    - "EntityScheduler for all entity types (players, fireworks)"
    - "Retired callback pattern (null for ignore)"

key-files:
  created: []
  modified:
    - "src/main/java/com/blockworlds/collections/listener/PlayerListener.java"
    - "src/main/java/com/blockworlds/collections/manager/RewardManager.java"
    - "src/main/java/com/blockworlds/collections/listener/ArmorChangeListener.java"

key-decisions:
  - "EntityScheduler over RegionScheduler for players - follows entity across regions"
  - "Null retired callback - no action needed when player logs out before task runs"
  - "Remove redundant isOnline() checks - EntityScheduler handles via retired callback"

patterns-established:
  - "player.getScheduler().run(plugin, task -> { ... }, null) for immediate player tasks"
  - "player.getScheduler().runDelayed(plugin, task -> { ... }, null, ticks) for delayed player tasks"
  - "entity.getScheduler().runDelayed() for any entity-scoped delayed operations"

# Metrics
duration: 4min
completed: 2026-01-21
---

# Phase 2 Plan 2: EntityScheduler Migration Summary

**All BukkitScheduler usage migrated to Folia-compatible EntityScheduler for player and entity operations**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-21T00:00:00Z
- **Completed:** 2026-01-21T00:04:00Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- Migrated PlayerListener from BukkitScheduler to EntityScheduler for both recipe unlock and visibility refresh
- Migrated RewardManager firework detonation from BukkitScheduler to EntityScheduler
- Migrated ArmorChangeListener from RegionScheduler to EntityScheduler
- Removed redundant isOnline() checks (handled by EntityScheduler retired callback)
- Cleaned up unused imports (Bukkit, EquipmentSlot)

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate PlayerListener to EntityScheduler** - `ca4509c` (feat)
2. **Task 2: Migrate RewardManager firework to EntityScheduler** - `f8affe3` (feat)
3. **Task 3: Migrate ArmorChangeListener to EntityScheduler** - `0fc103c` (feat)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/listener/PlayerListener.java` - Recipe unlock and visibility refresh now use player.getScheduler()
- `src/main/java/com/blockworlds/collections/manager/RewardManager.java` - Firework detonation now uses firework.getScheduler()
- `src/main/java/com/blockworlds/collections/listener/ArmorChangeListener.java` - Helmet change handling now uses player.getScheduler()

## Decisions Made
- **EntityScheduler over RegionScheduler:** RegionScheduler schedules based on location at call time, but players can move between event and task execution. EntityScheduler follows the entity, ensuring the task runs on the correct thread regardless of player movement.
- **Null retired callback:** When a player logs out before a scheduled task runs, we simply do nothing (null callback). The task silently cancels, which is correct behavior since the player is gone.
- **Removed isOnline() checks:** EntityScheduler handles player validity internally via the retired callback mechanism, making explicit isOnline() checks redundant.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **Gradle wrapper issue:** The `_JAVA_OPTIONS` environment variable conflicted with gradle's JVM options, causing class loading errors. Resolved by clearing the variable and running gradle wrapper directly via java -cp.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All scheduler migrations complete for the specified files
- No BukkitScheduler.runTask() or runTaskLater() calls remain in modified files
- Plugin is now more Folia-compatible for future multi-threaded region support
- Ready for Plan 02-03 (thread-safe collections in PlayerProgress)

---
*Phase: 02-concurrency-safety*
*Completed: 2026-01-21*
