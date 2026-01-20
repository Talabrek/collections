# Research Summary: Collections Plugin Audit

**Synthesized:** 2026-01-21
**Research Files:** PERFORMANCE.md, BUGS.md, CONCURRENCY.md, DATA_INTEGRITY.md
**Overall Confidence:** HIGH
**Target:** Paper 1.21.4 collectibles plugin for multi-server network deployment

---

## Executive Summary

This audit examines a Paper 1.21.4 collectibles plugin that implements EQ2-style collectible spawning, GUIs, particles, and player persistence. The plugin uses standard patterns (HikariCP, CompletableFuture, ConcurrentHashMap) but has several critical gaps for production deployment, especially on multi-server networks.

**The three highest-priority issues are:**

1. **Fire-and-forget saves on player quit** - Player data saves are async without blocking. If a player reconnects quickly (same server or via proxy), the load may complete before the previous save, causing data loss. This is the most likely source of player-reported "lost progress."

2. **SQLite is unsuitable for multi-server** - The current SQLite storage cannot be shared between servers. Multi-server deployment requires MySQL/MariaDB/PostgreSQL with cross-server synchronization (Redis pub/sub or a sync plugin like HuskSync).

3. **Missing Folia scheduler migration** - The plugin uses `Bukkit.getScheduler().runTask()` in at least one location (PlayerListener). While functional on Paper, this breaks on Folia and should be migrated to EntityScheduler/RegionScheduler for future-proofing.

**Recommended approach:** Audit for the high-risk issues first (data persistence timing, GUI click handling, exception swallowing), then address medium-risk items (SQLite configuration, transaction atomicity, scheduler migration).

---

## Key Findings

### From PERFORMANCE.md

| Finding | Impact | Status |
|---------|--------|--------|
| O(n*m) particle iteration | SEVERE | Check particle/spawn systems for nested player-collectible loops |
| getNearbyEntities abuse | MODERATE-SEVERE | Verify spatial partitioning for collectible lookups |
| Unbatched database writes | MODERATE | Check for batch operations on bulk saves |
| Synchronous database on main thread | SEVERE | Verify all DB calls are async |
| PlayerMoveEvent abuse | MODERATE-SEVERE | Check for heavy operations in move handlers |
| Player reference storage | MODERATE (memory leak) | Verify UUID storage, not Player objects |
| NamespacedKey recreation | MINOR | Check for static key constants |
| Armor stand holograms | MODERATE | Consider Display entities (1.19.4+) if using armor stands |

**Key recommendation:** Use Spark profiler to identify actual bottlenecks before optimizing. Priority should be particle systems and entity lookups if they exist.

### From BUGS.md

| Bug Pattern | Plugin Risk | Detection |
|-------------|-------------|-----------|
| Async Bukkit API access | HIGH | Search for Bukkit calls inside CompletableFuture/async blocks |
| ConcurrentModificationException | MEDIUM | Check shared collections between threads |
| Player reference retention | MEDIUM | Search for `Map<Player, ...>` |
| Uncancelled scheduled tasks | MEDIUM | Verify task cleanup in PlayerQuitEvent and onDisable |
| Player join data race | HIGH | Verify data loaded before GUI/interaction enabled |
| Chunk load entity race | MEDIUM | Check entity access in ChunkLoadEvent |
| GUI click duplication | HIGH | Verify ALL click types cancelled in InventoryClickEvent |
| Entity despawn cleanup | MEDIUM | Check ChunkUnloadEvent handling |
| HikariCP pool exhaustion | MEDIUM | Verify try-with-resources pattern everywhere |
| CompletableFuture exception swallowing | HIGH | Check all futures have .exceptionally() handlers |
| onDisable data loss | HIGH | Verify blocking wait on saves in onDisable |
| Config static reference | LOW | Check config caching after reload |

**Key recommendation:** The GUI click handling and CompletableFuture exception handling are the most likely sources of existing bugs. Prioritize auditing these.

### From CONCURRENCY.md

| Pattern | Status in Codebase |
|---------|-------------------|
| ConcurrentHashMap for cache | IMPLEMENTED |
| CompletableFuture for async DB | IMPLEMENTED |
| AtomicBoolean for collect race | IMPLEMENTED |
| Folia-compatible schedulers | PARTIAL - needs migration |
| computeIfAbsent for atomic loads | IMPLEMENTED |
| Thread-safe iteration | NEEDS VERIFICATION |

