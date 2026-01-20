# Phase 2: Concurrency Safety - Research

**Researched:** 2026-01-21
**Domain:** Paper/Folia concurrency, thread-safe player data management
**Confidence:** HIGH

## Summary

This research examines the current concurrency state of the Collections plugin and identifies specific fixes needed to ensure race-free player data access from join to quit. The codebase already uses ConcurrentHashMap and AtomicBoolean patterns correctly in several places, but has specific gaps that need addressing.

The primary issues are:
1. **PlayerListener uses deprecated BukkitScheduler** instead of Folia-compatible schedulers
2. **RewardManager uses deprecated BukkitScheduler** for firework detonation
3. **PlayerProgress internal HashMap is not thread-safe** and is accessed from multiple threads
4. **No verification that player data load completes** before feature access (getProgress() can return null)
5. **ConcurrentHashMap operations in PlayerDataManager** are mostly correct, but loadPlayer has a subtle race

**Primary recommendation:** Add a `pendingLoads` check to `getProgress()` to block-wait if load is in progress, convert PlayerProgress internal collections to thread-safe variants, and migrate all BukkitScheduler usage to EntityScheduler/RegionScheduler.

## Current State Analysis

### PlayerDataManager.java - Mostly Good, One Issue

**Lines 38-68 - loadPlayer():**
```java
return pendingLoads.computeIfAbsent(playerId, id -> {
    CompletableFuture<PlayerProgress> future = storage.loadPlayer(id)
            .orTimeout(30, TimeUnit.SECONDS)
            .thenApply(progress -> {
                cache.put(id, progress);
                pendingLoads.remove(id);
                return progress;
            })
            ...
});
```

**GOOD:** Uses `computeIfAbsent` for atomic pending load creation. This prevents duplicate loads.

**ISSUE:** Race window between load completing and `pendingLoads.remove(id)`:
- Thread A: Calls `loadPlayer()`, starts async load
- Thread B: Calls `getProgress()`, returns null (not in cache yet)
- Thread A: Load completes, puts in cache, removes from pendingLoads
- Thread B: Now has null, proceeds incorrectly

**Lines 76-78 - getProgress():**
```java
public PlayerProgress getProgress(UUID playerId) {
    return cache.get(playerId);
}
```

**ISSUE (CONC-01):** Returns null if player recently joined and load is in progress. Many callers don't handle null properly (GUIs proceed with null progress, showing 0 items).

**Lines 87-100 - getProgressOrLoad():**
```java
public PlayerProgress getProgressOrLoad(Player player) {
    PlayerProgress cached = cache.get(player.getUniqueId());
    if (cached != null) {
        return cached;
    }
    try {
        return loadPlayer(player).get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
        ...
    }
}
```

**GOOD:** This method exists but is never used. It blocks on load, which is the correct behavior for synchronous access.

### PlayerListener.java - BukkitScheduler Usage (CONC-03)

**Lines 50-54:**
```java
Bukkit.getScheduler().runTask(plugin, () -> {
    if (player.isOnline()) {
        recipeManager.unlockRecipesForPlayer(player);
    }
});
```

**ISSUE:** Uses deprecated `BukkitScheduler.runTask()`. Should use `player.getScheduler().run()` for Folia compatibility.

**Lines 60-67:**
```java
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    if (player.isOnline()) {
        GoggleManager goggleManager = plugin.getGoggleManager();
        if (goggleManager != null) {
            goggleManager.refreshVisibilityForPlayer(player);
        }
    }
}, 20L);
```

**ISSUE:** Uses deprecated `BukkitScheduler.runTaskLater()`. Should use `player.getScheduler().runDelayed()`.

### RewardManager.java - BukkitScheduler Usage (CONC-03)

**Line 184:**
```java
Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 10L);
```

**ISSUE:** Uses deprecated `BukkitScheduler.runTaskLater()`. Firework is an entity, should use `firework.getScheduler().runDelayed()`.

### PlayerProgress.java - Thread Safety Issue (CONC-05)

**Lines 14-15:**
```java
private final Map<String, CollectionProgress> collections;
...
this.collections = new HashMap<>();
```

**ISSUE:** Uses plain `HashMap` which is NOT thread-safe. PlayerProgress objects are:
1. Created on async thread (database load)
2. Accessed from main thread (GUI, commands)
3. Modified from main thread (addItem, markComplete)
4. Read from multiple threads potentially

