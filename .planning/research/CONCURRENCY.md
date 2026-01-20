# Concurrency Patterns for Paper Plugins

**Domain:** Paper/Folia plugin concurrency
**Researched:** 2026-01-21
**Overall Confidence:** HIGH (verified against official Paper/Folia documentation)

---

## Executive Summary

Paper plugins operate in a complex threading environment with strict rules about what can be accessed from which threads. The traditional Bukkit model had a single "main thread" that owned all game state. Folia fundamentally changes this by introducing **regionized multithreading** where multiple regions tick in parallel, each with their own "main thread."

**Key principle:** The Bukkit API is NOT thread-safe. Most API calls that read or modify world state MUST happen on the thread that owns that data. Violating this causes data corruption, not just exceptions.

---

## Thread Rules

### Rule 1: Never Access Bukkit API from Async Threads

**Why:** The Bukkit API assumes single-threaded access. World state (chunks, entities, blocks, inventories) is not synchronized. Concurrent access causes data corruption, not just race conditions.

**What's unsafe from async:**
- `World.getChunkAt()` - throws `IllegalStateException: Asynchronous Async Chunk Load`
- `World.getEntities()` / `World.getNearbyEntities()`
- `Entity.teleport()` (use `teleportAsync()` instead)
- `Inventory.addItem()` / `Inventory.removeItem()`
- `Block.setType()` / `Block.getState()`
- `Player.sendMessage()` - technically works but discouraged
- `Bukkit.getOfflinePlayer()` - may block with HTTP requests

**What's safe from async:**
- `Bukkit.getLogger()` - logging is thread-safe
- `CompletableFuture` operations on your own data
- Database operations via connection pools (HikariCP)
- Pure computation with no Bukkit API calls
- `Player.getUniqueId()` - returns immutable UUID

**Correct:**
```java
// Database operation runs async, then schedules result handling on main thread
CompletableFuture.supplyAsync(() -> {
    // Safe: database query on async thread
    return database.loadPlayerData(playerId);
}).thenAccept(data -> {
    // UNSAFE: This runs on ForkJoinPool, not main thread!
    // player.sendMessage(...); // DON'T DO THIS
});

// Correct pattern: schedule back to region/entity thread
CompletableFuture.supplyAsync(() -> database.loadPlayerData(playerId))
    .thenAccept(data -> {
        // Schedule on the entity's owning thread
        player.getScheduler().run(plugin, task -> {
            player.sendMessage(Component.text("Data loaded!"));
        }, null);
    });
```

**Incorrect:**
```java
// WRONG: Accessing world state from async thread
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    World world = Bukkit.getWorld("world");
    Chunk chunk = world.getChunkAt(0, 0);  // IllegalStateException!

    for (Entity entity : chunk.getEntities()) {  // Data corruption risk!
        entity.remove();
    }
});
```

### Rule 2: Use the Correct Scheduler for the Task

**Why:** Folia has no single "main thread." Each region has its own tick loop. Code must run on the thread that owns the data it accesses.

| Scheduler | Use Case | How to Get |
|-----------|----------|------------|
| `EntityScheduler` | Operations on a specific entity | `entity.getScheduler()` |
| `RegionScheduler` | Operations at a specific location | `Bukkit.getRegionScheduler()` |
| `GlobalRegionScheduler` | Server-wide tasks (no specific location) | `Bukkit.getGlobalRegionScheduler()` |
| `AsyncScheduler` | Database, file I/O, HTTP requests | `Bukkit.getAsyncScheduler()` |

**Correct:**
```java
// Entity operation: use EntityScheduler
player.getScheduler().run(plugin, task -> {
    player.getInventory().addItem(item);  // Safe: on player's thread
}, null);

// Location operation: use RegionScheduler
Bukkit.getRegionScheduler().run(plugin, location, task -> {
    location.getBlock().setType(Material.STONE);  // Safe: on region's thread
});

// Periodic server-wide task: use GlobalRegionScheduler
Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
    // Runs on global region - safe for non-location-specific logic
    checkAndSpawnCollectibles();
}, 20L, 100L);

// Database operation: use AsyncScheduler
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    database.savePlayerData(playerId, data);  // Safe: off main thread
});
```

