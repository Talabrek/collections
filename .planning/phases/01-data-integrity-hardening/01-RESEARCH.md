# Phase 1: Data Integrity Hardening - Research

**Researched:** 2026-01-21
**Domain:** Player data persistence, SQLite configuration, async operation safety
**Confidence:** HIGH

## Summary

This research analyzes the Collections plugin's current data persistence implementation against the four requirements (DATA-01 through DATA-04). The codebase has a solid foundation with `ConcurrentHashMap` caching, `CompletableFuture` async operations, and HikariCP connection pooling. However, several critical gaps exist that could cause data loss.

**Key findings:**
1. Player quit saves are fire-and-forget (DATA-01 violation)
2. SQLite has no WAL mode or busy_timeout configuration (DATA-02 violation)
3. Player saves execute multiple statements without transaction wrapping (DATA-03 violation)
4. Exception handlers log at WARNING level instead of SEVERE for critical operations (DATA-04 violation)

**Primary recommendation:** Implement blocking saves on player quit with timeout, enable SQLite WAL mode, wrap multi-statement operations in transactions, and upgrade critical exception logging to SEVERE.

---

## Current State Analysis

### SQLiteStorage.java

**What it does:**
- Uses HikariCP for connection pooling (10 max connections)
- Creates tables on initialization for players, collection_progress, collected_items, active_collectibles
- All database operations return `CompletableFuture` for async execution
- Uses `INSERT OR REPLACE` and `INSERT OR IGNORE` for upserts
- Has 30-second timeouts via `orTimeout()` on most operations

**Issues found:**

| Line | Issue | Severity |
|------|-------|----------|
| 65-78 | No WAL mode or busy_timeout PRAGMA configuration | HIGH |
| 280-336 | `savePlayer()` executes 3+ separate statements without transaction | HIGH |
| 329-331 | Exception logged at WARNING, should be SEVERE for player data loss | MEDIUM |
| 339-356 | `saveCollectedItem()` has no exception handler at all | HIGH |
| 359-376 | `updateCollectionStatus()` has no exception handler at all | HIGH |

**Code pattern issue in savePlayer():**
```java
// Current: Each statement auto-commits separately
try (PreparedStatement stmt = conn.prepareStatement(...)) {
    stmt.executeUpdate();  // Auto-commits
}
// If next statement fails, previous one is already committed
// Partial save state is possible
```

### PlayerDataManager.java

**What it does:**
- Caches `PlayerProgress` objects in `ConcurrentHashMap`
- Tracks pending loads to prevent duplicate concurrent loads
- Uses `computeIfAbsent()` for atomic load-once guarantee
- Provides `saveAndUnload()` for player quit
- Has `saveAll()` with 60-second timeout for shutdown

**Issues found:**

| Line | Issue | Severity |
|------|-------|----------|
| 108-121 | `savePlayer()` logs SEVERE on failure - correct | OK |
| 129-135 | `saveAndUnload()` removes from cache AFTER save starts, but doesn't wait for save to complete | HIGH |
| 154-160 | `addItem()` exception handler swallows failure without SEVERE log | MEDIUM |
| 181-188 | `markComplete()` exception handler at WARNING level | MEDIUM |
| 206-213 | `claimReward()` exception handler at WARNING level | MEDIUM |

**Critical issue in saveAndUnload():**
```java
public CompletableFuture<Void> saveAndUnload(UUID playerId) {
    return savePlayer(playerId)
            .thenRun(() -> {
                cache.remove(playerId);  // Runs AFTER save...
                pendingLoads.remove(playerId);
            });
}
// BUT: The caller doesn't wait for this future to complete!
```

### PlayerListener.java

**What it does:**
- Loads player data asynchronously on join
- Saves and unloads player data on quit
- Cleans up cooldown tracking on quit

**Issues found:**

| Line | Issue | Severity |
|------|-------|----------|
| 67-85 | `onPlayerQuit()` fires `saveAndUnload()` but doesn't wait for completion | CRITICAL |
| 30 | Uses `EventPriority.NORMAL` - should be HIGH/HIGHEST for quit save | LOW |

**Critical issue:**
```java
@EventHandler(priority = EventPriority.NORMAL)
public void onPlayerQuit(PlayerQuitEvent event) {
    // ...
    playerDataManager.saveAndUnload(playerId)
            .thenRun(() -> { /* just logging */ });  // Fire and forget!
}
```