**Lines 75-77 - getProgress():**
```java
public CollectionProgress getProgress(String collectionId) {
    return collections.computeIfAbsent(collectionId, id -> new CollectionProgress(collectionId));
}
```

**ISSUE:** `HashMap.computeIfAbsent()` is NOT thread-safe. If called from multiple threads simultaneously, can cause data corruption or infinite loops.

**Line 219 - CollectionProgress.collectedItems:**
```java
this.collectedItems = new HashSet<>();
```

**ISSUE:** Uses plain `HashSet` which is NOT thread-safe. Same problem as above.

### ArmorChangeListener.java - Good Pattern Example

**Lines 39-49:**
```java
Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {
    if (player.isOnline()) {
        goggleManager.refreshVisibilityForPlayer(player);
    }
});
```

**GOOD:** Uses RegionScheduler correctly. However, should use EntityScheduler since this is a player operation:
```java
player.getScheduler().run(plugin, task -> { ... }, null);
```

### CollectibleInteractListener.java - Correct Pattern

**Lines 115-127:**
```java
AtomicBoolean lock = collectLocks.computeIfAbsent(collectible.id(), k -> new AtomicBoolean(false));
if (!lock.compareAndSet(false, true)) {
    player.sendMessage(configManager.getMessage("already-collected"));
    return;
}
try {
    processCollection(player, collectible);
} finally {
    collectLocks.remove(collectible.id());
}
```

**GOOD:** Correct use of AtomicBoolean with compareAndSet for race condition prevention. This pattern is exactly right.

### SpawnManager.java - Good Patterns

**Lines 127-129:**
```java
spawnTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
    checkAndSpawnCollectibles();
}, 100L, intervalTicks);
```

**GOOD:** Uses GlobalRegionScheduler correctly for server-wide periodic tasks.

**Line 354:**
```java
collectibleCountByZone.merge(zone.id(), 1, Integer::sum);
```

**GOOD:** Uses atomic `merge()` operation.

**Line 399:**
```java
collectibleCountByZone.computeIfPresent(collectible.zoneId(), (k, v) -> Math.max(0, v - 1));
```

**GOOD:** Uses atomic `computeIfPresent()` operation.

## Specific Issues and Fixes

### CONC-01: Race condition where getProgress() returns null

**Problem:** Player joins, loadPlayer() starts async. Player opens GUI before load completes. getProgress() returns null. GUI shows 0/0 progress.

**Evidence from code:**
- `CollectionMenuGUI.java:184`: `PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());`
- `CollectionMenuGUI.java:185`: `int collected = progress != null ? progress.getCollectedCount(collection.id()) : 0;`
- No blocking/waiting, just null check

**Solution:** Create `getProgressBlocking(UUID)` that:
1. Checks cache first
2. If not cached, checks pendingLoads and waits on the future
3. If neither, returns null (player not loaded at all)

```java
public PlayerProgress getProgressBlocking(UUID playerId) {
    // Fast path: already cached
    PlayerProgress cached = cache.get(playerId);
    if (cached != null) {
        return cached;
    }

    // Check if load is pending
    CompletableFuture<PlayerProgress> pending = pendingLoads.get(playerId);
    if (pending != null) {
        try {
            return pending.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Timed out waiting for player data load: " + playerId, e);
            return null;
        }
    }

    return null;
}
```

### CONC-02: Verify player data load completes before feature access

**Problem:** No mechanism to ensure data is ready before features access it.

**Solution Options:**

1. **Blocking on join (not recommended):** Would lag the join event handler
2. **Gating feature access:** Check `isLoaded()` and show "Loading..." message
3. **Event-based:** Fire custom event when load completes, listeners enable features

**Recommended:** Option 2 + defensive coding. Replace `getProgress()` calls with `getProgressBlocking()` in GUIs and commands.

### CONC-03: Migrate BukkitScheduler to Folia-compatible schedulers

**Files to change:**

| File | Line | Current | Fix |
|------|------|---------|-----|
| PlayerListener.java | 50 | `Bukkit.getScheduler().runTask()` | `player.getScheduler().run()` |
| PlayerListener.java | 60 | `Bukkit.getScheduler().runTaskLater()` | `player.getScheduler().runDelayed()` |
| RewardManager.java | 184 | `Bukkit.getScheduler().runTaskLater()` | `firework.getScheduler().runDelayed()` |

**Migration pattern:**