**Incorrect:**
```java
// WRONG: Using RegionScheduler for entity operations
// Entity might teleport to different region between scheduling and execution!
Bukkit.getRegionScheduler().run(plugin, entity.getLocation(), task -> {
    entity.remove();  // Entity might not be in this region anymore!
});

// WRONG: Using BukkitScheduler (deprecated for Folia)
Bukkit.getScheduler().runTask(plugin, () -> {
    // Works on Paper, breaks on Folia - no main thread exists!
});
```

### Rule 3: Check Region Ownership Before Access

**Why:** In Folia, code may execute on any region's thread. Before accessing location/entity data, verify you're on the owning thread.

**Correct:**
```java
public void handleBlockInteraction(Location location) {
    if (Bukkit.isOwnedByCurrentRegion(location)) {
        // Safe: we're on the owning thread
        location.getBlock().setType(Material.AIR);
    } else {
        // Schedule to run on the correct thread
        Bukkit.getRegionScheduler().run(plugin, location, task -> {
            location.getBlock().setType(Material.AIR);
        });
    }
}

// For entities
public void handleEntityOperation(Entity entity) {
    if (Bukkit.isOwnedByCurrentRegion(entity)) {
        entity.remove();
    } else {
        entity.getScheduler().run(plugin, task -> {
            entity.remove();
        }, null);
    }
}
```

### Rule 4: Use teleportAsync() for Cross-Region Teleports

**Why:** Synchronous teleport across regions is unsafe. The entity ownership changes during teleport.

**Correct:**
```java
entity.teleportAsync(destination).thenAccept(success -> {
    if (success) {
        // Entity is now in destination region
        // Schedule follow-up on entity's new thread
        entity.getScheduler().run(plugin, task -> {
            entity.sendMessage(Component.text("Teleported!"));
        }, null);
    }
});
```

**Incorrect:**
```java
// WRONG: Synchronous teleport
entity.teleport(destination);  // May fail or corrupt state on Folia
entity.sendMessage(...);  // Entity might be on different thread now!
```

---

## Data Structure Patterns

### Pattern 1: Thread-Safe Player Data Cache

**When to use:** Caching player data loaded from database, accessed from multiple threads.

**Implementation:**
```java
public class PlayerDataManager {
    private final Map<UUID, PlayerProgress> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerProgress>> pendingLoads = new ConcurrentHashMap<>();

    public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
        // Check cache first (thread-safe read)
        PlayerProgress cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Use computeIfAbsent for atomic "load once" guarantee
        // Multiple threads calling this for same player will all get same Future
        return pendingLoads.computeIfAbsent(playerId, id -> {
            return CompletableFuture.supplyAsync(() -> storage.loadPlayer(id))
                .thenApply(progress -> {
                    cache.put(id, progress);
                    pendingLoads.remove(id);  // Cleanup after cache populated
                    return progress;
                });
        });
    }

    public void saveAndUnload(UUID playerId) {
        PlayerProgress progress = cache.remove(playerId);  // Atomic remove
        pendingLoads.remove(playerId);
        if (progress != null) {
            CompletableFuture.runAsync(() -> storage.savePlayer(progress));
        }
    }
}
```

**Pitfalls:**
- Don't use `get()` then `put()` - use `computeIfAbsent()` for atomicity
- The mapping function in `computeIfAbsent()` may block other threads accessing same key
- Remove from `pendingLoads` AFTER populating cache, not before

### Pattern 2: Atomic Race Condition Prevention

**When to use:** Preventing multiple players from collecting the same item simultaneously.

**Implementation:**
```java
public class CollectibleInteractListener {
    // Track collectibles currently being collected
    private final Map<UUID, AtomicBoolean> collectLocks = new ConcurrentHashMap<>();

    private void handleInteraction(Player player, Collectible collectible) {
        // Atomically acquire lock for this collectible
        AtomicBoolean lock = collectLocks.computeIfAbsent(
            collectible.id(),
            k -> new AtomicBoolean(false)
        );

        // compareAndSet returns true only if we changed false->true
        // Only ONE thread will succeed for each collectible
        if (!lock.compareAndSet(false, true)) {
            player.sendMessage("Someone else is collecting this!");
            return;
        }

        try {
            processCollection(player, collectible);
        } finally {
            // Always release lock and cleanup
            collectLocks.remove(collectible.id());
        }
    }
}
```

