---
phase: 15-collectible-radar
verified: 2026-01-23T21:45:00Z
status: passed
score: 7/7 must-haves verified
must_haves:
  truths:
    - Player with no helmet sees no radar boss bar
    - Player with normal helmet sees boss bar showing common+uncommon collectibles
    - Player with upgraded helmet sees boss bar showing all tier collectibles
    - Boss bar appears when helmet is equipped
    - Boss bar disappears when helmet is removed
    - Radar updates as player moves and collectibles enter/exit range
    - Boss bar cleaned up when player disconnects
  artifacts:
    - path: src/main/java/com/blockworlds/collections/manager/RadarManager.java
      provides: Boss bar lifecycle management per player
      exports: [showRadar, hideRadar, updateRadar, hasRadar, cleanup]
    - path: src/main/java/com/blockworlds/collections/task/RadarTask.java
      provides: Scheduled radar updates for all helmet-wearing players
      exports: [start, stop]
  key_links:
    - from: RadarTask
      to: RadarManager
      via: updateRadar() calls for each player with helmet
    - from: ArmorChangeListener
      to: RadarManager
      via: showRadar/hideRadar on helmet equip/unequip
    - from: RadarTask
      to: GoggleManager.canPlayerSeeCollectible
      via: tier filtering for visible collectibles
    - from: PlayerListener
      to: RadarManager.cleanup
      via: cleanup on PlayerQuitEvent
---

# Phase 15: Collectible Radar Verification Report

**Phase Goal:** Players wearing collector helmet see a boss bar radar showing nearby collectibles.
**Verified:** 2026-01-23T21:45:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Player with no helmet sees no radar boss bar | VERIFIED | ArmorChangeListener:47-52 checks goggleTier \!= null before showing radar |
| 2 | Player with normal helmet sees boss bar showing common+uncommon collectibles | VERIFIED | RadarTask:144 filters via goggleManager.canPlayerSeeCollectible() which respects tier visibility |
| 3 | Player with upgraded helmet sees boss bar showing all tier collectibles | VERIFIED | Same canPlayerSeeCollectible logic - master goggles (RARE tier) can see all collectibles |
| 4 | Boss bar appears when helmet is equipped | VERIFIED | ArmorChangeListener:49 calls radarManager.showRadar(player) when goggleTier \!= null |
| 5 | Boss bar disappears when helmet is removed | VERIFIED | ArmorChangeListener:51 calls radarManager.hideRadar(player) when goggleTier == null |
| 6 | Radar updates as player moves and collectibles enter/exit range | VERIFIED | RadarTask runs at configurable interval (default 10 ticks), recalculates nearby collectibles each tick |
| 7 | Boss bar cleaned up when player disconnects | VERIFIED | PlayerListener:73-76 calls radarManager.cleanup(player) in onPlayerQuit |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| RadarManager.java | Boss bar lifecycle management | VERIFIED | 165 lines, exports showRadar/hideRadar/updateRadar/hasRadar/cleanup, ConcurrentHashMap for thread safety |
| RadarTask.java | Scheduled radar updates | VERIFIED | 226 lines, exports start/stop, uses GlobalRegionScheduler for Folia compatibility |
| ConfigManager.java | Radar configuration | VERIFIED | Fields radarEnabled/radarRangeBlocks/radarUpdateIntervalTicks with getters |
| config.yml | Radar config section | VERIFIED | radar.enabled, radar.range-blocks, radar.update-interval-ticks |

### Artifact Verification (Three Levels)

#### RadarManager.java
- **Level 1 (Exists):** YES - file exists at expected path
- **Level 2 (Substantive):** YES - 165 lines, no TODOs/FIXMEs/placeholders, complete implementation of all methods
- **Level 3 (Wired):** YES - imported and used in ArmorChangeListener, PlayerListener, RadarTask, Collections.java

