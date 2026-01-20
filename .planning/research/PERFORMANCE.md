# Performance Patterns for Paper Plugins

**Domain:** Paper 1.21.4 Plugin Performance
**Researched:** 2026-01-21
**Overall Confidence:** HIGH (verified with official docs and multiple sources)

This document catalogs performance antipatterns and best practices for Paper 1.21.4 plugins, with specific focus on patterns relevant to collectibles systems (particles, entity lookup, database operations, GUI handling).

---

## Antipatterns

### 1. O(n*m) Particle Iteration

**What it looks like:**
```java
// BAD: Iterates all players for every collectible location every tick
@Override
public void run() {
    for (Location collectibleLoc : allCollectibles) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().distance(collectibleLoc) < 32) {
                player.spawnParticle(Particle.FLAME, collectibleLoc, 1);
            }
        }
    }
}
```

**Detection:**
- Spark profiler shows high time in particle-related methods
- CPU usage scales with `players * collectibles` rather than `collectibles`
- TPS drops correlate with player count increases
- Look for nested loops: `for (player) { for (collectible) }` or vice versa

**Fix:**
```java
// GOOD: Use spatial partitioning - iterate collectibles near each player
@Override
public void run() {
    for (Player player : Bukkit.getOnlinePlayers()) {
        Collection<Location> nearby = getCollectiblesInChunks(player.getLocation(), 2);
        for (Location loc : nearby) {
            player.spawnParticle(Particle.FLAME, loc, 1);
        }
    }
}

// Or use Paper's ParticleBuilder with automatic receiver filtering
new ParticleBuilder(Particle.FLAME)
    .location(collectibleLoc)
    .receivers(32, true)  // Only players within 32 blocks
    .spawn();
```

**Impact:** SEVERE - This is one of the most common causes of server lag in collectible/cosmetic plugins. With 100 players and 1000 collectibles, you're doing 100,000 distance calculations per tick.

---

### 2. Linear Entity Search (getNearbyEntities Abuse)

**What it looks like:**
```java
// BAD: Searches all entities in radius repeatedly
public Entity findCollectible(Location loc) {
    for (Entity e : loc.getWorld().getNearbyEntities(loc, 50, 50, 50)) {
        if (isCollectible(e)) return e;
    }
    return null;
}
```

**Detection:**
- Spark shows time in `World.getNearbyEntities` or `Entity.getNearbyEntities`
- Method called frequently (every tick, every player move)
- Large search radius (>16 blocks)
- No predicate filtering passed to method

**Fix:**
```java
// GOOD: Use chunk-based lookup with filtering predicate
public Entity findCollectible(Location loc) {
    return loc.getWorld().getNearbyEntities(
        loc, 16, 16, 16,
        e -> e.getType() == EntityType.ARMOR_STAND && isCollectible(e)
    ).stream().findFirst().orElse(null);
}

// BETTER: Maintain your own spatial index
private Map<ChunkKey, Set<Entity>> collectiblesByChunk = new HashMap<>();

public Entity findCollectible(Location loc) {
    ChunkKey key = new ChunkKey(loc.getChunk());
    Set<Entity> inChunk = collectiblesByChunk.get(key);
    if (inChunk == null) return null;
    for (Entity e : inChunk) {
        if (e.getLocation().distanceSquared(loc) < 256) return e; // 16^2
    }
    return null;
}
```

**Impact:** MODERATE to SEVERE - `getNearbyEntities` creates a new collection on every call and iterates all entities in loaded chunks within the bounding box. Paper has optimizations, but it's still O(n) where n = entities in range.

---

### 3. Unbatched Database Writes

**What it looks like:**
```java
// BAD: Individual INSERT for each collection
public void saveCollections(UUID player, List<String> collections) {
    for (String collection : collections) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO collections (uuid, name) VALUES (?, ?)")) {
            ps.setString(1, player.toString());
            ps.setString(2, collection);
            ps.executeUpdate();
        }
    }
}
```

**Detection:**
- Spark async profiler shows database methods taking significant time
- Database connection pool exhaustion warnings
- Player join/quit causes lag spikes
- Each save operation acquires a new connection

**Fix:**
```java
// GOOD: Batch operations with single connection
public void saveCollections(UUID player, List<String> collections) {
    Bukkit.getAsyncScheduler().runNow(plugin, task -> {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO collections (uuid, name) VALUES (?, ?)")) {
            for (String collection : collections) {
                ps.setString(1, player.toString());
                ps.setString(2, collection);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.severe("Failed to save collections: " + e.getMessage());
        }
    });
}
```