**Pitfalls:**
- Always use `compareAndSet()`, never `get()` then `set()`
- Always cleanup in `finally` block to prevent memory leaks
- Don't hold locks across async operations

### Pattern 3: Safe Concurrent Counter Updates

**When to use:** Tracking counts that may be modified from multiple threads.

**Implementation:**
```java
// For simple counting
private final Map<String, Integer> collectibleCountByZone = new ConcurrentHashMap<>();

// Increment atomically
collectibleCountByZone.merge(zoneId, 1, Integer::sum);

// Decrement atomically (ensuring non-negative)
collectibleCountByZone.computeIfPresent(zoneId, (k, v) -> Math.max(0, v - 1));

// For high-contention counting, use LongAdder
private final Map<String, LongAdder> highContentionCounts = new ConcurrentHashMap<>();

highContentionCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
long count = highContentionCounts.get(key).sum();
```

**Pitfalls:**
- Don't use `get()` + arithmetic + `put()` - not atomic
- `merge()` and `compute()` are atomic for the operation, but the mapping function should be fast
- For very high contention, `LongAdder` outperforms `AtomicLong`

### Pattern 4: Thread-Safe Collection Iteration

**When to use:** Iterating over collections that may be modified concurrently.

**Implementation:**
```java
// ConcurrentHashMap values() is weakly consistent - safe to iterate
private final Map<UUID, Collectible> activeCollectibles = new ConcurrentHashMap<>();

// Safe iteration (won't throw ConcurrentModificationException)
for (Collectible collectible : activeCollectibles.values()) {
    // May see updates made during iteration
    // Will not see collectible twice or skip any
    if (shouldDespawn(collectible)) {
        despawnQueue.add(collectible.id());
    }
}

// Process despawns after iteration (avoid modifying during iteration)
for (UUID id : despawnQueue) {
    despawnCollectible(id, true);
}

// For lists that need concurrent modification: CopyOnWriteArrayList
// Good for: infrequent writes, frequent reads (like listener lists)
private final List<Listener> listeners = new CopyOnWriteArrayList<>();
```

**Pitfalls:**
- Removing during iteration with ConcurrentHashMap is safe but may skip elements
- Better to collect IDs to remove, then remove after iteration
- CopyOnWriteArrayList copies entire array on write - expensive for frequent modifications

---

## CompletableFuture Patterns

### Pattern 1: Database Load with Timeout

**When to use:** Loading data that must complete within a time limit.

**Implementation:**
```java
public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> {
        // Runs on ForkJoinPool.commonPool() by default
        return storage.loadPlayer(playerId);
    })
    .orTimeout(30, TimeUnit.SECONDS)
    .exceptionally(throwable -> {
        if (throwable instanceof TimeoutException) {
            logger.warning("Player load timed out: " + playerId);
        } else {
            logger.log(Level.WARNING, "Failed to load player: " + playerId, throwable);
        }
        // Return default on failure
        return new PlayerProgress(playerId);
    });
}
```

**Pitfalls:**
- `orTimeout()` throws `TimeoutException` wrapped in `CompletionException`
- Default executor is ForkJoinPool - don't do blocking I/O without custom executor
- `exceptionally()` runs on same thread as the exception - may be ForkJoinPool

### Pattern 2: Chaining Back to Main Thread

**When to use:** Processing async result that needs to update game state.

**Implementation:**
```java
// Load from database, then update player on their thread
storage.loadPlayer(playerId)
    .thenAccept(progress -> {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            // Schedule on player's owning thread
            player.getScheduler().run(plugin, task -> {
                cache.put(playerId, progress);
                player.sendMessage(Component.text("Data loaded!"));
            }, null);
        }
    });
```

**Pitfalls:**
- `thenAccept()` may run on calling thread OR completing thread - unpredictable
- Always schedule Bukkit API calls explicitly, don't assume thread context
- Check `player.isOnline()` before scheduling - player may have quit

### Pattern 3: Parallel Operations with allOf