If save takes 2 seconds and player rejoins in 1 second:
1. Old save still running
2. New load starts
3. New load completes with stale/missing data
4. Old save completes (data lost)

### Collections.java (Main Plugin)

**What it does:**
- Initializes all managers in correct dependency order
- Calls `playerDataManager.saveAll().get()` in onDisable() - BLOCKING wait is correct
- Shuts down storage AFTER saves complete - correct

**Issues found:**

| Line | Issue | Severity |
|------|-------|----------|
| 170-176 | onDisable() correctly blocks on saveAll() | OK |
| 173 | Exception handler only calls `e.getMessage()`, loses stack trace | LOW |

---

## Implementation Approach by Requirement

### DATA-01: Player quit saves must block with timeout

**Problem:** Current implementation is fire-and-forget. Save may not complete before player entity is cleaned up or player rejoins.

**Solution:** Block the event handler with a reasonable timeout.

**Pattern to use:**
```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onPlayerQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();

    // Cleanup first (non-critical)
    CollectibleInteractListener interactListener = plugin.getCollectibleInteractListener();
    if (interactListener != null) {
        interactListener.cleanupPlayer(playerId);
    }

    // Block until save completes with timeout
    try {
        playerDataManager.saveAndUnload(playerId)
            .get(5, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        plugin.getLogger().log(Level.SEVERE,
            "Save timed out for " + playerId + " - data may be lost");
    } catch (Exception e) {
        plugin.getLogger().log(Level.SEVERE,
            "Save failed for " + playerId, e);
    }

    if (plugin.getConfigManager().isDebugMode()) {
        plugin.getLogger().info("Saved and unloaded data for " + event.getPlayer().getName());
    }
}
```

**Files to modify:**
- `PlayerListener.java` (lines 67-85)

**Edge cases:**
- Many simultaneous quits (server shutdown): handled by main plugin's `saveAll()` which already blocks
- 5-second timeout may cause slight lag on individual quits - acceptable tradeoff for data safety
- If timeout expires, data may still save eventually - just not guaranteed

**Verification:** After implementation, add test that:
1. Player quits
2. Verify save completed before method returns
3. Player rejoins immediately
4. Verify data is present

### DATA-02: SQLite WAL mode and busy_timeout

**Problem:** SQLite in default journal mode can get `SQLITE_BUSY` errors under concurrent access. HikariCP with 10 connections can easily trigger this.

**Solution:** Enable WAL mode and configure busy_timeout in `initialize()`.

**Pattern to use:**
```java
public void initialize() {
    // ... existing HikariConfig setup ...

    dataSource = new HikariDataSource(config);

    // Configure SQLite for concurrent access
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {

        // WAL mode allows concurrent reads during writes
        stmt.execute("PRAGMA journal_mode=WAL");

        // Wait up to 30 seconds if database is locked
        stmt.execute("PRAGMA busy_timeout=30000");

        // Balance between safety and performance
        stmt.execute("PRAGMA synchronous=NORMAL");

        // Verify settings applied
        ResultSet rs = stmt.executeQuery("PRAGMA journal_mode");
        if (rs.next()) {
            String mode = rs.getString(1);
            if (!"wal".equalsIgnoreCase(mode)) {
                plugin.getLogger().warning("Failed to enable WAL mode, got: " + mode);
            }
        }

    } catch (SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "Failed to configure SQLite pragmas", e);
    }

    createTables();
    plugin.getLogger().info("SQLite storage initialized with WAL mode");
}
```

**Files to modify:**
- `SQLiteStorage.java` (after line 78, in `initialize()`)

**Edge cases:**
- WAL mode creates `.db-wal` and `.db-shm` files alongside `.db` file
- WAL files on network drives can cause corruption (not applicable - local file)
- Checkpoint happens automatically but can be forced if needed

**Verification:**
1. Check that `PRAGMA journal_mode` returns "wal"
2. Under load test, verify no `SQLITE_BUSY` errors

### DATA-03: Transaction wrapping for player saves

**Problem:** `savePlayer()` executes multiple statements that auto-commit individually. If one fails, player data is in inconsistent state.

**Solution:** Wrap all related statements in a single transaction with rollback on failure.