Before:
```java
Bukkit.getScheduler().runTask(plugin, () -> {
    player.doSomething();
});
```

After:
```java
player.getScheduler().run(plugin, task -> {
    player.doSomething();
}, null);  // null = retired callback (player logged out)
```

Before:
```java
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    player.doSomething();
}, 20L);
```

After:
```java
player.getScheduler().runDelayed(plugin, task -> {
    player.doSomething();
}, null, 20L);  // retired callback, delay in ticks
```

### CONC-04: ConcurrentHashMap atomic methods

**Current state:** Mostly correct already.

| Location | Status |
|----------|--------|
| `PlayerDataManager.pendingLoads.computeIfAbsent()` | GOOD |
| `SpawnManager.collectibleCountByZone.merge()` | GOOD |
| `SpawnManager.collectibleCountByZone.computeIfPresent()` | GOOD |
| `CollectibleInteractListener.collectLocks.computeIfAbsent()` | GOOD |
| `CollectibleInteractListener.lastCollectTime.put()` | OK (simple put is atomic) |

**No changes needed** for ConcurrentHashMap usage.

### CONC-05: PlayerProgress internal HashMap thread safety

**Problem:** `PlayerProgress.collections` is a `HashMap`, accessed from multiple threads.

**Solution:** Change to `ConcurrentHashMap`:

```java
// In PlayerProgress constructor
this.collections = new ConcurrentHashMap<>();
```

And for `CollectionProgress.collectedItems`:

```java
// In CollectionProgress constructor
this.collectedItems = ConcurrentHashMap.newKeySet();
```

**Alternative (simpler):** Since PlayerProgress is owned by one player and modifications happen on main thread after load, could synchronize on the object. However, ConcurrentHashMap is cleaner and has no performance penalty.

## Architecture Patterns

### Pattern 1: Player Data Access Gate

For GUIs and commands that need player data:

```java
public void open() {
    PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());
    if (progress == null) {
        player.sendMessage(Component.text("Loading your data, please wait...", NamedTextColor.YELLOW));
        // Retry after delay
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) open();
        }, null, 20L);
        return;
    }
    // Proceed with GUI
}
```

### Pattern 2: EntityScheduler for Player Operations

```java
// Player-scoped operation
player.getScheduler().run(plugin, task -> {
    // Safe: runs on player's owning thread
    player.getInventory().addItem(item);
}, () -> {
    // Optional: called if player logs out before task runs
    plugin.getLogger().info("Player logged out, task cancelled");
});
```

### Pattern 3: Thread-Safe Model Objects

```java
public class PlayerProgress {
    private final Map<String, CollectionProgress> collections = new ConcurrentHashMap<>();

    // All public methods are now thread-safe
    public CollectionProgress getProgress(String collectionId) {
        return collections.computeIfAbsent(collectionId,
            id -> new CollectionProgress(collectionId));
    }
}
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Thread-safe map | `synchronized HashMap` | `ConcurrentHashMap` | Better performance, atomic operations |
| Thread-safe set | `synchronized HashSet` | `ConcurrentHashMap.newKeySet()` | Consistent API, no wrapping |
| Atomic flag | `volatile boolean` | `AtomicBoolean` | CAS operations for races |
| Scheduler abstraction | Custom thread pools | Paper EntityScheduler | Folia-native, handles entity lifecycle |

## Common Pitfalls

### Pitfall 1: Assuming thenAccept() runs on main thread

**What goes wrong:** `CompletableFuture.thenAccept()` runs on the completing thread (often ForkJoinPool), not main thread.

**Why it happens:** Java's CompletableFuture doesn't know about Minecraft's threading model.

**How to avoid:** Always schedule back to appropriate thread:
```java
future.thenAccept(result -> {
    player.getScheduler().run(plugin, task -> {
        // NOW safe to access Bukkit API
    }, null);
});
```

### Pitfall 2: Check-then-act on ConcurrentHashMap

**What goes wrong:** `if (!map.containsKey(k)) map.put(k, v)` is not atomic.

**Why it happens:** Two operations, another thread can act between them.

**How to avoid:** Use `computeIfAbsent()`:
```java
map.computeIfAbsent(key, k -> createValue());
```

### Pitfall 3: RegionScheduler vs EntityScheduler

**What goes wrong:** Using RegionScheduler for player operations. Player moves, operation runs in wrong region.

**Why it happens:** RegionScheduler schedules based on location at call time.

**How to avoid:** Use EntityScheduler for any entity-specific operation:
```java
// WRONG: Player may move
Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> {});