**Impact:** MODERATE - Each database call has overhead (connection acquisition, network round-trip for remote DBs, disk I/O). Batching can provide 80-90% improvement for bulk operations.

---

### 4. Synchronous Database on Main Thread

**What it looks like:**
```java
// BAD: Database call blocks main thread
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    try (Connection conn = dataSource.getConnection()) {
        // This blocks the main thread!
        ResultSet rs = conn.createStatement()
            .executeQuery("SELECT * FROM player_data WHERE uuid = '" + event.getPlayer().getUniqueId() + "'");
        // ... process
    }
}
```

**Detection:**
- Spark profiler shows JDBC/SQL methods on main thread
- Server freezes briefly when players join/quit
- TPS drops correlate with database operations
- Look for `getConnection()` calls outside async context

**Fix:**
```java
// GOOD: Async database with callback to main thread
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    CompletableFuture.supplyAsync(() -> {
        // Runs on ForkJoinPool
        return loadPlayerData(uuid);
    }).thenAccept(data -> {
        // Return to main thread for Bukkit API calls
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                applyPlayerData(player, data);
            }
        });
    });
}
```

**Impact:** SEVERE - Any operation over ~50ms will cause noticeable lag. Database queries can easily take 10-100ms, causing visible stuttering.

---

### 5. Excessive Location Object Creation

**What it looks like:**
```java
// BAD: Creates new Location every call
public Location getSpawnLocation(Entity entity) {
    return new Location(
        entity.getWorld(),
        entity.getLocation().getX() + offset,
        entity.getLocation().getY(),
        entity.getLocation().getZ()
    );
}

// Called in tight loop
for (int i = 0; i < 1000; i++) {
    Location loc = getSpawnLocation(entity);  // 1000 allocations
}
```

**Detection:**
- GC pressure visible in Spark memory profiler
- Many `Location` objects in heap dump
- Short-lived objects causing frequent minor GC
- Look for `new Location()` in hot paths (tick handlers, particle loops)

**Fix:**
```java
// GOOD: Reuse mutable Location objects
private final Location reusableLocation = new Location(null, 0, 0, 0);

public Location getSpawnLocation(Entity entity, Location output) {
    Location entityLoc = entity.getLocation();
    output.setWorld(entityLoc.getWorld());
    output.setX(entityLoc.getX() + offset);
    output.setY(entityLoc.getY());
    output.setZ(entityLoc.getZ());
    return output;
}

// Or use primitive coordinates
public void spawnParticleAt(World world, double x, double y, double z) {
    world.spawnParticle(Particle.FLAME, x, y, z, 1);
}
```

**Impact:** MINOR to MODERATE - Individual allocations are cheap, but in hot loops (particle systems, entity iteration) they accumulate. GC pauses cause lag spikes.

---

### 6. PlayerMoveEvent Abuse

**What it looks like:**
```java
// BAD: Heavy computation on every player movement
@EventHandler
public void onMove(PlayerMoveEvent event) {
    // Called ~20 times per second per player
    checkNearbyCollectibles(event.getPlayer());  // Heavy operation
    updatePlayerParticles(event.getPlayer());
    savePlayerLocation(event.getPlayer());  // Database!
}
```

**Detection:**
- Spark shows high time in `PlayerMoveEvent` handlers
- Lag increases linearly with player count
- Event fires even for head rotation (no actual movement)
- Look for expensive operations in move handlers

**Fix:**
```java
// GOOD: Early exit for non-movement, throttle checks
@EventHandler(ignoreCancelled = true)
public void onMove(PlayerMoveEvent event) {
    // Early exit if only head rotation
    if (event.getFrom().getBlockX() == event.getTo().getBlockX()
        && event.getFrom().getBlockY() == event.getTo().getBlockY()
        && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
        return;
    }

    // Throttle to once per second max
    UUID uuid = event.getPlayer().getUniqueId();
    long now = System.currentTimeMillis();
    if (now - lastCheck.getOrDefault(uuid, 0L) < 1000) return;
    lastCheck.put(uuid, now);

    // Now do expensive work
    checkNearbyCollectibles(event.getPlayer());
}
```

**Impact:** MODERATE to SEVERE - `PlayerMoveEvent` fires extremely frequently. With 100 players, that's ~2000 events per second. Any non-trivial work compounds rapidly.

---

### 7. Storing Player References Instead of UUIDs

**What it looks like:**
```java
// BAD: Holds reference to Player object
private Map<Player, CollectionProgress> playerProgress = new HashMap<>();

public void trackPlayer(Player player) {
    playerProgress.put(player, new CollectionProgress());
}
```

