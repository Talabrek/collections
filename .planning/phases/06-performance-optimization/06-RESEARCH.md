# Phase 6: Performance Optimization - Research

**Researched:** 2026-01-21
**Domain:** Minecraft Plugin Performance (Particles, Spatial Indexing, Database Batching)
**Confidence:** HIGH

## Summary

This research addresses three remaining performance requirements for the Collections plugin at network scale (50+ concurrent players):

1. **PERF-01 (Particle Task):** The current particle task uses O(players x collectibles) iteration. This must be optimized to chunk-based lookups.
2. **PERF-03 (Batch Inserts):** Database writes should use JDBC batch operations. However, research reveals SQLite-specific insight: transaction wrapping provides the major performance gain, not batching itself.
3. **PERF-04 (Spawn Finder Allocations):** The AdaptiveSpawnFinder pre-allocates a full grid of Location objects. This should use lazy iteration to avoid thousands of temporary allocations.

**Note:** PERF-02 (entity index) was completed in Phase 5 as part of the entityToCollectible dual-index implementation.

**Primary recommendation:** Implement chunk-based spatial index for collectibles, use Paper's ParticleBuilder with `receivers(radius, byDistance)`, and replace grid point List with lazy Iterator.

## Standard Stack

The established libraries/tools for this domain:

### Core (Already In Use)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Paper API | 1.21.4 | ParticleBuilder API | Native, optimized particle distribution |
| HikariCP | 5.1.0 | Connection pooling | Industry standard, already configured |
| SQLite JDBC | bundled | Database driver | Lightweight, file-based storage |

### Supporting (No New Dependencies Required)
| Pattern | Purpose | When to Use |
|---------|---------|-------------|
| ConcurrentHashMap | Chunk-based spatial index | Store collectibles keyed by chunk coordinates |
| Long chunk key | Efficient chunk lookup | Pack (chunkX, chunkZ) into single long |
| Java Iterator | Lazy grid iteration | Avoid pre-allocating Location lists |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom spatial index | Paper's getNearbyEntities | getNearbyEntities creates new collections each call, O(n) entity scan |
| JDBC batch | Individual inserts in transaction | SQLite: nearly identical performance, simpler code |
| Lazy Iterator | Stream API | Iterator gives more control over termination |

**Installation:**
No new dependencies required. All patterns use existing APIs.

## Architecture Patterns

### Recommended Project Structure

No new files required. Modifications to existing classes:

```
src/main/java/com/blockworlds/collections/
├── manager/SpawnManager.java         # Add chunk-based spatial index
├── task/ParticleTask.java            # Rewrite to use chunk lookups + ParticleBuilder
├── spawn/AdaptiveSpawnFinder.java    # Replace List<Location> with Iterator
└── storage/SQLiteStorage.java        # Batch inserts (optional - transaction wrapping sufficient)
```

### Pattern 1: Chunk-Based Spatial Index

**What:** Store collectibles in a Map keyed by packed chunk coordinates for O(1) chunk lookup.

**When to use:** Any time you need to find objects near a location without iterating all objects.

**Example:**
```java
// Pack chunk coordinates into long key
private long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
}

// Spatial index: chunkKey -> collectibles in that chunk
private final Map<Long, Set<UUID>> collectiblesByChunk = new ConcurrentHashMap<>();

// Add to index
public void indexCollectible(Collectible c) {
    long key = chunkKey(c.getChunkX(), c.getChunkZ());
    collectiblesByChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
            .add(c.id());
}

// Remove from index
public void unindexCollectible(Collectible c) {
    long key = chunkKey(c.getChunkX(), c.getChunkZ());
    Set<UUID> set = collectiblesByChunk.get(key);
    if (set != null) {
        set.remove(c.id());
        if (set.isEmpty()) {
            collectiblesByChunk.remove(key);
        }
    }
}

// Get nearby collectibles (within chunk radius)
public List<Collectible> getCollectiblesNearChunk(int centerChunkX, int centerChunkZ, int radius) {
    List<Collectible> result = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
            long key = chunkKey(centerChunkX + dx, centerChunkZ + dz);
            Set<UUID> ids = collectiblesByChunk.get(key);
            if (ids != null) {
                for (UUID id : ids) {
                    Collectible c = activeCollectibles.get(id);
                    if (c != null && c.spawned()) {
                        result.add(c);
                    }
                }
            }
        }
    }
    return result;
}
```