// CORRECT: Follows player
player.getScheduler().run(plugin, task -> {}, null);
```

### Pitfall 4: Null from getProgress() during load window

**What goes wrong:** GUIs/commands call `getProgress()` before async load completes, get null.

**Why it happens:** `loadPlayer()` is async, no blocking.

**How to avoid:** Use `getProgressBlocking()` or check `isLoaded()` and retry.

## Code Examples

### Verified: EntityScheduler delayed task

```java
// Source: Paper API documentation
player.getScheduler().runDelayed(plugin, task -> {
    if (player.isOnline()) {
        player.sendMessage(Component.text("Delayed message!"));
    }
}, () -> {
    // Retired callback - player logged out
    plugin.getLogger().info("Player left before task executed");
}, 20L);  // 20 ticks = 1 second
```

### Verified: Blocking on pending load

```java
// Pattern for waiting on in-progress load
CompletableFuture<PlayerProgress> pending = pendingLoads.get(playerId);
if (pending != null) {
    try {
        return pending.get(5, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        // Load taking too long
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
        // Load failed
    }
}
```

### Verified: ConcurrentHashMap.newKeySet()

```java
// Source: Java 8+ API
// Creates a Set backed by ConcurrentHashMap for thread safety
private final Set<String> collectedItems = ConcurrentHashMap.newKeySet();

// Thread-safe operations
collectedItems.add(itemId);      // Atomic
collectedItems.contains(itemId); // Consistent view
collectedItems.remove(itemId);   // Atomic
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Bukkit.getScheduler()` | EntityScheduler/RegionScheduler | Folia (2023) | Required for Folia compatibility |
| `HashMap` in shared objects | `ConcurrentHashMap` | Always | Prevents data corruption |
| `HashSet` in shared objects | `ConcurrentHashMap.newKeySet()` | Java 8+ | Thread-safe set operations |
| Blocking on join | Async load + gate access | Best practice | No join lag |

## Risk Areas and Edge Cases

### Risk 1: Load timeout during high database load

**Scenario:** Many players join simultaneously, database pool saturated, loads time out.

**Mitigation:**
- 30-second timeout is generous
- Fallback creates empty PlayerProgress
- Consider caching aggressively

### Risk 2: Player quits during pending load

**Scenario:** Player joins, load starts async. Player quits immediately. Load completes, puts stale data in cache.

**Current handling:** `saveAndUnload()` removes from cache and pendingLoads. But if load completes after this, stale data enters cache.

**Solution:** Check `Bukkit.getPlayer(id) != null` in thenApply before caching (already partially done on line 53, but should verify player is online).

### Risk 3: GUI opened during load window

**Scenario:** Player opens collection GUI 0.5 seconds after join. Data still loading. GUI shows 0/0 progress.

**Current state:** Proceeds with null progress, shows empty data.

**Solution:** Implement gating pattern, show "Loading..." and retry.

### Risk 4: Concurrent modification during save

**Scenario:** Player modifies collection (addItem) while save is in progress.

**Current state:** Not a problem - saves read from cache snapshot, modifications go to same cache object.

**Analysis:** Safe because:
1. Save reads PlayerProgress reference
2. PlayerProgress modifications update same object in cache
3. Next save will include new modifications

## Open Questions

1. **Should ArmorChangeListener use EntityScheduler?**
   - Current: Uses RegionScheduler with player.getLocation()
   - Question: Is this safe if player moves between event and task execution?
   - Recommendation: Yes, migrate to EntityScheduler for safety

2. **Should getProgressBlocking() be used everywhere?**
   - Adds 5-second potential block
   - Alternative: Return Optional and let callers handle empty state
   - Recommendation: Use for GUIs (user expects response), not for background operations

## Sources

### Primary (HIGH confidence)
- `.planning/research/CONCURRENCY.md` - Prior research on Folia patterns
- Direct code analysis of current implementation
- Paper API documentation for EntityScheduler

### Secondary (MEDIUM confidence)
- Java ConcurrentHashMap documentation
- CompletableFuture best practices

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Using Paper's native schedulers
- Architecture: HIGH - Patterns verified against Paper docs
- Pitfalls: HIGH - Based on actual code analysis showing current issues

**Research date:** 2026-01-21
**Valid until:** 60 days (stable APIs, no fast-moving dependencies)