**Detection:**
- Memory leaks after player disconnect
- `Player` objects appear in heap dump long after logout
- NullPointerExceptions when accessing stored players
- Look for `Map<Player, ...>` or `Set<Player>` or `List<Player>`

**Fix:**
```java
// GOOD: Store UUID, look up Player when needed
private Map<UUID, CollectionProgress> playerProgress = new HashMap<>();

public void trackPlayer(Player player) {
    playerProgress.put(player.getUniqueId(), new CollectionProgress());
}

public void updatePlayer(UUID uuid) {
    Player player = Bukkit.getPlayer(uuid);
    if (player != null && player.isOnline()) {
        // Safe to use
    }
}
```

**Impact:** MODERATE - Memory leak causes increasing RAM usage, eventual OOM. Also causes subtle bugs when Player reference becomes stale.

---

### 8. ConcurrentModificationException from Async Access

**What it looks like:**
```java
// BAD: Modifying collection from async thread
private List<Location> collectibles = new ArrayList<>();

public void addCollectible(Location loc) {
    collectibles.add(loc);  // Called from main thread
}

// In async task
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    for (Location loc : collectibles) {  // CME!
        saveToDatabase(loc);
    }
});
```

**Detection:**
- `ConcurrentModificationException` in logs
- Sporadic crashes during async operations
- Race conditions causing missing/duplicate data
- Look for collections accessed from both main and async threads

**Fix:**
```java
// GOOD: Use concurrent collections or synchronization
private List<Location> collectibles = new CopyOnWriteArrayList<>();

// Or snapshot before async work
public void saveAllAsync() {
    List<Location> snapshot = new ArrayList<>(collectibles);  // Copy on main thread
    Bukkit.getAsyncScheduler().runNow(plugin, task -> {
        for (Location loc : snapshot) {  // Safe iteration
            saveToDatabase(loc);
        }
    });
}
```

**Impact:** MODERATE - CME crashes the task, potentially losing data. Race conditions cause subtle, hard-to-debug issues.

---

### 9. Inefficient ItemMeta Access (Pre-1.21.1)

**What it looks like:**
```java
// BAD: Gets full ItemMeta just to read PDC
public boolean hasCustomData(ItemStack item) {
    ItemMeta meta = item.getItemMeta();  // Full snapshot created
    if (meta == null) return false;
    return meta.getPersistentDataContainer().has(myKey, PersistentDataType.STRING);
}
```

**Detection:**
- Spark shows time in `getItemMeta()` calls
- Called in hot paths (inventory clicks, item comparisons)
- Only reading PDC, not modifying meta

**Fix (1.21.1+):**
```java
// GOOD: Use direct PDC access (Paper 1.21.1+)
public boolean hasCustomData(ItemStack item) {
    return item.getPersistentDataContainer().has(myKey, PersistentDataType.STRING);
}
```

**Fix (Pre-1.21.1):**
```java
// Cache results or batch operations
private Map<ItemStack, Boolean> customDataCache = new WeakHashMap<>();

public boolean hasCustomData(ItemStack item) {
    return customDataCache.computeIfAbsent(item, i -> {
        ItemMeta meta = i.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(myKey, PersistentDataType.STRING);
    });
}
```

**Impact:** MINOR - Each `getItemMeta()` call constructs a full snapshot. Adds up in inventory operations.

---

### 10. Armor Stand Holograms Instead of Display Entities

**What it looks like:**
```java
// BAD: Using armor stands for floating text (pre-1.19.4 pattern)
public void createHologram(Location loc, String text) {
    ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class);
    stand.setCustomName(text);
    stand.setCustomNameVisible(true);
    stand.setInvisible(true);
    stand.setMarker(true);
    stand.setGravity(false);
}
```

**Detection:**
- Many invisible armor stands in entity list
- Lag correlates with hologram count
- Spark shows time in armor stand tick methods
- Server entity count unexpectedly high

**Fix (1.19.4+):**
```java
// GOOD: Use display entities (1.19.4+)
public void createHologram(Location loc, String text) {
    TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
        td.text(Component.text(text));
        td.setBillboard(Display.Billboard.CENTER);
    });
}

// BETTER: Use packet-based displays (no server entity)
// Use libraries like FancyHolograms or DecentHolograms
```

**Impact:** MODERATE - Display entities are specifically designed for this use case and have lower overhead than armor stands. Packet-based approaches have virtually zero server cost.

---

### 11. Not Using Folia-Compatible Schedulers

