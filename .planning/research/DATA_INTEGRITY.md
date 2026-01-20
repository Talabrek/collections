# Data Integrity for Paper Plugins

**Domain:** Player data persistence in Paper/Bukkit plugins
**Researched:** 2026-01-21
**Confidence:** HIGH (verified against official docs, established patterns, codebase analysis)

---

## Executive Summary

Data integrity in Paper plugins requires careful management of the player lifecycle, async operation handling, and database robustness. The primary risks are:

1. **Player quit during async save** - Data loss if save hasn't completed
2. **Server shutdown timing** - Async tasks rejected after executor shutdown
3. **Exception swallowing** - `CompletableFuture.exceptionally()` hiding failures
4. **Multi-server race conditions** - Stale cache when player switches servers
5. **SQLite concurrency** - `SQLITE_BUSY` errors under load

This document provides patterns to address each risk, with specific recommendations for the Collections plugin's current architecture.

---

## Lifecycle

### Player Join Phase

- **Risks:**
  - Player interacts before data loads (cache miss -> null progress)
  - Multiple rapid reconnects cause duplicate load operations
  - Async load completes after player has already quit

- **Best practice:**
  - Use `AsyncPlayerPreLoginEvent` for data loading (fires before player entity exists)
  - Block interaction until data is confirmed loaded
  - Use `computeIfAbsent` on pending loads map to prevent duplicate loads (current code does this correctly)
  - Check `player.isOnline()` before caching loaded data

- **Paper 1.21.7+ Note:**
  - `PlayerLoginEvent` is deprecated in favor of `PlayerConnectionValidateLoginEvent`
  - Plugins should migrate to avoid legacy loading path warnings

### Player Active Phase

- **Risks:**
  - Write-behind caching loses data on crash
  - Concurrent modifications to cached data from multiple threads
  - Memory leaks from unbounded cache growth

- **Best practice:**
  - Write-through for critical operations (items collected, rewards claimed)
  - Use `ConcurrentHashMap` for cache (current code does this)
  - Store UUIDs, never Player references
  - Clean up cache entries on quit

### Player Quit Phase

- **Risks:**
  - Save operation not completed before player data evicted
  - `PlayerQuitEvent` only fires on clean disconnects
  - Cache cleared before save writes to database
  - Server may shut down during save

- **Best practice:**
  - Save synchronously or use `join()` on quit (blocks main thread but ensures data)
  - Listen to both `PlayerQuitEvent` and `PlayerKickEvent`
  - Save BEFORE removing from cache
  - Use timeout on join to prevent indefinite blocking

- **Current code issue:**
  ```java
  // PlayerListener.java - save is fire-and-forget
  playerDataManager.saveAndUnload(playerId)
      .thenRun(() -> { /* logging only */ });
  ```
  The save may not complete before the player object is garbage collected.

### Server Shutdown Phase

- **Risks:**
  - `RejectedExecutionException` when submitting to shut-down executor
  - Async tasks cancelled mid-flight
  - HikariCP pool closed before pending saves complete
  - Data loss for all online players

- **Best practice:**
  - In `onDisable()`, block until all saves complete with timeout
  - Use `CompletableFuture.allOf().join()` (current code does this correctly)
  - Shutdown storage AFTER all saves complete (current code does this)
  - Set reasonable timeout (60 seconds is good)

- **Current code (correct pattern):**
  ```java
  // Collections.java onDisable()
  try {
      playerDataManager.saveAll().get();  // Blocking wait
  } catch (Exception e) {
      getLogger().warning("Failed to save player data on shutdown: " + e.getMessage());
  }
  storage.shutdown();  // After saves complete
  ```

---

## Patterns

### Pattern: Write-Through for Critical Data

- **Purpose:** Prevents data loss by persisting immediately on mutation
- **Implementation:**
  ```java
  public boolean addItem(UUID playerId, String collectionId, String itemId) {
      PlayerProgress progress = cache.get(playerId);
      if (progress == null) return false;

      boolean added = progress.addItem(collectionId, itemId);
      if (added) {
          // CRITICAL: Persist immediately, don't wait for quit
          storage.saveCollectedItem(playerId, collectionId, itemId)
              .exceptionally(t -> {
                  // Log AND potentially retry
                  logger.severe("CRITICAL: Failed to save item for " + playerId);
                  return null;
              });
      }
      return added;
  }
  ```