**Key recommendation:** Migrate from `Bukkit.getScheduler().runTask()` to `entity.getScheduler().run()` for Folia compatibility. This is not urgent but recommended for future-proofing.

### From DATA_INTEGRITY.md

| Issue | Current State | Risk |
|-------|---------------|------|
| Player quit save timing | Fire-and-forget | **CRITICAL** |
| Exception handling severity | WARNING level only | MEDIUM |
| SQLite WAL mode | Not configured | MEDIUM |
| Transaction atomicity | Auto-commit | MEDIUM |
| Multi-server support | None (SQLite only) | **CRITICAL for networks** |
| Retry mechanism | None | MEDIUM |
| Periodic autosave | None | LOW |

**Key recommendation:** For multi-server deployment, the storage layer needs significant changes. For single-server, add blocking saves on quit and SQLite WAL mode configuration.

---

## Audit Priorities

Ranked by risk and likelihood of causing issues:

### Priority 1: Critical (Fix Before Production)

1. **Player quit save timing** - Change to blocking save with timeout
   - Location: PlayerListener.java onPlayerQuit
   - Fix: `.get(5, TimeUnit.SECONDS)` instead of `.thenRun()`
   - Risk: Data loss on quick reconnect or server switch

2. **GUI click duplication** - Verify all click types handled
   - Location: All InventoryClickEvent handlers
   - Check: SHIFT_LEFT, SHIFT_RIGHT, HOTBAR_SWAP, DOUBLE_CLICK, DRAG
   - Risk: Item duplication exploits

3. **CompletableFuture exception handling** - Ensure no silent failures
   - Location: All async chains in PlayerDataManager, Storage
   - Check: Every `.thenApply()` chain has `.exceptionally()`
   - Risk: Silent data loss

### Priority 2: High (Should Fix)

4. **SQLite configuration** - Add WAL mode and busy timeout
   - Location: Storage initialization
   - Add: `PRAGMA journal_mode=WAL`, `PRAGMA busy_timeout=30000`
   - Risk: SQLITE_BUSY errors under load

5. **Transaction atomicity** - Wrap player saves in transaction
   - Location: Storage.savePlayer()
   - Fix: `conn.setAutoCommit(false)` with commit/rollback
   - Risk: Partial saves leaving inconsistent state

6. **Player data load race** - Verify interaction blocked until loaded
   - Check: GUI commands, collection interactions
   - Risk: NPE or missing progress on quick interactions after join

### Priority 3: Medium (Should Address)

7. **Scheduler migration** - Replace BukkitScheduler with Folia schedulers
   - Location: Any `Bukkit.getScheduler().runTask()` usage
   - Fix: Use `entity.getScheduler().run()` or `Bukkit.getRegionScheduler()`
   - Risk: Folia incompatibility, minor

8. **Exception severity levels** - Upgrade critical failures to SEVERE
   - Location: All `.exceptionally()` handlers for saves
   - Risk: Missed alerts for data loss in logs

9. **Task cleanup verification** - Ensure tasks cancelled on quit/disable
   - Check: All BukkitTask/scheduled task tracking
   - Risk: Memory leaks, duplicate execution

### Priority 4: Low (Nice to Have)

10. **Retry queue for failed saves** - Add resilience
11. **Periodic autosave** - Crash protection
12. **Database integrity check** - Startup validation

---

## Risk Areas

### Critical Risk: Multi-Server Deployment

The current architecture **cannot support multi-server networks** without significant changes:

1. **Storage:** SQLite must be replaced with MySQL/MariaDB/PostgreSQL
2. **Synchronization:** Need cross-server cache invalidation (Redis recommended)
3. **Race conditions:** Server switch can cause stale reads (need locking or versioning)
4. **UUID forwarding:** Proxy must forward UUIDs or players get different UUIDs per server

**Minimum viable for multi-server:**
- MySQL/MariaDB storage adapter
- Optimistic locking (version column) on player data
- Proper exception handling with retry

**Recommended for multi-server:**
- All of the above PLUS Redis pub/sub for cache invalidation
- OR integrate with existing sync solution (HuskSync)

### High Risk: Data Loss Vectors

Current code has these data loss vectors:

1. Quick player reconnect before async save completes
2. Server crash during write-behind caching period
3. Database unavailable during save (no retry)
4. Exception in CompletableFuture chain silently swallowed
5. Partial save due to non-atomic transactions

### Medium Risk: Performance Under Load

Potential performance issues to verify:

1. Particle systems with many players/collectibles
2. Entity searches without spatial indexing
3. Database under high player churn (join/quit spam)
4. GUI operations with many inventory slots

