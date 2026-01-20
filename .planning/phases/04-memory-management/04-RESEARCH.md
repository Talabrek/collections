# Phase 4: Memory Management - Research

**Researched:** 2026-01-21
**Domain:** Java Memory Management, Bukkit Plugin Lifecycle
**Confidence:** HIGH

## Summary

Phase 4 addresses memory leaks that can cause the plugin to consume increasing memory during extended operation. The audit identified four specific memory management requirements:

1. **Cooldown map leak** (MEM-01): The `lastCollectTime` map in CollectibleInteractListener is cleaned on quit, but the `collectLocks` map is NOT cleaned.
2. **Task cancellation** (MEM-02): All ScheduledTask instances are properly cancelled in onDisable().
3. **Per-player map cleanup** (MEM-03): Some maps are cleaned, but not all cleanup happens on all quit paths.
4. **Player object retention** (MEM-04): Three GUI classes store `Player` references directly instead of UUIDs.

**Primary recommendation:** Fix all identified leaks with minimal code changes. Each fix is straightforward and low-risk.

## Current Implementation Analysis

### 1. Cooldown Maps (MEM-01)

**File:** `CollectibleInteractListener.java`

```java
// Line 45-48
private final Map<UUID, Long> lastCollectTime = new ConcurrentHashMap<>();
private final Map<UUID, AtomicBoolean> collectLocks = new ConcurrentHashMap<>();
```

**Current cleanup (Line 246-248):**
```java
public void cleanupPlayer(UUID playerId) {
    lastCollectTime.remove(playerId);
    // BUG: collectLocks is NOT cleaned here
}
```

**Issue:** The `collectLocks` map stores per-player AtomicBoolean lock state. If a player disconnects while mid-collection, the lock entry remains in memory forever.

**Impact:** LOW per-player (single AtomicBoolean = ~32 bytes), but unbounded growth over time.

### 2. Scheduled Tasks (MEM-02)

**File:** `Collections.java` (main plugin class)

**Task tracking:**
| Task | Field | Cancelled in onDisable? |
|------|-------|------------------------|
| ParticleTask | `particleTask` | YES (line 155-157) |
| ActionBarPromptTask | `actionBarPromptTask` | YES (line 160-162) |
| SpawnManager (spawn) | `spawnTask` | YES via shutdown() (line 165-167) |
| SpawnManager (validity) | `validityTask` | YES via shutdown() (line 165-167) |

**Analysis:** All four ScheduledTask instances are properly cancelled:

```java
// Collections.java onDisable()
if (particleTask != null) particleTask.stop();
if (actionBarPromptTask != null) actionBarPromptTask.stop();
if (spawnManager != null) spawnManager.shutdown();
```

**SpawnManager.shutdown() (Line 91-98):**
```java
public void shutdown() {
    if (spawnTask != null) spawnTask.cancel();
    if (validityTask != null) validityTask.cancel();
}
```

**Status:** COMPLIANT - No fix needed for MEM-02.

### 3. Per-Player Maps (MEM-03)

**Identified per-player Maps:**

| Map | Class | Cleaned on Quit? | Cleaned in onDisable? |
|-----|-------|------------------|----------------------|
| `cache` (PlayerProgress) | PlayerDataManager | YES | YES (saveAll) |
| `pendingLoads` | PlayerDataManager | YES | YES (saveAll clears) |
| `lastCollectTime` | CollectibleInteractListener | YES | NO |
| `collectLocks` | CollectibleInteractListener | NO | NO |
| `openGuis` | GUIManager | YES | NO |

**Cleanup call chain on quit:**

```
PlayerQuitEvent
  |
  +-> PlayerListener.onPlayerQuit()
  |     +-> CollectibleInteractListener.cleanupPlayer(uuid)  // partial cleanup
  |     +-> PlayerDataManager.saveAndUnload(uuid)
  |
  +-> GUIListener.onPlayerQuit()
        +-> GUIManager.cleanupPlayer(uuid)
```

**Issues found:**

1. **collectLocks not cleaned** in CollectibleInteractListener.cleanupPlayer()
2. **No onDisable cleanup** for CollectibleInteractListener or GUIManager

During server shutdown, players are not processed through PlayerQuitEvent, so any remaining entries in these maps are not cleaned. However, since the JVM is shutting down, this is only a concern during `/reload`.

**Recommendation:** Add cleanup methods to clear all per-player data during plugin disable.

### 4. Player Object Retention (MEM-04)

**Files storing Player references:**

| Class | Field | Type | Issue |
|-------|-------|------|-------|
| CollectionMenuGUI | `player` | Player | Retained for GUI lifetime |
| CollectionDetailGUI | `player` | Player | Retained for GUI lifetime |
| ConfirmAddGUI | `player` | Player | Retained for GUI lifetime |

**Code examples:**

```java
// CollectionMenuGUI.java line 36
private final Player player;

// CollectionDetailGUI.java line 39
private final Player player;

// ConfirmAddGUI.java line 33
private final Player player;
```

**Analysis:**

These Player references are held only for the duration the GUI is open. When the inventory is closed:
1. GUIListener.onInventoryClose() is called
2. GUIManager.unregisterGUI(uuid) removes the GUIHolder
3. The GUI object becomes eligible for garbage collection

