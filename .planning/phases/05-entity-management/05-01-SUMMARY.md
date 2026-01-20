---
phase: 05-entity-management
plan: 01
subsystem: entity-tracking
tags: [bukkit-events, entityremoveevent, concurrent-hashmap, o1-lookup, pdc]

# Dependency graph
requires:
  - phase: 04-memory-management
    provides: Memory-safe patterns and cleanup mechanisms
provides:
  - EntityRemoveEvent handler for all removal causes
  - Dual-index tracking (activeCollectibles + entityToCollectible)
  - O(1) entity-to-collectible lookup
  - handleEntityRemoved() API for external entity removal events
affects: [05-02-chunk-respawn, future-collectible-tracking]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual-index tracking for O(1) bidirectional lookup"
    - "EntityRemoveEvent MONITOR priority for state sync"
    - "PDC fast-fail check before map operations"

key-files:
  created:
    - src/main/java/com/blockworlds/collections/listener/EntityRemoveListener.java
  modified:
    - src/main/java/com/blockworlds/collections/manager/SpawnManager.java
    - src/main/java/com/blockworlds/collections/Collections.java

key-decisions:
  - "Dual-index tracking: entityToCollectible ConcurrentHashMap provides O(1) reverse lookup"
  - "MONITOR priority for EntityRemoveListener: Observe without interfering with other handlers"
  - "PDC check before map lookup: Fast-fail for non-collectible entities"
  - "UNLOAD cause distinction: Mark unspawned but keep tracking vs permanent removal"

patterns-established:
  - "Dual-index pattern: When bidirectional lookup needed, maintain two maps with synchronized updates"
  - "Index desync cleanup: If secondary index has stale entry, clean up in lookup method"

# Metrics
duration: 6min
completed: 2026-01-21
---

# Phase 5 Plan 01: Entity Removal Tracking Summary

**EntityRemoveEvent listener with dual-index tracking for O(1) entity-to-collectible lookup and all removal cause handling**

## Performance

- **Duration:** 6 min
- **Started:** 2026-01-21T00:00:00Z
- **Completed:** 2026-01-21T00:06:00Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- Added entityToCollectible dual-index for O(1) reverse lookup (was O(n) iteration)
- Created EntityRemoveListener handling all EntityRemoveEvent causes
- Distinguishes UNLOAD (temporary) from permanent removals (PLUGIN, DEATH, DESPAWN, etc.)
- No more orphaned tracking entries when entities removed by /kill or plugins

## Task Commits

Each task was committed atomically:

1. **Task 1: Add entityToCollectible index to SpawnManager** - `1baaa21` (feat)
2. **Task 2: Create EntityRemoveListener** - `f485c19` (feat)
3. **Task 3: Register EntityRemoveListener in main plugin class** - `7b39893` (feat)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/listener/EntityRemoveListener.java` - New listener for all entity removal scenarios
- `src/main/java/com/blockworlds/collections/manager/SpawnManager.java` - Added entityToCollectible index and handleEntityRemoved() method
- `src/main/java/com/blockworlds/collections/Collections.java` - Register EntityRemoveListener

## Decisions Made
- **Dual-index pattern:** Using entityToCollectible ConcurrentHashMap provides O(1) lookup instead of O(n) iteration through activeCollectibles
- **MONITOR priority:** EntityRemoveListener uses MONITOR priority to observe without interfering with other handlers
- **PDC fast-fail:** Check PersistentDataContainer for COLLECTIBLE_KEY before map lookup to avoid unnecessary work for non-collectible entities
- **UNLOAD vs permanent:** UNLOAD cause marks collectible unspawned (ChunkListener backup), all other causes trigger full despawn and database removal

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Entity removal tracking complete - all causes handled
- Ready for 05-02 (chunk respawn optimization)
- entityToCollectible index enables efficient entity lookup in all listeners

---
*Phase: 05-entity-management*
*Completed: 2026-01-21*