**What it looks like:**
```java
// BAD: Old-style schedulers (breaks on Folia, less efficient on Paper)
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    player.teleport(destination);  // May be wrong thread on Folia
}, 20L);
```

**Detection:**
- Plugin breaks on Folia servers
- Deprecation warnings for `BukkitScheduler` methods
- Entity operations scheduled globally instead of regionally

**Fix:**
```java
// GOOD: Use Folia-compatible schedulers
// For location-based operations
Bukkit.getRegionScheduler().execute(plugin, destination, () -> {
    player.teleport(destination);
});

// For entity-following operations
player.getScheduler().execute(plugin, () -> {
    // Runs on player's region thread
    player.sendMessage(Component.text("Hello"));
}, null, 20L);

// For async I/O
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    // Database operations
});

// For server-wide operations
Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
    // Broadcast, etc.
});
```

**Impact:** MINOR on Paper (future-proofing), SEVERE on Folia (crashes/undefined behavior).

---

### 12. NamespacedKey Recreation

**What it looks like:**
```java
// BAD: Creates new NamespacedKey on every access
public boolean hasData(PersistentDataContainer pdc) {
    return pdc.has(new NamespacedKey(plugin, "my_key"), PersistentDataType.STRING);
}
```

**Detection:**
- Many `NamespacedKey` objects in heap
- Called in hot paths

**Fix:**
```java
// GOOD: Reuse NamespacedKey instances
private static final NamespacedKey MY_KEY = new NamespacedKey("myplugin", "my_key");

public boolean hasData(PersistentDataContainer pdc) {
    return pdc.has(MY_KEY, PersistentDataType.STRING);
}
```

**Impact:** MINOR - Small allocation overhead, but considered best practice.

---

## Best Practices

### 1. Use Spatial Partitioning for Location-Based Data

**Why:** Collectibles, spawn points, and other location-based data need efficient spatial queries. Linear search becomes untenable at scale.

**How:**
```java
// Chunk-based spatial index
public class SpatialIndex<T> {
    private final Map<Long, Set<T>> byChunk = new HashMap<>();

    public void add(int chunkX, int chunkZ, T item) {
        long key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
        byChunk.computeIfAbsent(key, k -> new HashSet<>()).add(item);
    }

    public Set<T> getNearby(Chunk chunk, int radius) {
        Set<T> result = new HashSet<>();
        int cx = chunk.getX(), cz = chunk.getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long key = (long) (cx + dx) << 32 | ((cz + dz) & 0xFFFFFFFFL);
                Set<T> inChunk = byChunk.get(key);
                if (inChunk != null) result.addAll(inChunk);
            }
        }
        return result;
    }
}
```

---

### 2. Batch Particle Updates Per-Player

**Why:** Reduces nested iteration and allows skipping particles outside view distance.

**How:**
```java
// Run periodically (every 5-10 ticks, not every tick)
public void updateParticles() {
    for (Player player : Bukkit.getOnlinePlayers()) {
        Collection<CollectibleLocation> nearby = spatialIndex.getNearby(
            player.getLocation().getChunk(), 2  // 2 chunk radius
        );

        for (CollectibleLocation loc : nearby) {
            if (!playerHasCollected(player, loc)) {
                player.spawnParticle(Particle.FLAME, loc.getLocation(), 1);
            }
        }
    }
}
```

---

### 3. Use CompletableFuture for Async Database Chains

**Why:** Clean async code that properly returns to main thread for Bukkit API calls.

**How:**
```java
public CompletableFuture<PlayerData> loadPlayerAsync(UUID uuid) {
    return CompletableFuture.supplyAsync(() -> {
        // Async: database query
        return database.loadPlayer(uuid);
    }).thenApply(data -> {
        // Still async: transform data
        return processData(data);
    });
}

// Usage
loadPlayerAsync(player.getUniqueId()).thenAccept(data -> {
    // Return to main thread
    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
        if (Bukkit.getPlayer(uuid) != null) {
            applyData(player, data);
        }
    });
});
```

---

### 4. Cache Expensive Computations

**Why:** Repeated expensive operations (permission checks, config lookups, distance calculations) should be cached.

**How:**
```java
// Per-player cache cleared on quit
private final Map<UUID, PlayerCache> playerCaches = new HashMap<>();

@EventHandler
public void onQuit(PlayerQuitEvent event) {
    playerCaches.remove(event.getPlayer().getUniqueId());
}

public boolean canCollect(Player player, String collectionId) {
    PlayerCache cache = playerCaches.computeIfAbsent(
        player.getUniqueId(),
        k -> new PlayerCache()
    );

    return cache.permissions.computeIfAbsent(collectionId, id ->
        player.hasPermission("collections.collect." + id)
    );
}
```

