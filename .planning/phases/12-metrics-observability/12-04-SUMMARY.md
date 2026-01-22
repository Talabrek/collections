---
phase: 12-metrics-observability
plan: 04
subsystem: metrics
tags: [database-persistence, counter-storage, periodic-save]

# Dependency graph
requires:
  - phase: 12-metrics-observability
    plan: 02
    provides: MetricsManager with thread-safe AtomicLong counters
provides:
  - Metrics table in SQLite and MySQL storage
  - Counter persistence across server restarts
  - Periodic saves every 5 minutes for crash protection
affects: [metrics-reporting, bStats charts, admin monitoring]

# Tech tracking
tech-stack:
  added: []
  patterns: [storage-interface-extension, periodic-scheduler-task]

key-files:
  created: []
  modified:
    - src/main/java/com/blockworlds/collections/storage/Storage.java
    - src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java
    - src/main/java/com/blockworlds/collections/storage/MySQLStorage.java
    - src/main/java/com/blockworlds/collections/metrics/MetricsManager.java
    - src/main/java/com/blockworlds/collections/Collections.java

key-decisions:
  - "Dedicated executor for metrics operations to avoid blocking main pool"
  - "MySQL key column quoted as reserved word"
  - "Periodic save every 5 minutes as crash protection"
  - "Blocking save on shutdown with 10 second timeout"

patterns-established:
  - "Storage interface extension pattern for new data types"
  - "GlobalRegionScheduler for periodic background tasks"

# Metrics
duration: 5min
completed: 2026-01-22
---

# Phase 12 Plan 04: Database Persistence for Metrics Counters Summary

**Metrics counters persist to database with load on startup, periodic save, and final save on shutdown**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-22
- **Completed:** 2026-01-22
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- Added getMetric(), setMetric(), getAllMetrics() to Storage interface
- Implemented metrics table and operations in SQLiteStorage (INSERT OR REPLACE)
- Implemented metrics table and operations in MySQLStorage (ON DUPLICATE KEY UPDATE)
- Added loadCounters() to restore state on MetricsManager construction
- Added saveCounters() to persist all 5 counters to database
- Added startPeriodicSave() using GlobalRegionScheduler (every 5 minutes)
- Added shutdown() method with task cancellation and blocking final save
- Integrated MetricsManager.shutdown() in Collections.onDisable() before storage.shutdown()

## Task Commits

Each task was committed atomically:

1. **Task 1: Add metrics methods to Storage interface and implementations** - `343eec3` (feat)
2. **Task 2: Add load/save and periodic save to MetricsManager** - `e164e2d` (feat)
3. **Task 3: Call MetricsManager.shutdown() in plugin onDisable** - `ad504a4` (feat)

## Files Created/Modified

- `src/main/java/com/blockworlds/collections/storage/Storage.java` - Added Map import, getMetric(), setMetric(), getAllMetrics() interface methods
- `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java` - Added metrics table creation, HashMap/ExecutorService imports, dedicated executor, metrics method implementations
- `src/main/java/com/blockworlds/collections/storage/MySQLStorage.java` - Added metrics table creation with quoted key column, HashMap/ExecutorService imports, dedicated executor, MySQL-specific upsert syntax
- `src/main/java/com/blockworlds/collections/metrics/MetricsManager.java` - Added Storage field, ScheduledTask field, loadCounters(), saveCounters(), startPeriodicSave(), shutdown()
- `src/main/java/com/blockworlds/collections/Collections.java` - Added metricsManager.shutdown() call in onDisable() before storage.shutdown()

## Decisions Made

- **Dedicated executor for metrics:** Each storage implementation uses a single-threaded executor for metrics operations to avoid blocking the main connection pool
- **MySQL key quoting:** `key` is a reserved word in MySQL, so column name is quoted with backticks
- **5 minute periodic save:** Balances between crash protection and database load
- **10 second shutdown timeout:** Reasonable wait for final save before allowing shutdown to proceed

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - straightforward implementation with immediate compilation verification.

## Next Phase Readiness

- Counter values now persist across server restarts
- Success criterion "Counter values persist across server restarts" satisfied
- All Phase 12 plans complete (01-04)
- Ready for Phase 13: Export/Import functionality

---
*Phase: 12-metrics-observability*
*Completed: 2026-01-22*