**When to use:** Waiting for multiple independent async operations.

**Implementation:**
```java
public CompletableFuture<Void> saveAll() {
    if (cache.isEmpty()) {
        return CompletableFuture.completedFuture(null);
    }

    // Create array of save futures
    CompletableFuture<?>[] futures = cache.keySet().stream()
        .map(this::savePlayer)
        .toArray(CompletableFuture[]::new);

    // Wait for all to complete
    return CompletableFuture.allOf(futures)
        .orTimeout(60, TimeUnit.SECONDS)
        .exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Failed to save all player data", throwable);
            return null;
        });
}
```

**Pitfalls:**
- `allOf()` completes when ALL futures complete (including failures)
- Individual future failures don't stop others
- If you need to stop on first failure, use different pattern

### Pattern 4: Custom Executor for Database Operations

**When to use:** Avoiding ForkJoinPool saturation with blocking I/O.

**Implementation:**
```java
// Create dedicated executor for database operations
private final ExecutorService dbExecutor = Executors.newFixedThreadPool(
    4,
    new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "Collections-DB-" + counter.incrementAndGet());
            t.setDaemon(true);  // Don't prevent JVM shutdown
            return t;
        }
    }
);

public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
    // Use custom executor instead of ForkJoinPool
    return CompletableFuture.supplyAsync(
        () -> storage.loadPlayer(playerId),
        dbExecutor
    );
}

// Shutdown in onDisable()
public void shutdown() {
    dbExecutor.shutdown();
    try {
        if (!dbExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            dbExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        dbExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

**Pitfalls:**
- MUST shutdown executor in `onDisable()` or threads leak
- Size pool based on expected concurrent operations and database connection pool size
- Use daemon threads so they don't prevent server shutdown

---

## Race Condition Prevention

### Issue 1: Player Data Load Race

**Scenario:** Player joins, async load starts. Player quits before load completes. Player rejoins, second load starts. First load completes, overwrites newer state.

**Solution:**
```java
public CompletableFuture<PlayerProgress> loadPlayer(Player player) {
    UUID playerId = player.getUniqueId();

    // computeIfAbsent ensures only ONE load runs per player
    return pendingLoads.computeIfAbsent(playerId, id -> {
        return storage.loadPlayer(id)
            .thenApply(progress -> {
                // Only cache if player is still online
                // Check on the completion thread
                if (Bukkit.getPlayer(id) != null) {
                    cache.put(id, progress);
                }
                pendingLoads.remove(id);
                return progress;
            })
            .exceptionally(throwable -> {
                pendingLoads.remove(id);
                return new PlayerProgress(id);
            });
    });
}
```

### Issue 2: Concurrent Collection Modification

**Scenario:** Two players click same collectible at exact same moment. Both pass "exists" check. Both process collection. Duplicate rewards given.

**Solution:** Use `AtomicBoolean` lock pattern (see Pattern 2 above).

### Issue 3: Stale Cache Read

**Scenario:** Thread A reads player progress from cache. Thread B modifies same progress object. Thread A continues with stale data.

**Solution:** Either:
1. Make `PlayerProgress` immutable, return new instances on modification
2. Synchronize all access to mutable `PlayerProgress` fields
3. Use atomic operations for individual field updates

```java
// Option 1: Immutable with copy-on-write
public PlayerProgress addItem(String collectionId, String itemId) {
    // Create new instance with modification
    return new PlayerProgress(this, collectionId, itemId);
}

// Option 2: Synchronized access (simpler but more contention)
public class PlayerProgress {
    private final Object lock = new Object();

