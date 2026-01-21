---
phase: 07-code-quality
plan: 03
subsystem: utilities
tags: [refactor, code-deduplication, utility-classes]

dependency_graph:
  requires: [07-02]
  provides: [LocationUtils, SpawnConditionParser]
  affects: [future-spawn-system-changes]

tech_stack:
  added: []
  patterns:
    - Utility class pattern with private constructor
    - Delegation pattern for backward compatibility
    - parseOrNull() variant for null-returning semantics

key_files:
  created:
    - src/main/java/com/blockworlds/collections/util/LocationUtils.java
    - src/main/java/com/blockworlds/collections/util/SpawnConditionParser.java
  modified:
    - src/main/java/com/blockworlds/collections/manager/ZoneManager.java
    - src/main/java/com/blockworlds/collections/manager/CollectionManager.java
    - src/main/java/com/blockworlds/collections/spawn/AdaptiveSpawnFinder.java

decisions:
  - id: UTIL-01
    description: "Use parseOrNull() for CollectionManager, parse() for ZoneManager"
    rationale: "CollectionManager returns null for no restrictions, ZoneManager returns NONE"
  - id: UTIL-02
    description: "Mark ZoneManager.parseSpawnConditions() as @Deprecated"
    rationale: "Keep public method for backward compatibility but direct to utility class"
  - id: UTIL-03
    description: "Keep private wrapper methods in callers"
    rationale: "Cleaner delegation with descriptive method names in calling code"

metrics:
  duration: 4 min
  completed: 2026-01-21
---

# Phase 7 Plan 3: Extract Utility Methods Summary

Extracted duplicated spawn condition parsing and surface location finding into shared utility classes.

## One-Liner

LocationUtils and SpawnConditionParser consolidate duplicated spawn logic into single-source-of-truth utilities.

## What Was Done

### Task 1: Create LocationUtils utility class
- Created `LocationUtils.java` with three static methods:
  - `findSurfaceLocation(World, int, int, SpawnConditions)` - finds valid surface at X,Z
  - `isStandableLocation(Location)` - checks air-with-solid-below
  - `hasBlockAbove(Location)` - detects underground via solid ceiling check
- Based on AdaptiveSpawnFinder version (includes null world safety check)
- Commit: a41f125

### Task 2: Create SpawnConditionParser utility class
- Created `SpawnConditionParser.java` with two static methods:
  - `parse(ConfigurationSection, Logger)` - returns NONE for null (zone-level)
  - `parseOrNull(ConfigurationSection, Logger)` - returns null for null (item-level)
- Consolidated biome, dimension, time, and Y-level parsing
- Centralized warning logging for invalid enum values
- Commit: 2026333

### Task 3: Update callers to use utility classes
- **ZoneManager**: Delegates to both utilities
  - `parseSpawnConditions()` now calls `SpawnConditionParser.parse()`
  - `findSurfaceLocation()` and `isStandableLocation()` call LocationUtils
  - Removed `hasBlockAbove()` (no longer called directly)
  - Marked `parseSpawnConditions()` as @Deprecated
- **CollectionManager**: Delegates to SpawnConditionParser
  - `parseSpawnConditions()` now calls `SpawnConditionParser.parseOrNull()`
- **AdaptiveSpawnFinder**: Delegates to LocationUtils
  - All three location methods delegate to utility class
- Commit: 97c1509

## Deviations from Plan

None - plan executed exactly as written.

## Code Quality Impact

**Before:**
- Spawn condition parsing duplicated in ZoneManager and CollectionManager
- Surface location finding duplicated in ZoneManager and AdaptiveSpawnFinder
- 189 lines of duplicated code

**After:**
- Single source of truth for each utility
- 3 files delegate to 2 utility classes
- Code reduced by 176 net lines (189 removed, 13 added for delegation)

## Key Links Verified

| From | To | Pattern |
|------|-----|---------|
| ZoneManager.findSpawnLocation() | LocationUtils | LocationUtils.findSurfaceLocation |
| AdaptiveSpawnFinder.findLocation() | LocationUtils | LocationUtils.findSurfaceLocation |
| CollectionManager.parseCollection() | SpawnConditionParser | SpawnConditionParser.parseOrNull |
| ZoneManager.parseZone() | SpawnConditionParser | SpawnConditionParser.parse |

## Files Changed

| File | Change |
|------|--------|
| util/LocationUtils.java | Created - 84 lines |
| util/SpawnConditionParser.java | Created - 92 lines |
| manager/ZoneManager.java | Modified - delegates to utilities |
| manager/CollectionManager.java | Modified - delegates to SpawnConditionParser |
| spawn/AdaptiveSpawnFinder.java | Modified - delegates to LocationUtils |

## Next Phase Readiness

Phase 7 complete. Ready for Phase 8 (Multi-Server Support) or Phase 9 (Testing/Documentation).