#### RadarTask.java
- **Level 1 (Exists):** YES - file exists at expected path
- **Level 2 (Substantive):** YES - 226 lines, no stubs, complete implementation including direction calculation logic
- **Level 3 (Wired):** YES - instantiated in Collections.java, start/stop called in lifecycle methods

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| RadarTask | RadarManager | updateRadar() | WIRED | Line 101: radarManager.updateRadar(player, count, directionIndicator, highestTier) |
| ArmorChangeListener | RadarManager | showRadar/hideRadar | WIRED | Lines 49, 51: radarManager.showRadar/hideRadar |
| RadarTask | GoggleManager | canPlayerSeeCollectible | WIRED | Line 144: goggleManager.canPlayerSeeCollectible(player, collectible) |
| PlayerListener | RadarManager | cleanup | WIRED | Line 75: radarManager.cleanup(player) in onPlayerQuit handler |
| Collections.java | RadarManager/RadarTask | initialization | WIRED | Lines 135-138: creates RadarManager and RadarTask, starts task if enabled |
| Collections.java | RadarTask | lifecycle | WIRED | Lines 185-186 (onDisable), 278-281 (reload): stop/start task |

### Requirements Coverage

| Requirement | Status | Supporting Truths |
|-------------|--------|-------------------|
| RADAR-01: Boss bar displays nearby collectibles when wearing collector helmet | SATISFIED | Truths 2, 3, 4 |
| RADAR-02: Normal helmet radar detects common and uncommon collectibles | SATISFIED | Truth 2 |
| RADAR-03: Upgraded helmet radar detects all collectible tiers | SATISFIED | Truth 3 |
| RADAR-04: Boss bar radar hidden when not wearing any collector helmet | SATISFIED | Truths 1, 5, 7 |

### Anti-Patterns Scan

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none found) | - | - | - | - |

No TODOs, FIXMEs, placeholders, or stub implementations found in any radar-related files.

### Human Verification Required

The following items should be verified in-game as they cannot be fully verified programmatically:

#### 1. Boss Bar Visual Appearance
**Test:** Equip collector goggles and observe boss bar
**Expected:** Boss bar appears with Radar Loading initially, then updates to show count and direction
**Why human:** Visual rendering verification

#### 2. Direction Indicator Accuracy
**Test:** Spawn a collectible, face different directions while observing radar
**Expected:** [^] when facing collectible, [<] when collectible is to the left, [>] when to the right
**Why human:** Requires real player rotation and perception

#### 3. Tier Color Coding
**Test:** Have multiple collectibles of different tiers nearby
**Expected:** Boss bar color matches highest tier (WHITE=Common, GREEN=Uncommon, BLUE=Rare, PURPLE=Epic, YELLOW=Legendary)
**Why human:** Visual color verification

#### 4. Real-time Updates
**Test:** Walk toward/away from collectibles while wearing goggles
**Expected:** Count updates as collectibles enter/exit 32-block range
**Why human:** Dynamic behavior verification

### Compilation Status

- **Compile:** SUCCESS - gradlew.bat compileJava completes without errors
- **Line counts:** RadarManager.java (165 lines), RadarTask.java (226 lines) - both substantive implementations

## Summary

All 7 observable truths verified through code inspection:

1. **Boss bar lifecycle** - RadarManager properly creates, updates, and removes boss bars
2. **Helmet-based activation** - ArmorChangeListener triggers show/hide based on goggle tier
3. **Tier-based filtering** - RadarTask uses GoggleManager.canPlayerSeeCollectible() for filtering
4. **Spatial awareness** - Chunk-based spatial lookup with configurable range (default 32 blocks)
5. **Direction indication** - atan2-based angle calculation provides accurate direction indicators
6. **Cleanup on disconnect** - PlayerListener ensures no memory leaks from orphaned boss bars
7. **Configuration support** - All radar settings configurable via config.yml

Phase 15 goal achieved: Players wearing collector helmet see a boss bar radar showing nearby collectibles.

---

*Verified: 2026-01-23T21:45:00Z*
*Verifier: Claude (gsd-verifier)*
