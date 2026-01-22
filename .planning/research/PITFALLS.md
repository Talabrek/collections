# Pitfalls Research: v1.1 Operational Features

**Domain:** Adding export/import, notifications, admin commands, and metrics to existing Paper plugin
**Researched:** 2026-01-22
**Confidence:** HIGH (verified against existing codebase analysis, community patterns, established pitfalls)

---

## Summary

Adding operational features to an existing plugin with multi-server MySQL support introduces integration pitfalls beyond typical greenfield concerns. The key risks are: (1) export operations causing OutOfMemory on large datasets, (2) notification spam overwhelming players during batch operations, (3) admin commands on offline players racing with in-flight operations, and (4) metrics with high cardinality causing memory pressure. The existing async/CompletableFuture patterns must be extended carefully to avoid cache coherency issues in multi-server deployments.

---

## Export/Import Pitfalls

### Export Memory Exhaustion

**Risk:** HIGH
**What goes wrong:** Exporting all player data loads entire result set into memory before serialization. With thousands of players and collections, this causes `OutOfMemoryError: Java heap space`.

**Warning signs:**
- Export command hangs, then server restarts
- Heap usage spikes to 100% during export
- Export works for small servers, fails on production

**Prevention:**
1. Use streaming export with JDBC cursor (`setFetchSize(100)`) to avoid loading all rows
2. Write JSON incrementally using Jackson's `JsonGenerator.writeStartArray()` + `flush()` per batch
3. Add row count estimation and warn before large exports
4. Implement chunked export: `/collections export --limit 1000 --offset 0`

**Phase:** Export/Import implementation - critical design decision upfront

### Import Data Validation Bypass

**Risk:** HIGH
**What goes wrong:** Importing malformed or tampered export files inserts invalid data. Collection IDs reference non-existent collections, UUIDs are malformed, timestamps are in the future.

**Warning signs:**
- NullPointerExceptions when accessing imported progress
- Players have "completed" collections that don't exist
- Database constraint violations after import

**Prevention:**
1. Validate every field against current collection definitions
2. Skip unknown collection/item IDs with warning log, don't insert
3. Require `--force` flag to import data for collections not currently loaded
4. Validate UUID format before database insert
5. Schema version in export file; reject incompatible versions

**Phase:** Export/Import implementation - validation layer required

### Import Race with Active Players

**Risk:** MEDIUM
**What goes wrong:** Admin imports data for a player who is currently online. Import writes to database, but in-memory cache has stale data. Player's session overwrites imported data on quit.

**Warning signs:**
- Imported progress "disappears" after player logs out
- Player has progress that doesn't match export file
- Works in single-player testing, fails with active players

**Prevention:**
1. Check `PlayerDataManager.isLoaded(uuid)` before import
2. Either: (a) kick player, import, let them rejoin, or (b) update cache AND database atomically
3. For multi-server: broadcast cache invalidation via Redis pub/sub or plugin messaging channel
4. Add `--kick-online` flag or refuse import for online players

**Phase:** Export/Import implementation - integration with PlayerDataManager

### Multi-Server Export Inconsistency

**Risk:** MEDIUM
**What goes wrong:** On multi-server network, export from Server A while player is actively playing on Server B. Export captures stale data from database because Server B's cache hasn't synced yet.

**Warning signs:**
- Exported data is missing recent collections
- Export timestamp shows data is old
- Different exports from different servers produce different results

**Prevention:**
1. Flush all pending saves before export: `playerDataManager.saveAll().join()`
2. For multi-server: plugin messaging to request save-all on all servers before export
3. Add `last_save_timestamp` column, show warning if data is old
4. Export only from "primary" server or during maintenance window

**Phase:** Export/Import implementation - multi-server coordination

### Export Format Lock-In

**Risk:** LOW
**What goes wrong:** Export format uses Java serialization or plugin-specific binary format. Format becomes unreadable after plugin updates. No migration path.

**Warning signs:**
- Old exports fail to import after version upgrade
- Can't inspect export file contents manually
- No way to migrate to different plugin

**Prevention:**
1. Use human-readable JSON with explicit schema version
2. Include metadata: plugin version, export timestamp, server identifier
3. Document format in EXPORT_FORMAT.md
4. Add format version migration during import

**Phase:** Export/Import design

---

## Notification Pitfalls

### Chat Spam During Batch Collection

**Risk:** HIGH
**What goes wrong:** Admin uses `/collections complete <player> <collection>` which adds all items. Each item triggers "You collected X! (1/5)... (2/5)..." notification. Player receives 20+ messages instantly.