**Pattern to use:**
```java
@Override
public CompletableFuture<Void> savePlayer(PlayerProgress progress) {
    return CompletableFuture.runAsync(() -> {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Upsert player base data
                savePlayerBase(conn, progress);

                // Save each collection progress
                for (var entry : progress.getAllProgress().entrySet()) {
                    saveCollectionProgress(conn, progress.getPlayerId(), entry.getValue());
                    saveCollectedItems(conn, progress.getPlayerId(), entry.getValue());
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                "Failed to save player data: " + progress.getPlayerId(), e);
            throw new RuntimeException(e);  // Propagate for CompletableFuture
        }
    }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
        plugin.getLogger().log(Level.SEVERE,
            "Timeout or error saving player data: " + progress.getPlayerId(), throwable);
        return null;
    });
}

private void savePlayerBase(Connection conn, PlayerProgress progress) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT OR REPLACE INTO players
            (uuid, total_collectibles_collected, total_collections_completed,
             first_collection_date, last_activity_date)
            VALUES (?, ?, ?, ?, ?)
            """)) {
        stmt.setString(1, progress.getPlayerId().toString());
        stmt.setInt(2, progress.getTotalCollectiblesCollected());
        stmt.setInt(3, progress.getTotalCollectionsCompleted());
        stmt.setLong(4, progress.getFirstCollectionDate());
        stmt.setLong(5, progress.getLastActivityDate());
        stmt.executeUpdate();
    }
}

private void saveCollectionProgress(Connection conn, UUID playerId,
        PlayerProgress.CollectionProgress colProgress) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement("""
            INSERT OR REPLACE INTO collection_progress
            (uuid, collection_id, reward_claimed, completed_date)
            VALUES (?, ?, ?, ?)
            """)) {
        stmt.setString(1, playerId.toString());
        stmt.setString(2, colProgress.getCollectionId());
        stmt.setBoolean(3, colProgress.isRewardClaimed());
        stmt.setLong(4, colProgress.getCompletedDate());
        stmt.executeUpdate();
    }
}

private void saveCollectedItems(Connection conn, UUID playerId,
        PlayerProgress.CollectionProgress colProgress) throws SQLException {
    for (String itemId : colProgress.getCollectedItems()) {
        try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT OR IGNORE INTO collected_items
                (uuid, collection_id, item_id, collected_date)
                VALUES (?, ?, ?, ?)
                """)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, colProgress.getCollectionId());
            stmt.setString(3, itemId);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }
}
```

**Files to modify:**
- `SQLiteStorage.java` (lines 280-336, refactor `savePlayer()`)

**Edge cases:**
- Large player with many collections: transaction may be large but SQLite handles this
- Connection timeout during transaction: automatic rollback by JDBC
- `INSERT OR IGNORE` still works correctly within transaction

**Verification:**
1. Introduce artificial failure mid-save
2. Verify database is not in partial state
3. Verify subsequent save succeeds completely

### DATA-04: CompletableFuture exception handlers at SEVERE level

**Problem:** Several `CompletableFuture` chains either have no exception handler or log at WARNING level for operations where data loss is possible.

**Solution:** Audit all `exceptionally()` handlers and upgrade critical ones to SEVERE.

**Operations requiring SEVERE logging:**
- `savePlayer()` - player data loss
- `saveCollectedItem()` - collected item loss
- `updateCollectionStatus()` - completion status loss
- `saveAndUnload()` - player quit data loss

**Operations acceptable at WARNING:**
- `loadPlayer()` - can return default progress
- `resetPlayer()` - admin operation, not data loss
- Statistics queries - informational only

**Files to modify:**
- `SQLiteStorage.java`:
  - Line 329-331: Already logs SEVERE in `exceptionally()` but also WARNING earlier - consolidate
  - Lines 339-356: `saveCollectedItem()` - ADD exception handler
  - Lines 359-376: `updateCollectionStatus()` - ADD exception handler
- `PlayerDataManager.java`:
  - Lines 154-160: `addItem()` - upgrade to SEVERE
  - Lines 181-188: `markComplete()` - upgrade to SEVERE
  - Lines 206-213: `claimReward()` - upgrade to SEVERE

**Pattern for missing handlers:**
```java
// saveCollectedItem - add exception handler
public CompletableFuture<Void> saveCollectedItem(UUID playerId, String collectionId, String itemId) {
    return CompletableFuture.runAsync(() -> {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT OR IGNORE INTO collected_items
                     (uuid, collection_id, item_id, collected_date)
                     VALUES (?, ?, ?, ?)
                     """)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, collectionId);
            stmt.setString(3, itemId);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);  // Propagate for CompletableFuture
        }
    }).orTimeout(30, TimeUnit.SECONDS).exceptionally(throwable -> {
        plugin.getLogger().log(Level.SEVERE,
            "CRITICAL: Failed to save collected item " + itemId +
            " for collection " + collectionId + " for player " + playerId, throwable);
        return null;
    });
}
```