---

## Quick Reference

### Detection Commands

```bash
# Find async Bukkit API access
grep -r "CompletableFuture" --include="*.java" | xargs grep -l "getWorld\|teleport\|getEntities"

# Find Player object storage
grep -rn "Map<Player" --include="*.java"
grep -rn "List<Player>" --include="*.java"

# Find BukkitScheduler usage (needs migration)
grep -rn "Bukkit.getScheduler()" --include="*.java"
grep -rn "runTask(" --include="*.java"

# Find exception handling gaps
grep -rn "thenApply\|thenAccept" --include="*.java" | grep -v "exceptionally"

# Find GUI click handlers
grep -rn "InventoryClickEvent" --include="*.java"
```

### Fix Patterns

**Blocking save on quit:**
```java
try {
    playerDataManager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    logger.warning("Save timed out for " + playerId);
} catch (Exception e) {
    logger.log(Level.SEVERE, "Save failed for " + playerId, e);
}
```

**GUI click cancellation:**
```java
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    if (isMyGUI(event.getInventory())) {
        event.setCancelled(true);  // FIRST LINE - cancel everything
        // Then handle specific slots
    }
}
```

**SQLite WAL mode:**
```java
try (Statement stmt = conn.createStatement()) {
    stmt.execute("PRAGMA journal_mode=WAL");
    stmt.execute("PRAGMA busy_timeout=30000");
    stmt.execute("PRAGMA synchronous=NORMAL");
}
```

**Folia-compatible scheduling:**
```java
// Instead of: Bukkit.getScheduler().runTask(plugin, () -> { ... });
// Use:
player.getScheduler().run(plugin, task -> {
    // Code that needs player context
}, null);
```

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Performance patterns | HIGH | Verified against Paper docs, Spark profiler docs, community guides |
| Bug patterns | HIGH | Verified against Paper issue tracker, Spigot wiki, community experience |
| Concurrency patterns | HIGH | Verified against Folia docs, Java concurrency docs |
| Data integrity | HIGH | Verified against Paper docs, SQLite docs, established plugin patterns |
| Multi-server requirements | MEDIUM | Based on HuskSync docs and general distributed systems knowledge |

### Gaps Requiring Further Investigation

1. **Actual performance profile** - Need Spark profiler run on live server to identify real bottlenecks
2. **Multi-server sync solution choice** - Depends on network infrastructure (existing Redis? HuskSync already deployed?)
3. **GUI complexity** - Need to trace all GUI classes to verify click handling completeness
4. **Chunk-based entity tracking** - Need to verify if plugin tracks entities across chunk boundaries

---

## Sources

### Official Documentation
- [PaperMC Scheduling](https://docs.papermc.io/paper/dev/scheduler/)
- [PaperMC Profiling](https://docs.papermc.io/paper/profiling/)
- [PaperMC PDC](https://docs.papermc.io/paper/dev/pdc/)
- [PaperMC Using Databases](https://docs.papermc.io/paper/dev/using-databases/)
- [Folia Documentation](https://docs.papermc.io/folia/)
- [PaperMC Folia Support Guide](https://docs.papermc.io/paper/dev/folia-support/)

### Tools
- [Spark Profiler](https://spark.lucko.me/)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)

### Community Resources
- [Minecraft Optimization Guide](https://github.com/YouHaveTrouble/minecraft-optimization)
- [Paper Chan's Optimization Guide](https://paper-chan.moe/paper-optimization/)
- [MultiPaper Threading Guide](https://multipaper.io/shreddedpaper/writing-a-multithreaded-plugin.html)

### SQLite
- [SQLite WAL Mode](https://sqlite.org/wal.html)
- [SQLite Busy Timeout](https://www.sqlite.org/c3ref/busy_timeout.html)

### Multi-Server
- [HuskSync](https://github.com/WiIIiam278/HuskSync)
- [Velocity Player Information Forwarding](https://docs.papermc.io/velocity/player-information-forwarding/)

---

## Next Steps

1. **Immediate:** Run detection commands above to identify specific instances
2. **Phase 1:** Fix critical issues (quit save timing, GUI click handling, exception handling)
3. **Phase 2:** Add SQLite optimizations (WAL mode, transactions)
4. **Phase 3:** Migrate schedulers for Folia compatibility
5. **Phase 4:** If multi-server required, plan storage migration to MySQL + sync solution

The roadmapper should structure phases around these priorities, with Phase 1 being a "hardening" phase focused on data integrity before any feature work.