**Warning signs:**
- Chat scrolls uncontrollably during admin operations
- Players complain about notification spam
- Server console flooded with message sends

**Prevention:**
1. Add `suppressNotifications` parameter to `PlayerDataManager.addItem()`
2. Batch operations use single summary notification: "Collection X completed! (5 items added)"
3. Admin commands default to silent mode for target player
4. Rate-limit notifications per player: max 1 per 500ms, queue the rest

**Phase:** Notification system - must integrate with existing addItem flow

### Action Bar Flicker

**Risk:** MEDIUM
**What goes wrong:** Progress notification uses action bar. Multiple systems (existing ActionBarPromptTask, new progress notifications) compete for action bar space. Result flickers between "Right-click to collect" and "2/5 collected".

**Warning signs:**
- Action bar text changes rapidly
- Players can't read either message
- Existing prompt system appears broken

**Prevention:**
1. Establish action bar priority system: prompt > progress > other
2. Use single ActionBarManager with priority queue
3. Set minimum display duration (2 seconds) before allowing override
4. Consider: progress in title/subtitle, keep action bar for prompts

**Phase:** Notification system - requires coordination with ActionBarPromptTask

### Notification When Player Offline

**Risk:** LOW
**What goes wrong:** Admin grants progress to offline player. Code calls `player.sendMessage()` on null Player object. NullPointerException or silent failure.

**Warning signs:**
- Console shows NullPointerException from notification code
- Admin doesn't get feedback that notification wasn't delivered
- Works for online players, silent fail for offline

**Prevention:**
1. Check `Bukkit.getPlayer(uuid) != null` before sending
2. Queue notifications for delivery on next login
3. Or: simply don't notify for offline progress grants (document this behavior)
4. Return feedback to admin: "Progress granted. Player is offline, will not be notified."

**Phase:** Notification system - offline handling

### Progress Count Desync

**Risk:** MEDIUM
**What goes wrong:** Notification shows "3/5 collected" but player only has 2 items. Race condition: notification reads count before database write completes, or cache is stale.

**Warning signs:**
- Progress notification doesn't match GUI
- Number goes backwards (4/5 then 3/5)
- Intermittent, hard to reproduce

**Prevention:**
1. Read count from same source that just updated (in-memory cache after mutation)
2. Pass counts as parameters to notification method, don't re-query
3. For completion detection: use return value from `addItem()` that includes new count
4. Add `CollectionProgressEvent` with before/after counts

**Phase:** Notification system - tight integration with data mutation

---

## Admin Command Pitfalls

### Force-Complete Without Confirmation

**Risk:** HIGH
**What goes wrong:** Admin typo: `/collections complete SomePlayer all` intending to complete one collection, but "all" is parsed as collection ID (or a future flag). Or admin runs command on wrong player.

**Warning signs:**
- Player has all collections completed unexpectedly
- Admin can't undo the action
- Support tickets about "I didn't earn these legitimately"

**Prevention:**
1. Add confirmation step: "This will complete 15 items. Type `/collections confirm` within 10 seconds."
2. Log all admin operations with timestamp, admin name, target player, action details
3. Add `--confirm` flag for destructive operations: `/collections complete --confirm player collection`
4. Implement `/collections undo <player>` using backup-before-modify pattern

**Phase:** Admin commands - confirmation system

### Offline Player UUID Resolution

**Risk:** MEDIUM
**What goes wrong:** Admin uses `/collections complete "Player Name"` for offline player. Plugin can't resolve username to UUID because player has never joined this server, or name changed.

**Warning signs:**
- "Player not found" for known player
- Works for online players, fails for offline
- Different behavior on multi-server (player joined other server, not this one)

**Prevention:**
1. Use Mojang API for username->UUID resolution (with caching)
2. Accept both username AND UUID as input
3. Store username in database alongside UUID for reverse lookup
4. For multi-server: query shared database for player records

**Phase:** Admin commands - player resolution utility

### Admin Operation During Server Shutdown

**Risk:** MEDIUM
**What goes wrong:** Admin starts large operation (reset all players, export) during planned shutdown. Server shuts down mid-operation. Data in inconsistent state.

**Warning signs:**
- Partial resets: some players reset, others not
- Export file truncated
- Database in invalid state after restart

**Prevention:**
1. Long-running operations set a "busy" flag that blocks shutdown
2. `onDisable()` waits for in-progress operations with timeout
3. Use database transactions: operation is atomic or rolled back
4. Add operation queue with persistence; resume on restart