**Risk Assessment:**
- If player disconnects with GUI open, GUIListener.onPlayerQuit() calls GUIManager.cleanupPlayer()
- The GUIHolder (containing Player ref) is removed from openGuis map
- Player object should be GC'd after disconnect completes

**Verdict:** LOW RISK - The current design is acceptable for GUI classes since:
1. GUIs are short-lived (seconds to minutes)
2. Cleanup happens on close AND on quit
3. Converting to UUID would require additional lookups on every action

However, the code pattern should be audited for consistency. The issue is more about code hygiene than actual memory leaks.

## Maps That DO NOT Store Per-Player Data

For completeness, these maps were reviewed and found to NOT store per-player data:

| Map | Class | Keys |
|-----|-------|------|
| `activeCollectibles` | SpawnManager | Collectible UUIDs |
| `collectibleCountByZone` | SpawnManager | Zone IDs (String) |
| `respawnTimers` | SpawnManager | Zone IDs (String) |
| `activeEvents` | EventManager | Event names (String) |

## Identified Memory Leak Vectors

### MEM-01: Cooldown Map Leak

**Location:** `CollectibleInteractListener.java:48`
**Map:** `collectLocks`
**Type:** Unbounded growth
**Fix:** Add `collectLocks.remove(playerId)` to `cleanupPlayer()` method

### MEM-02: Scheduled Task Cancellation

**Status:** COMPLIANT - No fix needed

### MEM-03: Per-Player Map Cleanup

**Issue 1:** `collectLocks` not cleaned (same as MEM-01)
**Issue 2:** No onDisable cleanup path for listener/GUI manager maps

**onDisable cleanup needed for:**
- CollectibleInteractListener: clear `lastCollectTime` and `collectLocks`
- GUIManager: clear `openGuis`

### MEM-04: Player Object Retention

**Status:** ACCEPTABLE - GUI classes hold Player refs only during GUI lifetime, with proper cleanup on close/quit.

**Recommendation:** Document this as an intentional pattern rather than convert to UUID, as:
1. Current implementation is safe
2. Converting would add complexity
3. No actual memory leak exists

## Recommended Fixes

### Fix 1: Complete collectLocks cleanup (MEM-01, MEM-03)

**File:** `CollectibleInteractListener.java`

```java
public void cleanupPlayer(UUID playerId) {
    lastCollectTime.remove(playerId);
    collectLocks.remove(playerId);  // ADD THIS LINE
}
```

### Fix 2: Add onDisable cleanup method (MEM-03)

**File:** `CollectibleInteractListener.java`

```java
/**
 * Clear all player data. Called on plugin disable.
 */
public void shutdown() {
    lastCollectTime.clear();
    collectLocks.clear();
}
```

**File:** `Collections.java` (onDisable)

```java
// Before saving player data
if (collectibleInteractListener != null) {
    collectibleInteractListener.shutdown();
}
```

### Fix 3: Add GUIManager onDisable cleanup (MEM-03)

**File:** `GUIManager.java`

```java
/**
 * Clear all tracked GUIs. Called on plugin disable.
 */
public void shutdown() {
    openGuis.clear();
}
```

**File:** `Collections.java` (onDisable)

```java
if (guiManager != null) {
    guiManager.shutdown();
}
```

## Dependencies/Risks

### Dependencies

- Phase 2 (Concurrency Safety) must be complete - DONE
- No blocking dependencies from other phases

### Risks

| Risk | Mitigation |
|------|------------|
| Cleanup order matters | Clear player maps before saving data |
| Race condition on shutdown | Use ConcurrentHashMap.clear() which is atomic |
| Reload scenario | Ensure all cleanup runs before re-initialization |

### Testing Strategy

1. **Manual test:** Join server, collect item, quit, rejoin 100+ times
2. **Verify:** Check cache sizes via debug command or JMX
3. **Reload test:** `/reload` should not leak memory

## Code Examples

### Pattern: Cleanup on Quit + Disable

```java
// In listener class
private final Map<UUID, SomeData> playerData = new ConcurrentHashMap<>();

public void cleanupPlayer(UUID playerId) {
    playerData.remove(playerId);
}

public void shutdown() {
    playerData.clear();
}
```

### Pattern: Exposing cleanup in main plugin

```java
// In main plugin class
private CollectibleInteractListener interactListener;

@Override
public void onDisable() {
    // Cleanup listeners
    if (interactListener != null) {
        interactListener.shutdown();
    }

    // Then save data...
}
```

## Open Questions

None - all requirements can be fully addressed with the identified fixes.

## Sources

### Primary (HIGH confidence)
- Direct code inspection of Collections plugin source
- Prior phase research and implementation

### Secondary (MEDIUM confidence)
- Paper API documentation for ScheduledTask lifecycle
- Java ConcurrentHashMap documentation

## Metadata

**Confidence breakdown:**
- Cooldown map analysis: HIGH - Direct code inspection
- Task cancellation analysis: HIGH - Traced all paths
- Per-player map analysis: HIGH - Comprehensive grep + inspection
- Player object retention: HIGH - Reviewed all GUI classes

**Research date:** 2026-01-21
**Valid until:** Indefinite (codebase-specific analysis)
