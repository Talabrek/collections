---
phase: 12-metrics-observability
plan: 01
subsystem: metrics
tags: [bstats, atomic-counters, analytics, thread-safe]

# Dependency graph
requires:
  - phase: 10-progress-notifications
    provides: NotificationManager for player feedback
provides:
  - MetricsManager with thread-safe AtomicLong counters
  - bStats community analytics integration
  - Custom charts for storage_type, collection_count, spawn_success_rate
  - Config section for metrics control
affects: [12-02 hook points, admin commands, debugging]

# Tech tracking
tech-stack:
  added: [bstats-bukkit:3.1.0]
  patterns: [AtomicLong for thread-safe counters, bStats custom charts]

key-files:
  created:
    - src/main/java/com/blockworlds/collections/metrics/MetricsManager.java
  modified:
    - build.gradle.kts
    - src/main/java/com/blockworlds/collections/Collections.java
    - src/main/resources/config.yml

key-decisions:
  - "bStats relocated to avoid conflicts with other plugins"
  - "AtomicLong counters for thread-safe async access"
  - "Placeholder plugin ID 12345 until registration"

patterns-established:
  - "Metrics counters: increment via recordX() methods, read via getX()"
  - "bStats custom charts: SimplePie with lambda value suppliers"

# Metrics
duration: 6min
completed: 2026-01-22
---

# Phase 12 Plan 01: MetricsManager Foundation Summary

**bStats 3.1.0 integration with thread-safe AtomicLong counters for items, completions, and spawn tracking**

## Performance

- **Duration:** 6 min
- **Started:** 2026-01-22T15:42:00Z
- **Completed:** 2026-01-22T15:48:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- Added bStats 3.1.0 dependency with proper relocation to avoid plugin conflicts
- Created MetricsManager with 5 AtomicLong counters (itemsCollected, collectionsCompleted, spawnAttempts, spawnSuccesses, spawnFailures)
- Implemented bStats custom charts for storage_type, collection_count buckets, and spawn_success_rate buckets
- Integrated MetricsManager into plugin lifecycle with config control

## Task Commits

Each task was committed atomically:

1. **Task 1: Add bStats dependency with relocation** - `a6bb289` (chore)
2. **Task 2: Create MetricsManager with counters and bStats** - `1d4d807` (feat)
3. **Task 3: Add metrics config and initialize in plugin** - `02063eb` (feat)

## Files Created/Modified

- `build.gradle.kts` - Added bstats-bukkit:3.1.0 dependency and relocation rule
- `src/main/java/com/blockworlds/collections/metrics/MetricsManager.java` - New class with counters and bStats (189 lines)
- `src/main/java/com/blockworlds/collections/Collections.java` - Added metricsManager field, initialization, and getter
- `src/main/resources/config.yml` - Added metrics section with enabled and bstats-id options

## Decisions Made

- **bStats relocated:** Shaded to com.blockworlds.collections.lib.bstats to avoid version conflicts with other plugins
- **Placeholder plugin ID:** Using 12345 as default until registered at bstats.org
- **Spawn success rate default:** Returns 100% when no attempts recorded to avoid NaN/division issues
- **Counters not persisted:** Session-only tracking - values reset on server restart (appropriate for rate metrics)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Gradle dependency command required Windows-specific invocation (cmd //c with _JAVA_OPTIONS cleared)
- Deprecation warnings exist in unrelated files (CollectionDetailGUI, ArmorChangeListener) but don't affect new code

## User Setup Required

**External service requires manual configuration:**

1. Register plugin at https://bstats.org/
2. Select "Bukkit" platform
3. Get numeric plugin ID
4. Update config.yml: `metrics.bstats-id: [your-id]`

The plugin works with placeholder ID 12345 but analytics won't appear on bStats dashboard until registered.

## Next Phase Readiness

- MetricsManager accessible via `plugin.getMetricsManager()`
- Counter methods ready for hook points in Plan 02:
  - `recordItemCollected()` - to hook in ItemUseListener
  - `recordCollectionCompleted()` - to hook in ItemUseListener
  - `recordSpawnAttempt(boolean)` - to hook in SpawnManager
- Config controls metrics.enabled toggle for opt-out

---
*Phase: 12-metrics-observability*
*Completed: 2026-01-22*