**Phase:** Admin commands - operation lifecycle management

### Permission Bypass in Suggestion

**Risk:** LOW
**What goes wrong:** Tab completion for `/collections reset <player>` suggests all players, even for admins who only have permission to reset specific players. Information leak.

**Warning signs:**
- Tab completion shows players admin shouldn't know about
- Exposes hidden staff alt accounts
- Violates privacy expectations

**Prevention:**
1. Filter suggestions based on command executor's permissions
2. For player suggestions: only show online players, or players in same group
3. Add `collections.admin.reset.all` vs `collections.admin.reset.self` granular permissions

**Phase:** Admin commands - permission-aware suggestions

### Concurrent Admin Operations

**Risk:** MEDIUM
**What goes wrong:** Two admins run operations on same player simultaneously. `/collections reset player` starts, then `/collections complete player collection` runs. Final state is undefined.

**Warning signs:**
- Operations produce unexpected results
- "It worked yesterday but not today"
- Logs show interleaved operations

**Prevention:**
1. Per-player operation lock (similar to existing `collectLocks` pattern)
2. Return "Operation in progress for this player, try again" error
3. Or: queue operations per-player, execute serially
4. Log concurrent operation attempts for audit

**Phase:** Admin commands - operation locking

---

## Metrics Pitfalls

### High Cardinality Tags

**Risk:** HIGH
**What goes wrong:** Metrics tagged with `player_uuid` or `collection_id` create millions of time series. Prometheus scrapes slow down, memory usage explodes, cardinality limits exceeded.

**Warning signs:**
- Prometheus OOM or "too many time series" errors
- Metrics endpoint response time > 10 seconds
- Grafana queries timeout

**Prevention:**
1. NEVER tag by player UUID or per-item identifiers
2. Safe tags: `collection_tier` (4 values), `server_id` (handful), `operation_type` (bounded)
3. Aggregate player-level metrics: `total_players_completed` counter, not per-player gauge
4. Use histograms for latency, not individual timing gauges
5. Cardinality budget: max 1000 unique label combinations per metric

**Phase:** Metrics design - critical constraint

### Metrics Overhead in Hot Path

**Risk:** MEDIUM
**What goes wrong:** Metrics collection added to `CollectibleInteractListener` or particle rendering. Synchronous metric recording adds latency to every interaction, causing TPS drops.

**Warning signs:**
- TPS drops after enabling metrics
- `Timer.record()` appears in spark profiler hot paths
- Metric endpoint scrape causes lag spikes

**Prevention:**
1. Use Micrometer's async-safe primitives (`Counter.increment()` is lock-free)
2. Avoid `Timer.record(() -> { ... })` in hot paths; use `Timer.Sample` instead
3. Pre-register metrics at startup, not lazily in hot path
4. Profile with and without metrics; budget max 1% overhead

**Phase:** Metrics implementation - performance validation

### bStats vs Custom Metrics Conflict

**Risk:** LOW
**What goes wrong:** Plugin uses bStats for anonymous usage stats AND custom Prometheus metrics. Two systems fight for same data, or duplicate collection causes confusion.

**Warning signs:**
- Same metric reported differently in bStats vs Prometheus
- "Which dashboard is correct?"
- Maintenance burden of two systems

**Prevention:**
1. Clear separation: bStats for anonymous plugin usage (standard), Prometheus for operational metrics
2. Document which metrics come from which system
3. Consider: single internal metrics interface that feeds both
4. bStats: keep simple (player count, collection count); Prometheus: detailed operational

**Phase:** Metrics design - system boundaries

### Metrics Not Reset on Reload

**Risk:** LOW
**What goes wrong:** Plugin reload resets internal counters but not registered metrics. `collections_completed` keeps counting from pre-reload values. Metrics drift from reality.

**Warning signs:**
- Metrics show impossible values after reload
- Counters never reset even with fresh data
- Discrepancy between internal state and exposed metrics

**Prevention:**
1. Use gauges for current-state metrics (they re-poll)
2. For counters: accept that counters are cumulative (this is standard Prometheus pattern)
3. Or: register new metrics instance on reload with different name (`collections_v2_completed`)
4. Document expected behavior in runbook

**Phase:** Metrics implementation - lifecycle handling

### Missing Metrics During Startup

**Risk:** LOW
**What goes wrong:** Prometheus scrapes during server startup before metrics are registered. Returns empty or partial response. Alerting triggers false positives.

**Warning signs:**
- Alerts fire during every server restart
- Gaps in Grafana graphs during deployment
- "No data" periods in metrics