---

### 5. Profile Before Optimizing

**Why:** Premature optimization wastes time. Use Spark to find actual bottlenecks.

**How:**
```
# Start profiling
/spark profiler start

# Let it run during typical gameplay (30-60 seconds)
# Stop and get report
/spark profiler stop

# Check the report for:
# - Methods taking >5% of tick time
# - Your plugin's methods in the hot path
# - Entity tick, chunk load, database operations
```

Key metrics to watch:
- **TPS** - Should be 20. Below 18 is noticeable lag.
- **MSPT** - Milliseconds per tick. Should be <50ms.
- **CPU %** - Your plugin's share of server CPU.

---

### 6. Use Try-With-Resources for Database Connections

**Why:** Prevents connection leaks that exhaust the pool.

**How:**
```java
// ALWAYS use try-with-resources
public Optional<PlayerData> loadPlayer(UUID uuid) {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT * FROM players WHERE uuid = ?")) {
        ps.setString(1, uuid.toString());
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(extractPlayer(rs));
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Failed to load player", e);
    }
    return Optional.empty();
}
```

---

### 7. Configure HikariCP Appropriately

**Why:** Default settings may not be optimal for Minecraft server patterns.

**How:**
```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:sqlite:plugins/MyPlugin/data.db");

// For SQLite (single-threaded writes)
config.setMaximumPoolSize(1);  // SQLite only supports one writer

// For MySQL/MariaDB
config.setMaximumPoolSize(10);  // Match your async thread count
config.setMinimumIdle(10);      // Fixed pool recommended
config.setConnectionTimeout(5000);  // 5 second timeout
config.setIdleTimeout(600000);  // 10 minute idle timeout
config.setMaxLifetime(1800000); // 30 minute max lifetime

HikariDataSource dataSource = new HikariDataSource(config);
```

---

### 8. Reduce Tick Frequency for Non-Critical Tasks

**Why:** Not everything needs to run every tick (20/second).

**How:**
```java
// Particle effects: every 5-10 ticks (4-2 times/second)
Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
    updateParticles();
}, 5, 5);  // Every 5 ticks

// Collection proximity checks: every 20 ticks (1/second)
Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
    checkCollectionProximity();
}, 20, 20);

// Autosave: every 6000 ticks (5 minutes)
Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
    saveAllPlayersAsync();
}, 6000, 6000);
```

---

## Detection Checklist

Use this checklist when auditing a plugin for performance issues:

- [ ] **Particle loops**: Look for nested player/location iteration
- [ ] **Entity searches**: Check for `getNearbyEntities` in frequent code paths
- [ ] **Database calls**: Ensure all DB operations are async with batching
- [ ] **PlayerMoveEvent**: Check for heavy operations without throttling
- [ ] **Collection storage**: Verify UUID storage instead of Player objects
- [ ] **Thread safety**: Check collections accessed from multiple threads
- [ ] **Scheduler usage**: Verify Folia-compatible schedulers
- [ ] **Object creation**: Check for `new Location()` in hot paths
- [ ] **NamespacedKey reuse**: Verify keys are stored as constants
- [ ] **Connection management**: Verify try-with-resources pattern

---

## Profiling Tools

| Tool | Purpose | Command |
|------|---------|---------|
| Spark | CPU/memory profiling | `/spark profiler start` |
| Spark | TPS monitoring | `/spark tps` |
| Spark | Heap analysis | `/spark heapdump` |
| Timings (deprecated) | Legacy profiling | `/timings on` |

---

## Sources

- [PaperMC Documentation - Scheduling](https://docs.papermc.io/paper/dev/scheduler/)
- [PaperMC Documentation - Profiling](https://docs.papermc.io/paper/profiling/)
- [PaperMC Documentation - PDC](https://docs.papermc.io/paper/dev/pdc/)
- [Spark Profiler](https://spark.lucko.me/)
- [YouHaveTrouble Minecraft Optimization Guide](https://github.com/YouHaveTrouble/minecraft-optimization)
- [Paper Chan's Optimization Guide](https://paper-chan.moe/paper-optimization/)
- [SpigotMC Wiki - Scheduler Programming](https://www.spigotmc.org/wiki/scheduler-programming/)
- [SpigotMC Wiki - MySQL Integration](https://www.spigotmc.org/wiki/mysql-database-integration-with-your-plugin/)
- [HikariCP Best Practices](https://github.com/brettwooldridge/HikariCP)
- [FancyHolograms Documentation](https://docs.fancyinnovations.com/fancyholograms/)