    public boolean addItem(String collectionId, String itemId) {
        synchronized (lock) {
            return collections.computeIfAbsent(collectionId,
                id -> new CollectionProgress(id)).addItem(itemId);
        }
    }
}
```

---

## Folia Compatibility Checklist

For full Folia compatibility, a plugin must:

- [ ] Add `folia-supported: true` to `paper-plugin.yml`
- [ ] Replace all `BukkitScheduler` usage with appropriate Folia schedulers
- [ ] Use `EntityScheduler` for entity operations (`entity.getScheduler()`)
- [ ] Use `RegionScheduler` for location operations (`Bukkit.getRegionScheduler()`)
- [ ] Use `GlobalRegionScheduler` for server-wide periodic tasks
- [ ] Use `AsyncScheduler` for database/file I/O (`Bukkit.getAsyncScheduler()`)
- [ ] Replace `entity.teleport()` with `entity.teleportAsync()`
- [ ] Check `Bukkit.isOwnedByCurrentRegion()` before direct world access
- [ ] Ensure no static state shared between regions without synchronization
- [ ] Test with multiple players in different world regions

**Current codebase (Collections plugin) status:**
- Uses `Bukkit.getGlobalRegionScheduler()` - GOOD
- Uses `ConcurrentHashMap` for shared state - GOOD
- Uses `CompletableFuture` for async database - GOOD
- Has `AtomicBoolean` lock for collect race - GOOD
- Uses `Bukkit.getScheduler().runTask()` in PlayerListener - NEEDS MIGRATION to EntityScheduler

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Blocking on Main Thread

```java
// WRONG: Blocks server tick
PlayerProgress progress = loadPlayer(player).get();  // BLOCKS!

// CORRECT: Use callback
loadPlayer(player).thenAccept(progress -> {
    player.getScheduler().run(plugin, task -> {
        // Use progress here
    }, null);
});
```

### Anti-Pattern 2: Check-Then-Act Without Atomicity

```java
// WRONG: Race condition between check and act
if (!collectLocks.containsKey(collectibleId)) {
    collectLocks.put(collectibleId, true);  // Another thread may have put in between!
    // process...
}

// CORRECT: Atomic operation
AtomicBoolean lock = collectLocks.computeIfAbsent(collectibleId, k -> new AtomicBoolean(false));
if (lock.compareAndSet(false, true)) {
    try {
        // process...
    } finally {
        collectLocks.remove(collectibleId);
    }
}
```

### Anti-Pattern 3: Storing Player References

```java
// WRONG: Player reference may become invalid
private final Map<UUID, Player> playerCache = new ConcurrentHashMap<>();

// CORRECT: Store UUID, look up Player when needed
private final Map<UUID, PlayerProgress> progressCache = new ConcurrentHashMap<>();

public void doSomething(UUID playerId) {
    Player player = Bukkit.getPlayer(playerId);  // Fresh lookup
    if (player != null && player.isOnline()) {
        // Use player
    }
}
```

### Anti-Pattern 4: Assuming Thread Context in Callbacks

```java
// WRONG: Assumes thenAccept runs on specific thread
storage.loadPlayer(id).thenAccept(progress -> {
    player.sendMessage(...);  // May be on wrong thread!
});

// CORRECT: Explicitly schedule to correct thread
storage.loadPlayer(id).thenAccept(progress -> {
    player.getScheduler().run(plugin, task -> {
        player.sendMessage(...);  // Guaranteed on player's thread
    }, null);
});
```

---

## Sources

### Official Documentation (HIGH confidence)
- [Folia Documentation](https://docs.papermc.io/folia/) - PaperMC official
- [Folia GitHub README](https://github.com/PaperMC/Folia) - Detailed threading model
- [Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/) - Migration guide
- [Paper Scheduling](https://docs.papermc.io/paper/dev/scheduler/) - Scheduler documentation

### API Documentation (HIGH confidence)
- [BukkitScheduler API](https://jd.papermc.io/paper/1.21.4/org/bukkit/scheduler/BukkitScheduler.html)
- [RegionScheduler API](https://jd.papermc.io/folia/1.21/io/papermc/paper/threadedregions/scheduler/RegionScheduler.html)

### Community Resources (MEDIUM confidence)
- [Writing a Multithreaded Plugin](https://multipaper.io/shreddedpaper/writing-a-multithreaded-plugin.html) - MultiPaper guide
- [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) - Cross-platform scheduler wrapper

### Java Concurrency (HIGH confidence)
- [ConcurrentHashMap JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html)
- [CompletableFuture Baeldung Guide](https://www.baeldung.com/java-completablefuture)
- [Java Concurrency Best Practices](https://www.javaguides.net/2025/02/concurrency-in-java-best-practices-for-multithreading.html)