**Prevention:**
1. Register all metrics in `onLoad()` or early `onEnable()` with initial values
2. Add `server_ready` gauge: 0 during startup, 1 when fully initialized
3. Configure alerts to account for startup window
4. Health check endpoint separate from metrics endpoint

**Phase:** Metrics implementation - initialization order

---

## Integration with Existing System

### Cache Coherency on Import

**Risk:** HIGH
**What goes wrong:** Import writes directly to database. PlayerDataManager cache is stale. Player's next action uses cached (old) data, overwrites imported data on next save.

**Warning signs:**
- Imported data "disappears"
- Works if player logs out and back in
- Multi-server makes it worse (cache on multiple servers)

**Prevention:**
1. Import must invalidate/update `PlayerDataManager.cache` for affected players
2. For offline players: no cache entry exists, import is safe
3. For online players: either kick, or `cache.remove(uuid)` forcing reload
4. Multi-server: broadcast invalidation message

**Phase:** Export/Import - PlayerDataManager integration

### Notification Conflicts with Existing Messages

**Risk:** MEDIUM
**What goes wrong:** New progress notifications duplicate or conflict with existing `configManager.getMessage("item-collected")` in `CollectibleInteractListener`. Player sees two messages for one action.

**Warning signs:**
- Duplicate chat messages
- Inconsistent message formatting
- One message from config, one hardcoded

**Prevention:**
1. Audit all existing message sends in `CollectibleInteractListener`, `ItemUseListener`
2. Decide: enhance existing messages OR add separate progress messages
3. Use single notification service that existing code calls into
4. Config option to enable/disable progress notifications

**Phase:** Notification system - audit existing messages first

### Metrics Exposure of Internal State

**Risk:** LOW
**What goes wrong:** Metrics expose cache sizes, queue lengths, or internal implementation details. Future refactoring breaks dashboards. Or: metrics reveal operational details competitors shouldn't see.

**Warning signs:**
- Dashboard breaks after internal refactor
- Metrics endpoint reveals player count, activity patterns
- Security review flags metrics endpoint

**Prevention:**
1. Expose semantic metrics (collections completed) not implementation (cache.size())
2. If exposing internal metrics, document they may change
3. Consider authentication on metrics endpoint for sensitive deployments
4. Separate "public" (bStats) from "operational" (Prometheus) metrics

**Phase:** Metrics design - API stability

---

## Confidence Assessment

| Area | Confidence | Rationale |
|------|------------|-----------|
| Export/Import | HIGH | Based on established Java streaming patterns, codebase analysis of existing async flows |
| Notifications | HIGH | Based on existing codebase patterns (ActionBarPromptTask, ConfigManager messages) |
| Admin Commands | HIGH | Based on existing command patterns, established Minecraft plugin practices |
| Metrics | MEDIUM | Prometheus/Micrometer patterns verified, Minecraft-specific concerns from community |
| Integration | HIGH | Direct analysis of existing PlayerDataManager, Storage, and listener code |

---

## Sources

### Codebase Analysis
- `PlayerDataManager.java` - cache patterns, async save flows
- `MySQLStorage.java` - multi-server deployment, existing transaction patterns
- `CollectibleInteractListener.java` - existing notification points
- `CollectionsCommand.java` - existing admin command patterns
- `ActionBarPromptTask.java` - existing action bar usage

### External Sources
- [HuskSync - Cross-server data synchronization](https://github.com/WiIIiam278/HuskSync) - multi-server sync patterns
- [Cache Synchronization Pitfalls](https://javanexus.com/blog/mastering-cache-synchronization-pitfalls) - stale cache, invalidation strategies
- [Fixing JSON OOM with Streaming](https://blog.jakubholy.net/fixing-json-oom-with-streaming-and-mapdb/) - streaming export patterns
- [Java OutOfMemoryError Serialization](https://www.javathinking.com/blog/how-to-handle-outofmemoryerror-in-java/) - batch fetching, streaming
- [UnifiedMetrics](https://github.com/Cubxity/UnifiedMetrics) - Minecraft Prometheus patterns
- [Micrometer Best Practices 2025](https://www.josedacruz.com/2025/06/08/mastering-micrometer-in-spring-boot-metrics-prometheus-observability-explained/) - cardinality, performance
- [OfflineManager Plugin](https://dev.bukkit.org/projects/offlinemanager) - offline player data patterns
- [ChatControl](https://modrinth.com/plugin/chatcontrol) - rate limiting, spam prevention patterns