**Source:** Chunk-based spatial partitioning is a standard game development pattern. The existing codebase already uses `chunkX >> 4` calculations in `Collectible.getChunkX()`.

### Pattern 2: Player-Centric Particle Distribution with ParticleBuilder

**What:** Iterate players once, find nearby collectibles per player using chunk index, use ParticleBuilder.

**When to use:** Particle distribution that must filter by player-specific conditions (goggle tier visibility).

**Example:**
```java
private void spawnParticles() {
    // O(players) outer loop
    for (Player player : Bukkit.getOnlinePlayers()) {
        Chunk chunk = player.getLocation().getChunk();

        // O(1) chunk lookup per radius
        List<Collectible> nearby = getCollectiblesNearChunk(
            chunk.getX(), chunk.getZ(),
            particleDistanceChunks  // 2 chunks = 32 blocks
        );

        // O(nearby collectibles) - much smaller than O(all collectibles)
        for (Collectible collectible : nearby) {
            if (!canPlayerSee(player, collectible)) continue;

            Location loc = collectible.location();
            Particle particle = collectible.tier().getParticle();

            // Paper ParticleBuilder - handles player-specific particle sending
            Particle.FLAME.builder()
                .location(loc)
                .count(3)
                .offset(0.2, 0.2, 0.2)
                .receivers(player)  // Single player
                .spawn();
        }
    }
}
```

