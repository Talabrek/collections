---
phase: 15-collectible-radar
plan: 01
subsystem: radar
tags: [boss-bar, radar, collectibles, goggles, adventure-api]
requires:
  - phase-14 (tier visibility - EPIC/LEGENDARY tiers and goggle management)
provides:
  - Boss bar radar system for helmet-wearing players
  - Direction indicator to nearest collectible
  - Tier-based color coding
affects:
  - Future phases may extend radar with distance display or compass integration
tech-stack:
  added: []
  patterns:
    - BossBar lifecycle management via ConcurrentHashMap per player UUID
    - Chunk-based spatial lookup for efficient nearby collectible detection
    - Entity scheduler for Folia-compatible periodic updates
key-files:
  created:
    - src/main/java/com/blockworlds/collections/manager/RadarManager.java
    - src/main/java/com/blockworlds/collections/task/RadarTask.java
  modified:
    - src/main/java/com/blockworlds/collections/Collections.java
    - src/main/java/com/blockworlds/collections/listener/ArmorChangeListener.java
    - src/main/java/com/blockworlds/collections/listener/PlayerListener.java
    - src/main/java/com/blockworlds/collections/config/ConfigManager.java
    - src/main/resources/config.yml
decisions:
  - decision: RadarTask accepts RadarManager as constructor parameter
    reason: Allows initialization order independence since RadarManager created before RadarTask
    date: 2026-01-23
  - decision: Direction indicator uses [^], [<], [>] symbols
    reason: Simple ASCII-compatible symbols that work across all clients
    date: 2026-01-23
  - decision: Boss bar color matches highest tier collectible nearby
    reason: Provides visual feedback about what tier collectibles are available
    date: 2026-01-23
metrics:
  duration: ~9 min
  completed: 2026-01-23
---

# Phase 15 Plan 01: Collectible Radar Summary

Boss bar radar system shows nearby collectibles to players wearing collector's helmets.

## What Was Built

### RadarManager (src/main/java/.../manager/RadarManager.java)
- Manages boss bar lifecycle per player via ConcurrentHashMap<UUID, BossBar>
- `showRadar(Player)`: Creates and shows boss bar with initial "Radar Loading..." state
- `hideRadar(Player)`: Removes boss bar when helmet unequipped
- `updateRadar(Player, count, direction, tier)`: Updates display with count, direction indicator, tier color
- `hasRadar(Player)`: Checks if player has active radar
- `cleanup(Player)`: Safe cleanup on disconnect (handles already-disconnected players)
- Color mapping: COMMON=WHITE, UNCOMMON=GREEN, RARE=BLUE, EPIC=PURPLE, LEGENDARY=YELLOW, EVENT=PINK

### RadarTask (src/main/java/.../task/RadarTask.java)
- Scheduled task using GlobalRegionScheduler for Folia compatibility
- Iterates online players with active radars
- Uses chunk-based spatial lookup via `spawnManager.getCollectiblesNearChunk()`
- Filters by: same world, within range, `goggleManager.canPlayerSeeCollectible()`
- Direction calculation: atan2-based angle comparison with player yaw
- Direction indicators: [^] for ahead (within 45 degrees), [<] for left, [>] for right

### Configuration (config.yml)
```yaml
radar:
  enabled: true
  range-blocks: 32
  update-interval-ticks: 10
```

### Integration Points
- **ArmorChangeListener**: Shows radar when goggles equipped, hides when removed
- **PlayerListener**: Cleans up radar boss bar on disconnect (prevents memory leak)
- **Collections.java**: Initializes RadarManager/RadarTask, lifecycle hooks in onEnable/onDisable/reload

## Key Implementation Details

### Tier Visibility Respects Goggle System
The radar uses `goggleManager.canPlayerSeeCollectible()` to filter:
- Basic goggles (UNCOMMON tier): See COMMON + UNCOMMON collectibles on radar
- Master goggles (RARE tier): See COMMON + UNCOMMON + RARE + EPIC + LEGENDARY collectibles on radar

### Performance Considerations
- Chunk-based spatial index (O(nearby_chunks) instead of O(all_collectibles))
- Boss bar updates only for players with active radars
- Configurable update interval (default 10 ticks = 500ms)

## Success Criteria Verification

| Criterion | Status |
|-----------|--------|
| Player with no helmet sees no radar boss bar | Implemented |
| Player with normal helmet sees common+uncommon on radar | Implemented |
| Player with upgraded helmet sees all tiers on radar | Implemented |
| Boss bar appears when helmet equipped | Implemented |
| Boss bar disappears when helmet removed | Implemented |
| Radar updates as player moves | Implemented |
| Boss bar cleaned up on disconnect | Implemented |

## Commits

| Commit | Type | Description |
|--------|------|-------------|
| c937cfa | feat | RadarManager, RadarTask, config (committed with Phase 16 docs) |
| dfad550 | feat | Integration with Collections.java, ArmorChangeListener, PlayerListener |

## Deviations from Plan

### Minor Adjustment
**[Rule 3 - Blocking] RadarTask constructor signature**
- **Found during:** Task 1
- **Issue:** RadarTask called `plugin.getRadarManager()` but getter didn't exist yet
- **Fix:** Changed RadarTask to accept RadarManager as constructor parameter
- **Files modified:** RadarTask.java
- **Commit:** Part of c937cfa

## Notes

### Testing Status
- Code compiles successfully
- 172/176 existing tests pass (4 failures are pre-existing MockBukkit compatibility issues)
- Manual in-game verification recommended for:
  - Boss bar appearance/disappearance on helmet equip/unequip
  - Direction indicator accuracy
  - Tier filtering with different goggles

### Future Enhancements (Out of Scope)
- Distance display to nearest collectible
- Compass integration for better direction indication
- Particle trail pointing to nearest collectible