- **Edge cases:**
  - Database temporarily unavailable: queue for retry
  - Player collects during server shutdown: may still be lost
  - Item duplication: use `INSERT OR IGNORE` (current code does this)

- **Current code status:** IMPLEMENTED - `PlayerDataManager.addItem()` persists immediately

### Pattern: Graceful Async Task Completion

- **Purpose:** Ensures async saves complete before shutdown
- **Implementation:**
  ```java
  public CompletableFuture<Void> saveAll() {
      if (cache.isEmpty()) {
          return CompletableFuture.completedFuture(null);
      }

      CompletableFuture<?>[] futures = cache.keySet().stream()
          .map(this::savePlayer)
          .toArray(CompletableFuture[]::new);

      return CompletableFuture.allOf(futures)
          .orTimeout(60, TimeUnit.SECONDS)
          .exceptionally(t -> {
              logger.severe("Save-all failed or timed out: " + t.getMessage());
              return null;
          });
  }
  ```
- **Edge cases:**
  - Single save hangs: timeout protects others
  - Many players online: may exceed timeout (consider batching)
  - Server force-killed: no mitigation possible (external backup needed)

- **Current code status:** IMPLEMENTED in `PlayerDataManager.saveAll()`

### Pattern: Verified Exception Handling

- **Purpose:** Ensures exceptions don't get swallowed silently
- **Implementation:**
  ```java
  // BAD: Exception swallowed, returns null silently
  .exceptionally(t -> {
      logger.warning("Failed to save");
      return null;
  });

  // BETTER: Exception logged with context, consider retry
  .exceptionally(t -> {
      logger.log(Level.SEVERE, "Failed to save player " + playerId +
          " - DATA MAY BE LOST", t);
      // Consider: add to retry queue
      return null;
  });

  // BEST: Use handle() for both success and failure
  .handle((result, t) -> {
      if (t != null) {
          logger.log(Level.SEVERE, "Save failed for " + playerId, t);
          retryQueue.add(new SaveRetry(playerId, progress.copy()));
      } else {
          logger.fine("Save completed for " + playerId);
      }
      return result;
  });
  ```
- **Edge cases:**
  - `TimeoutException` from `orTimeout()`: distinguish from DB errors
  - Chained futures: exception in early stage may not propagate
  - `exceptionally()` returning non-null can mask errors downstream

- **Current code issue:** Uses `exceptionally()` everywhere but only logs WARNING level. Critical data loss scenarios should log SEVERE.

### Pattern: SQLite WAL Mode and Busy Handling

- **Purpose:** Prevents `SQLITE_BUSY` errors and improves concurrent access
- **Implementation:**
  ```java
  public void initialize() {
      // ... HikariConfig setup ...
      dataSource = new HikariDataSource(config);

      // Enable WAL mode for concurrent reads during writes
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
          stmt.execute("PRAGMA journal_mode=WAL");
          stmt.execute("PRAGMA busy_timeout=30000");  // 30 second wait
          stmt.execute("PRAGMA synchronous=NORMAL");  // Balance safety/performance
      }
  }
  ```