**Source:** [Paper ParticleBuilder API](https://jd.papermc.io/paper/1.21.4/com/destroystokyo/paper/ParticleBuilder.html)

### Pattern 3: Lazy Grid Point Iterator

**What:** Generate grid points on-demand instead of pre-allocating a List.

**When to use:** When generating coordinate sequences where you may stop early (found valid location).

**Example:**
```java
/**
 * Lazy iterator over grid points - generates coordinates on demand.
 * Avoids allocating thousands of Location objects upfront.
 */
private class GridPointIterator implements Iterator<int[]> {
    private final int minX, maxX, minZ, maxZ, spacing;
    private int currentX, currentZ;
    private boolean hasNext = true;

    GridPointIterator(int centerX, int centerZ, int radius, int spacing,
                      SpawnZone zone) {
        this.spacing = spacing;
        this.minX = centerX - radius;
        this.maxX = centerX + radius;
        this.minZ = centerZ - radius;
        this.maxZ = centerZ + radius;

        // Constrain to zone bounds if specified
        if (zone.bounds() != null) {
            SpawnZone.Bounds bounds = zone.bounds();
            this.minX = Math.max(this.minX, bounds.minX());
            this.maxX = Math.min(this.maxX, bounds.maxX());
            this.minZ = Math.max(this.minZ, bounds.minZ());
            this.maxZ = Math.min(this.maxZ, bounds.maxZ());
        }

        this.currentX = this.minX;
        this.currentZ = this.minZ;
        checkHasNext();
    }

    private void checkHasNext() {
        hasNext = currentX <= maxX && currentZ <= maxZ;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public int[] next() {
        if (!hasNext) throw new NoSuchElementException();

        int[] result = new int[] { currentX, currentZ };

        // Advance to next point
        currentX += spacing;
        if (currentX > maxX) {
            currentX = minX;
            currentZ += spacing;
        }
        checkHasNext();

        return result;
    }
}

// Usage in findLocation():
Iterator<int[]> gridPoints = new GridPointIterator(center.getBlockX(), center.getBlockZ(),
                                                    radius, gridSpacing, zone);
List<int[]> shuffled = shuffleIterator(gridPoints, maxPerPass);
```

**Source:** [Lazy Iterator pattern for memory efficiency](https://blog.scottlogic.com/2011/06/24/lazy-lists-in-java.html)

### Pattern 4: JDBC Batch with Transaction Wrapping (SQLite-Optimized)

**What:** For SQLite, the key optimization is transaction wrapping, not batching. Batch syntax is still cleaner for bulk operations.

**When to use:** Saving multiple records (e.g., all collected items for a player).

**SQLite-Specific Insight:** Research shows that for SQLite, batching itself provides no performance benefit over individual statements within a single transaction. The critical factor is wrapping operations in a transaction (disabling auto-commit). However, batch syntax is still cleaner code.

**Example:**
```java
// Already in place: saveCollectedItems() uses individual inserts per item
// Optimization: use addBatch/executeBatch within existing transaction

private void saveCollectedItemsBatch(Connection conn, UUID playerId,
        PlayerProgress.CollectionProgress colProgress) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT OR IGNORE INTO collected_items
            (uuid, collection_id, item_id, collected_date)
            VALUES (?, ?, ?, ?)
            """)) {
        for (String itemId : colProgress.getCollectedItems()) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, colProgress.getCollectionId());
            stmt.setString(3, itemId);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.addBatch();
        }
        stmt.executeBatch();
    }
}
```

**Source:** [Fastest Way to Write to SQLite from Java](https://www.raphaelbauer.com/posts/fastest-way-to-write-to-sqlite-from-java-in-2023/) - "Batching did NOT bring any benefit for SQLite. The key is using a single transaction."

### Anti-Patterns to Avoid

- **O(players x collectibles) iteration:** Current ParticleTask iterates all collectibles then filters by distance per player. Complexity grows with both dimensions.
- **Pre-allocating full coordinate grid:** Current AdaptiveSpawnFinder creates ArrayList with potentially thousands of Location objects, then shuffles, then only uses a subset.
- **Location object creation in hot paths:** Creating `new Location()` in every iteration wastes memory. Use primitives or reusable objects where possible.
- **Relying on batching alone for SQLite:** JDBC batching provides minimal SQLite benefit. Transaction management (setAutoCommit(false) + commit()) is what matters.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Player-filtered particles | Manual distance checks + spawnParticle() | ParticleBuilder.receivers(radius, byDistance) | Built into Paper, handles edge cases |
| Chunk coordinate packing | Multiple map lookups | `(long)x << 32 | (z & 0xFFFFFFFFL)` | Standard bit-packing for 2D->1D key |
| Lazy iteration | Custom stream/collector | Iterator interface | Standard Java pattern, widely understood |

**Key insight:** The existing codebase already has chunk coordinate methods in `Collectible` (`getChunkX()`, `getChunkZ()`). Leverage these rather than recalculating.

## Common Pitfalls

### Pitfall 1: Chunk Boundary Edge Cases

**What goes wrong:** Players at chunk boundaries may not see collectibles in adjacent chunks if radius is too small.
**Why it happens:** Particle visibility extends beyond chunk boundaries, but chunk lookup is discrete.
**How to avoid:** Use radius of 2 chunks (32 blocks) to match particle visibility distance.
**Warning signs:** Players report collectibles not glowing when they should be visible.

### Pitfall 2: Iterator Invalidation During Iteration

**What goes wrong:** ConcurrentModificationException when collectible despawns while iterating nearby collectibles.
**Why it happens:** Iteration over concurrent collection while another thread modifies it.
**How to avoid:** Use ConcurrentHashMap.newKeySet() and defensive copies, or check collectible.spawned() during iteration.
**Warning signs:** Sporadic CME in particle task logs.

### Pitfall 3: SQLite Connection Contention

**What goes wrong:** SQLITE_BUSY errors despite batch operations.
**Why it happens:** Multiple threads trying to write simultaneously.
**How to avoid:**
1. WAL mode is already enabled (allows concurrent reads during writes)
2. busy_timeout is already set to 30 seconds
3. Keep HikariCP pool size at 10 for SQLite
**Warning signs:** Database timeout warnings in logs.

### Pitfall 4: Lazy Iterator Memory "Savings" Negated

**What goes wrong:** Collecting lazy iterator into List before shuffling negates memory benefits.
**Why it happens:** Shuffle requires random access, which needs materialization.
**How to avoid:** Sample randomly from iterator using reservoir sampling, or accept sequential iteration with random start offset.
**Warning signs:** High memory usage in AdaptiveSpawnFinder despite lazy iterator.

## Code Examples

Verified patterns from official sources:

### ParticleBuilder Usage (Paper API)
```java
// Source: https://jd.papermc.io/paper/1.21.4/com/destroystokyo/paper/ParticleBuilder.html
// Send particle to specific player
Particle.FLAME.builder()
    .location(location)
    .count(3)
    .offset(0.2, 0.2, 0.2)
    .receivers(player)
    .spawn();

// Send to all players within 32 blocks (sphere)
Particle.FLAME.builder()
    .location(location)
    .count(3)
    .receivers(32, true)  // true = spherical distance
    .spawn();
```

### JDBC Batch Insert
```java
// Source: https://www.baeldung.com/jdbc-batch-processing
conn.setAutoCommit(false);
try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO collected_items (uuid, collection_id, item_id) VALUES (?, ?, ?)")) {
    for (String itemId : items) {
        ps.setString(1, playerId.toString());
        ps.setString(2, collectionId);
        ps.setString(3, itemId);
        ps.addBatch();
    }
    ps.executeBatch();
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw e;
}
```

### Long Chunk Key Packing
```java
// Standard bit-packing for 2D coordinates
private static long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
}

// Unpacking (if needed)
private static int chunkX(long key) {
    return (int) (key >> 32);
}

private static int chunkZ(long key) {
    return (int) key;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| World.spawnParticle() | ParticleBuilder API | Paper 1.12+ | Cleaner API, automatic player filtering |
| Armor stand holograms | Display entities | 1.19.4 | Lower overhead (already using Interaction entities) |
| BukkitScheduler | Folia-compatible schedulers | Paper 1.20+ | Future-proofing for Folia |

**Deprecated/outdated:**
- `World.spawnParticle(Particle, Location, int, double, double, double, double, Object)` - Use ParticleBuilder instead
- Pre-Java 8 iteration patterns - Use Iterator or Stream where appropriate

## Open Questions

Things that couldn't be fully resolved:

1. **Shuffle Requirement vs Memory Savings**
   - What we know: Current code shuffles grid points for randomness
   - What's unclear: Is randomness strictly required, or can we use random start offset?
   - Recommendation: Try reservoir sampling or random offset; if randomness quality is insufficient, fall back to partial materialization

2. **Optimal Batch Size**
   - What we know: SQLite shows no performance difference with batching
   - What's unclear: Whether larger batch sizes cause memory issues with very large collections
   - Recommendation: Keep current per-player-save granularity; transaction wrapping is the key optimization

## Sources

### Primary (HIGH confidence)
- [Paper ParticleBuilder API Javadoc](https://jd.papermc.io/paper/1.21.4/com/destroystokyo/paper/ParticleBuilder.html) - receivers() methods
- [Paper Particles Documentation](https://docs.papermc.io/paper/dev/particles/) - ParticleBuilder usage
- [Fastest Way to Write to SQLite from Java](https://www.raphaelbauer.com/posts/fastest-way-to-write-to-sqlite-from-java-in-2023/) - SQLite batching insight

### Secondary (MEDIUM confidence)
- [Baeldung JDBC Batch Processing](https://www.baeldung.com/jdbc-batch-processing) - Batch insert patterns
- [DZone Performant Batch Inserts](https://dzone.com/articles/performant-batch-inserts-using-jdbc) - Best practices

### Tertiary (LOW confidence)
- [Lazy Lists in Java](https://blog.scottlogic.com/2011/06/24/lazy-lists-in-java.html) - Lazy iteration concepts
- Existing codebase research document (`.planning/research/PERFORMANCE.md`) - Project-specific patterns

## Metadata

**Confidence breakdown:**
- Chunk-based spatial index: HIGH - Standard pattern, simple implementation
- ParticleBuilder: HIGH - Official Paper API with documentation
- JDBC Batching: HIGH - Well-documented pattern; SQLite-specific insight verified
- Lazy iteration: MEDIUM - Pattern is standard, but shuffle requirement adds complexity

**Research date:** 2026-01-21
**Valid until:** 2026-02-21 (30 days - patterns are stable)
