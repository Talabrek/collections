---
phase: 12-metrics-observability
plan: 03
subsystem: metrics
tags: [placeholderapi, placeholders, integration, stats]

# Dependency graph
requires:
  - phase: 12-01
    provides: MetricsManager with getItemsCollected() and getCollectionsCompleted()
provides:
  - PlaceholderAPI expansion with player and server stats
  - %collections_completed% and %collections_items% player placeholders
  - %collections_server_total% and %collections_server_completed% server placeholders
  - %collections_progress_<id>% collection-specific placeholder
affects: [scoreboard plugins, hologram plugins, chat formatting]

# Tech tracking
tech-stack:
  added: [placeholderapi:2.11.7]
  patterns: [soft dependency registration, PlaceholderExpansion extension]

key-files:
  created:
    - src/main/java/com/blockworlds/collections/metrics/CollectionsExpansion.java
  modified:
    - build.gradle.kts
    - src/main/resources/paper-plugin.yml
    - src/main/java/com/blockworlds/collections/Collections.java

key-decisions:
  - "PlaceholderAPI as compileOnly soft-depend to avoid runtime dependency"
  - "persist() returns true to survive /papi reload command"
  - "Server placeholders return counters from MetricsManager (session-only)"

patterns-established:
  - "Soft dependency: check isPluginEnabled() before registration"
  - "PlaceholderExpansion: onRequest() handles both player and server placeholders"

# Metrics
duration: 5min
completed: 2026-01-22
---

# Phase 12 Plan 03: PlaceholderAPI Expansion Summary

**PlaceholderAPI integration providing player and server-wide collection statistics for scoreboards, holograms, and chat plugins**

## Performance

- **Duration:** 5 min
- **Started:** 2026-01-22
- **Completed:** 2026-01-22
- **Tasks:** 3
- **Files created:** 1
- **Files modified:** 3

## Accomplishments

- Added PlaceholderAPI 2.11.7 as compileOnly dependency with repository
- Created CollectionsExpansion class (130 lines) extending PlaceholderExpansion
- Implemented player placeholders: %collections_completed%, %collections_items%
- Implemented server placeholders: %collections_server_total%, %collections_server_completed%, %collections_server_active%
- Implemented collection-specific placeholder: %collections_progress_<id>% showing "collected/total"
- Added conditional registration in onEnable() when PlaceholderAPI is present

## Task Commits

Each task was committed atomically:

1. **Task 1: Add PlaceholderAPI dependency** - `8a502d1` (chore)
2. **Task 2: Create CollectionsExpansion class** - `ea4f826` (feat)
3. **Task 3: Register expansion conditionally** - `47f1774` (feat)

## Files Created/Modified

- `build.gradle.kts` - Added PlaceholderAPI repository and compileOnly dependency
- `src/main/resources/paper-plugin.yml` - Added PlaceholderAPI soft dependency with load: BEFORE
- `src/main/java/com/blockworlds/collections/metrics/CollectionsExpansion.java` - New PlaceholderExpansion class (130 lines)
- `src/main/java/com/blockworlds/collections/Collections.java` - Added conditional registration after MetricsManager

## Placeholders Provided

| Placeholder | Type | Description |
|-------------|------|-------------|
| `%collections_completed%` | Player | Number of collections completed |
| `%collections_items%` | Player | Total items collected |
| `%collections_progress_<id>%` | Player | Collection progress (e.g., "3/5") |
| `%collections_server_total%` | Server | Items collected since restart |
| `%collections_server_completed%` | Server | Collections completed since restart |
| `%collections_server_active%` | Server | Currently spawned collectibles |

## Decisions Made

- **Soft dependency pattern:** Using compileOnly + isPluginEnabled() check avoids ClassNotFoundError when PAPI absent
- **persist() returns true:** Expansion survives /papi reload without re-registering on plugin enable
- **Server counters from MetricsManager:** Leverages existing AtomicLong counters from 12-01
- **Collection ID case-preserved:** Only params.toLowerCase() for switch, actual collection ID kept for lookup

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Success Criteria Met

- METRICS-05 complete: Player placeholders %collections_completed% and %collections_items% work
- METRICS-06 complete: Server placeholders %collections_server_total% and %collections_server_completed% work
- Expansion survives /papi reload (persist() returns true)
- Plugin works without PlaceholderAPI installed (soft-depend)

## Next Phase Readiness

Phase 12 complete. All metrics and observability features implemented:
- 12-01: MetricsManager with bStats and counters
- 12-02: Hook points for item collection and spawn tracking
- 12-03: PlaceholderAPI expansion for external display

Ready for Phase 13: Export/Import functionality.

---
*Phase: 12-metrics-observability*
*Completed: 2026-01-22*