- **Edge cases:**
  - WAL files on network drives: SQLite corruption risk (don't use network storage)
  - Long transactions: can still cause busy errors
  - Checkpoint timing: large WAL files if checkpoint delayed

- **Current code gap:** No WAL mode, no busy_timeout configuration

### Pattern: Transaction Wrapping for Atomicity

- **Purpose:** Ensures related operations succeed or fail together
- **Implementation:**
  ```java
  public CompletableFuture<Void> savePlayer(PlayerProgress progress) {
      return CompletableFuture.runAsync(() -> {
          try (Connection conn = dataSource.getConnection()) {
              conn.setAutoCommit(false);
              try {
                  // All saves in one transaction
                  saveBaseData(conn, progress);
                  saveCollectionProgress(conn, progress);
                  saveCollectedItems(conn, progress);
                  conn.commit();
              } catch (SQLException e) {
                  conn.rollback();
                  throw e;
              } finally {
                  conn.setAutoCommit(true);
              }
          }
      });
  }
  ```
- **Edge cases:**
  - Connection timeout during transaction: automatic rollback
  - Nested transactions: SQLite doesn't support (use savepoints if needed)
  - Large batch inserts: consider chunking to avoid lock contention

- **Current code gap:** Each statement is auto-committed separately. Player save is not atomic - partial saves possible.

### Pattern: Blocking Save on Player Quit

- **Purpose:** Guarantees data persistence before player entity cleanup
- **Implementation:**
  ```java
  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerQuit(PlayerQuitEvent event) {
      UUID playerId = event.getPlayer().getUniqueId();

      try {
          // Block until save completes (with timeout)
          playerDataManager.saveAndUnload(playerId)
              .get(5, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
          logger.warning("Save timed out for " + playerId + " - data may be lost");
      } catch (Exception e) {
          logger.log(Level.SEVERE, "Save failed for " + playerId, e);
      }
  }
  ```
- **Edge cases:**
  - Many simultaneous quits (server shutdown): can cause lag spikes
  - Main thread blocked: affects other event handlers
  - Alternative: HIGH priority, fire-and-forget but with retry queue

- **Current code issue:** Fire-and-forget save on quit. Player may rejoin before save completes, causing data loss.

### Pattern: Retry Queue for Failed Operations

- **Purpose:** Recovers from transient database failures
- **Implementation:**
  ```java
  private final Queue<SaveOperation> retryQueue = new ConcurrentLinkedQueue<>();
  private ScheduledFuture<?> retryTask;

  public void startRetryTask() {
      retryTask = scheduler.scheduleAtFixedRate(() -> {
          SaveOperation op;
          int retried = 0;
          while ((op = retryQueue.poll()) != null && retried < 10) {
              try {
                  op.execute();
                  retried++;
              } catch (Exception e) {
                  if (op.attempts < 3) {
                      op.attempts++;
                      retryQueue.add(op);
                  } else {
                      logger.severe("Giving up on save after 3 attempts: " + op);
                  }
              }
          }
      }, 5, 5, TimeUnit.SECONDS);
  }
  ```
- **Edge cases:**
  - Queue grows unbounded during outage: add max size
  - Retry during shutdown: drain queue synchronously in `onDisable()`
  - Order dependency: some operations may need ordering guarantees

- **Current code gap:** No retry mechanism. Failed saves are logged and lost.

---

## Multi-Server Considerations

### Problem: Stale Cache on Server Switch

When player switches servers via proxy:
1. Player quits Server A (save starts async)
2. Player joins Server B (load starts)
3. Server B load completes BEFORE Server A save completes
4. Player has stale/missing data

### Solutions

**Option 1: Redis-based locking (recommended for networks)**
```
Player switches: Server A -> Server B
1. Server A acquires lock for player UUID
2. Server A saves to MySQL/shared DB
3. Server A releases lock
4. Proxy signals Server B to proceed
5. Server B acquires lock, loads data, releases lock
```

**Option 2: Proxy-coordinated delays**
```
1. Proxy intercepts server switch
2. Proxy waits 1-2 seconds before connecting to new server
3. Gives time for async save to complete
4. Not reliable under load
```

**Option 3: Shared database with optimistic locking**
```sql
-- Add version column
ALTER TABLE players ADD COLUMN version INT DEFAULT 0;

-- Load includes version
SELECT *, version FROM players WHERE uuid = ?;

-- Save increments and checks version
UPDATE players SET ..., version = version + 1
WHERE uuid = ? AND version = ?;
-- If rows affected = 0, data was modified elsewhere -> reload
```

### Current Architecture Assessment

The Collections plugin uses:
- SQLite (local file, not shareable between servers)
- No versioning or locking mechanism
- No proxy integration

**For multi-server deployment:**
1. **Minimum:** Replace SQLite with MySQL/MariaDB/PostgreSQL
2. **Recommended:** Add Redis for cross-server cache invalidation
3. **Ideal:** Integrate with existing sync plugin (HuskSync, PlayerDataSync) or implement lock-based sync

### UUID Forwarding Requirement

For multi-server to work correctly:
- Proxy must forward player UUIDs (BungeeCord legacy or Velocity modern forwarding)
- All servers must use same forwarding mode
- Without forwarding, players get different UUIDs per server -> completely separate data

---

## Error Recovery Strategies

### Strategy 1: Graceful Degradation

When database is unavailable:
```java
public PlayerProgress getProgressOrLoad(Player player) {
    PlayerProgress cached = cache.get(player.getUniqueId());
    if (cached != null) return cached;

    try {
        return loadPlayer(player).get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
        logger.warning("Database unavailable, using empty progress for " + player.getName());
        // Player can still play, just won't have saved progress
        PlayerProgress empty = new PlayerProgress(player.getUniqueId());
        empty.setDatabaseUnavailable(true);  // Flag for UI warning
        cache.put(player.getUniqueId(), empty);
        return empty;
    }
}
```

### Strategy 2: Periodic Backup Saves

Protect against crash-induced data loss:
```java
// Every 5 minutes, save all dirty cached data
scheduler.runTaskTimerAsync(() -> {
    for (PlayerProgress progress : cache.values()) {
        if (progress.isDirty()) {
            storage.savePlayer(progress);
            progress.setDirty(false);
        }
    }
}, 6000L, 6000L);  // 5 minutes in ticks
```

### Strategy 3: Database Integrity Checks

Periodic validation:
```java
// On startup, verify database integrity
try (Statement stmt = conn.createStatement()) {
    ResultSet rs = stmt.executeQuery("PRAGMA integrity_check");
    String result = rs.getString(1);
    if (!"ok".equals(result)) {
        logger.severe("DATABASE CORRUPTION DETECTED: " + result);
        // Consider: restore from backup, disable plugin
    }
}
```

---

## Audit Checklist for Current Codebase

Based on research, the Collections plugin should be audited for:

| Issue | Current State | Risk Level | Recommendation |
|-------|---------------|------------|----------------|
| Player quit save timing | Fire-and-forget | HIGH | Block with timeout |
| Exception handling | Logs warning only | MEDIUM | Log SEVERE for critical, add retry |
| SQLite WAL mode | Not configured | MEDIUM | Enable WAL and busy_timeout |
| Transaction atomicity | Auto-commit per statement | MEDIUM | Wrap player save in transaction |
| Multi-server support | None (SQLite) | HIGH for networks | Requires MySQL + sync solution |
| Retry mechanism | None | MEDIUM | Add retry queue for transient failures |
| Periodic autosave | None | LOW | Consider for crash protection |
| Database validation | None | LOW | Add integrity check on startup |

---

## Sources

- [PaperMC Persistent Data Container docs](https://docs.papermc.io/paper/dev/pdc/)
- [PaperMC Using Databases docs](https://docs.papermc.io/paper/dev/using-databases/)
- [Baeldung: Working with Exceptions in CompletableFuture](https://www.baeldung.com/java-exceptions-completablefuture)
- [Mincong Huang: Exception Handling in CompletableFuture](https://mincong.io/2020/05/30/exception-handling-in-completable-future/)
- [SQLite WAL Mode documentation](https://sqlite.org/wal.html)
- [SQLite Busy Timeout documentation](https://www.sqlite.org/c3ref/busy_timeout.html)
- [SQLite Concurrent Writes](https://fractaledmind.com/2023/10/13/sqlite-myths-concurrent-writes-can-corrupt-the-database/)
- [HuskSync - Cross-server data synchronization](https://github.com/WiIIiam278/HuskSync)
- [PlayerDataSync on SpigotMC](https://www.spigotmc.org/resources/playerdatasync-1-20-4-1-21-11-cross-server-data-synchronization.123166/)
- [Waiting for Bukkit async tasks in onDisable](https://gist.github.com/blablubbabc/e884c114484f34cae316c48290b21d8e)
- [PaperMC Velocity Player Information Forwarding](https://docs.papermc.io/velocity/player-information-forwarding/)
- [HikariCP SQLite discussions](https://github.com/brettwooldridge/HikariCP/issues/393)