---

## Code Patterns Reference

### Pattern: Blocking Save on Quit
```java
try {
    future.get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    logger.log(Level.SEVERE, "Save timed out for " + playerId);
} catch (Exception e) {
    logger.log(Level.SEVERE, "Save failed for " + playerId, e);
}
```

### Pattern: SQLite PRAGMA Configuration
```java
try (Connection conn = dataSource.getConnection();
     Statement stmt = conn.createStatement()) {
    stmt.execute("PRAGMA journal_mode=WAL");
    stmt.execute("PRAGMA busy_timeout=30000");
    stmt.execute("PRAGMA synchronous=NORMAL");
}
```

### Pattern: Transaction with Rollback
```java
conn.setAutoCommit(false);
try {
    // Multiple operations
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```

### Pattern: CompletableFuture Exception Handler (SEVERE)
```java
.exceptionally(throwable -> {
    logger.log(Level.SEVERE, "CRITICAL: Operation failed - " + context, throwable);
    return null;  // or default value
});
```

---

## Risk Areas and Edge Cases

### High Risk

1. **Rapid quit-rejoin cycle:** Player quits, immediately rejoins. Old save racing with new load.
   - Mitigation: Blocking quit save ensures save completes before rejoin can load.

2. **Server crash during save:** Transaction uncommitted, data lost.
   - Mitigation: WAL mode with `synchronous=NORMAL` provides good crash recovery. Full protection requires `synchronous=FULL` (slower).

3. **Database corruption:** SQLite file damaged.
   - Mitigation: Consider adding `PRAGMA integrity_check` on startup (not in scope for this phase).

### Medium Risk

1. **Many simultaneous quits (server shutdown):** Multiple blocking saves on main thread.
   - Mitigation: `onDisable()` already handles this with `saveAll()`. Individual quit events shouldn't happen during shutdown.

2. **Long transaction blocking readers:** Large player save locks database.
   - Mitigation: WAL mode allows concurrent readers during writes. Individual saves are fast.

3. **HikariCP pool exhaustion:** All 10 connections busy.
   - Mitigation: 30-second timeout on operations, busy_timeout on SQLite. Consider increasing pool size if issue appears.

### Low Risk

1. **WAL file growth:** Large WAL file if checkpoint delayed.
   - Mitigation: SQLite auto-checkpoints at 1000 pages (~4MB). Can force with `PRAGMA wal_checkpoint`.

2. **5-second quit timeout causing lag:** Noticeable delay for quitting player.
   - Mitigation: Saves should complete in <100ms normally. 5 seconds is safety margin for database issues.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Connection pooling | Custom pool | HikariCP (already using) |
| Async database ops | Raw threads | CompletableFuture (already using) |
| Thread-safe cache | synchronized blocks | ConcurrentHashMap (already using) |
| SQLite concurrency | Custom locking | WAL mode + busy_timeout |
| Transaction management | Manual tracking | JDBC setAutoCommit/commit/rollback |

---

## Testing Recommendations

### Unit Tests (MockBukkit)
1. `saveAndUnload()` completes before returning
2. Transaction rollback on partial failure
3. Exception handlers log at correct levels

### Integration Tests
1. WAL mode enabled after initialization
2. Concurrent saves don't cause SQLITE_BUSY
3. Quit-rejoin cycle preserves data

### Manual Tests
1. Quit player during collection (verify save)
2. Force-kill server during save (verify recovery)
3. 10+ simultaneous quits (verify no deadlock)

---

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `SQLiteStorage.java`, `PlayerDataManager.java`, `PlayerListener.java`, `Collections.java`
- Prior research: `.planning/research/DATA_INTEGRITY.md` (internal)
- Prior research: `.planning/research/CONCURRENCY.md` (internal)

### Secondary (MEDIUM confidence)
- SQLite WAL documentation: https://sqlite.org/wal.html
- SQLite PRAGMA documentation: https://www.sqlite.org/pragma.html
- HikariCP SQLite configuration: https://github.com/brettwooldridge/HikariCP/issues/393

---

## Metadata

**Confidence breakdown:**
- Current state analysis: HIGH - direct code examination
- Implementation patterns: HIGH - verified against prior research and official docs
- Edge cases: MEDIUM - based on experience, not runtime testing

**Research date:** 2026-01-21
**Valid until:** 60 days (stable SQLite/JDBC patterns)
