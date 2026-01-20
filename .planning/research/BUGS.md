# Common Paper Plugin Bugs

**Domain:** Paper/Bukkit Plugin Development
**Researched:** 2026-01-21
**Focus:** EQ2-style collectibles plugin with spawning, GUIs, persistence, particles
**Confidence:** HIGH (verified with official docs and community sources)

---

## Thread Safety

### Async Bukkit API Access

- **Symptoms:** `IllegalStateException: Asynchronous entity/world access`, server crashes, corrupted data, random NPEs
- **Root cause:** Calling Bukkit API methods from async threads (database callbacks, CompletableFuture chains). Large portions of the Bukkit API are not thread-safe; methods that read/modify world state must run on the main thread.
- **Detection:** Search for `CompletableFuture`, `runTaskAsynchronously`, database calls followed by Bukkit API calls without `runTask()` wrapper. Paper's `paper.strict-thread-checks` system property logs all violations.
- **Fix:**
  - Always return to main thread before Bukkit API calls: `Bukkit.getScheduler().runTask(plugin, () -> { ... })`
  - Use Folia-compatible schedulers: `Bukkit.getRegionScheduler()`, `entity.getScheduler()`
  - Keep async code purely computational; sync code handles Bukkit state

**Sources:** [PaperMC Scheduling Docs](https://docs.papermc.io/paper/dev/scheduler/), [MultiPaper Threading Guide](https://multipaper.io/shreddedpaper/writing-a-multithreaded-plugin.html)

### ConcurrentModificationException in Event Handlers

- **Symptoms:** `ConcurrentModificationException` in console, event handler stops working, players stuck in weird states
- **Root cause:** Iterating over a collection while modifying it, often in event handlers that loop through players or entities. Also occurs when async tasks modify collections being iterated on main thread.
- **Detection:** Search for `for (... : collection)` where `collection.remove()` or `collection.add()` is called inside the loop. Check for shared collections between event handlers.
- **Fix:**
  - Use `Iterator.remove()` pattern for removal during iteration
  - Collect items to remove/add into separate list, apply after iteration
  - Use `CopyOnWriteArrayList` or `ConcurrentHashMap` for cross-thread access
  - Use `synchronized` blocks when multiple threads access same collection

**Sources:** [Bukkit Forums - ConcurrentModificationException](https://bukkit.org/threads/concurrentmodificationexception.402359/), [SpigotMC Forums](https://www.spigotmc.org/threads/solved-concurrentmodificationexception.17907/)

---

## Memory Leaks

### Player Reference Retention

- **Symptoms:** Memory usage grows over time, eventually OutOfMemoryError, server slows down after many player sessions
- **Root cause:** Storing `Player` objects in maps/lists instead of UUIDs. Player objects hold references to entire player state; if not cleaned up on quit, they cannot be garbage collected.
- **Detection:** Search for `Map<Player, ...>` or `List<Player>`. Check for `Map<UUID, ...>` without corresponding cleanup in `PlayerQuitEvent`.
- **Fix:**
  - Store `UUID` instead of `Player`: `Map<UUID, Data>`
  - Clean up in `PlayerQuitEvent` handler
  - Use weak references if caching: `WeakHashMap<UUID, Data>`

**Sources:** [SpigotMC - Memory Leak Prevention](https://www.spigotmc.org/threads/prevent-memory-leaks-in-your-resource.609546/), [Bukkit Forums](https://bukkit.org/threads/memory-leak-prevention.244364/)

### Uncancelled Scheduled Tasks

- **Symptoms:** Memory grows, duplicate task execution after reload, tasks running for disconnected players
- **Root cause:** `BukkitRunnable` or scheduled tasks not cancelled when no longer needed (player quit, plugin disable, feature disabled). Tasks hold references to plugin classes and any objects they reference.
- **Detection:** Search for `runTaskTimer`, `BukkitRunnable` without corresponding `cancel()` calls. Check `onDisable()` for `Bukkit.getScheduler().cancelTasks(this)`.
- **Fix:**
  - Track all tasks: `Map<UUID, BukkitTask> activeTasks`
  - Cancel in `PlayerQuitEvent`: `activeTasks.remove(uuid).cancel()`
  - Cancel all in `onDisable()`: `Bukkit.getScheduler().cancelTasks(this)`
  - Self-cancelling tasks should check validity: `if (!player.isOnline()) { cancel(); return; }`

**Sources:** [Bukkit Wiki - Scheduler Programming](https://bukkit.fandom.com/wiki/Scheduler_Programming), [BukkitRunnable Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/scheduler/BukkitRunnable.html)

### Per-Player Listener Registration

- **Symptoms:** Event handlers fire multiple times, memory grows with each player join
- **Root cause:** Creating and registering new `Listener` instances per player without unregistering them on quit. Listeners remain registered indefinitely.
- **Detection:** Search for `registerEvents` called outside of `onEnable()`. Check for listener registration in `PlayerJoinEvent`.
- **Fix:**
  - Prefer single listener that handles all players via internal map
  - If per-player listeners required, unregister in `PlayerQuitEvent`: `HandlerList.unregisterAll(listener)`
  - Track registered listeners for cleanup

**Sources:** [SpigotMC - Unregister Event Listener](https://www.spigotmc.org/threads/unregister-a-event-listener.143889/), [Bukkit Forums](https://bukkit.org/threads/unregistering-of-events.60817/)

---

## Race Conditions

### Player Join Data Loading Race

- **Symptoms:** NPE when accessing player data right after join, "data not found" for new players, inconsistent player state
- **Root cause:** Async database load not complete when other events/commands try to access player data. `PlayerJoinEvent` fires before async load finishes.
- **Detection:** Search for async database calls in `PlayerJoinEvent` followed by data access in other handlers without null checks. Check for missing `CompletableFuture` chaining.
- **Fix:**
  - Block or delay access until load complete: use `CompletableFuture` and chain dependent operations
  - Provide "loading" state with graceful degradation
  - Use `PlayerLoginEvent` (earlier) to start loading, data ready by `PlayerJoinEvent`
  - Queue actions until data ready: `pendingActions.computeIfAbsent(uuid, k -> new ArrayList<>()).add(action)`

**Sources:** [Bukkit Forums - Player Join Event](https://bukkit.org/threads/player-join-event.386566/)

### Chunk Load Entity Access Race

- **Symptoms:** `chunk.getEntities()` returns empty array for just-loaded chunks, entities "disappear" then reappear
- **Root cause:** Paper's async chunk loading means entities may not be immediately available after `ChunkLoadEvent`. Entity list is populated asynchronously.
- **Detection:** Search for `chunk.getEntities()` in `ChunkLoadEvent` handlers. Check for entity spawning immediately after chunk load.
- **Fix:**
  - Use delayed task after chunk load: `Bukkit.getScheduler().runTaskLater(plugin, () -> { ... }, 1L)`
  - Use Paper's `EntitiesLoadEvent` if available (fires when entities are actually loaded)
  - Don't assume entities exist immediately after chunk load

**Sources:** [Paper Issue #5872](https://github.com/PaperMC/Paper/issues/5872)

---

## Inventory/GUI Bugs

### Item Duplication via Click Events

- **Symptoms:** Players duplicate items in custom GUIs, items appear in both inventories, items vanish then reappear
- **Root cause:** Not cancelling all click actions (shift-click, number keys, double-click, drag). `InventoryClickEvent` modifications can be overwritten by vanilla handling.
- **Detection:** Check `InventoryClickEvent` handlers for: missing `event.setCancelled(true)`, missing handling of `ClickType.SHIFT_LEFT/RIGHT`, `HOTBAR_SWAP`, `DOUBLE_CLICK`, `DRAG`.
- **Fix:**
  - Cancel ALL click types in custom GUIs: `event.setCancelled(true)` as first line
  - Handle each `ClickType` explicitly if allowing some actions
  - Call `player.updateInventory()` after modifications (deprecated but still necessary)
  - Schedule inventory modifications for next tick: `Bukkit.getScheduler().runTask(plugin, () -> { ... })`
  - For `HOTBAR_SWAP`, send packet update on next tick

**Sources:** [Paper Issue #12006](https://github.com/PaperMC/Paper/issues/12006), [InventoryClickEvent Javadoc](https://jd.papermc.io/paper/1.16/org/bukkit/event/inventory/InventoryClickEvent.html)

### InventoryClickEvent Modification Overwrite

- **Symptoms:** Slot modifications don't persist, items revert to previous state, ghost items
- **Root cause:** Modifications made during `InventoryClickEvent` can be overwritten by vanilla handling. The event occurs *during* inventory modification, not after.
- **Detection:** Check for `inventory.setItem()` or `cursor.setAmount()` inside `InventoryClickEvent` without cancellation.
- **Fix:**
  - Cancel event first, then make all modifications manually
  - OR schedule modifications for next tick
  - Never modify slots that vanilla is also modifying unless cancelled

**Sources:** [InventoryClickEvent Javadoc](https://jd.papermc.io/paper/1.16/org/bukkit/event/inventory/InventoryClickEvent.html)

---

## Entity Management

### Entity Despawn Without Cleanup

- **Symptoms:** Data loss for custom entities, orphaned database entries, "ghost" collectibles that don't exist
- **Root cause:** No `EntityRemoveEvent` handling. Entities can despawn (chunk unload, world rules, player distance) without plugin notification.
- **Detection:** Check for entity tracking without `EntityRemoveEvent`/`ChunkUnloadEvent` handling. Search for entity UUIDs stored without cleanup mechanism.
- **Fix:**
  - Listen to `ChunkUnloadEvent` and save/cleanup entities in that chunk
  - Use `PersistentDataContainer` on entities for data that survives despawn/respawn
  - Track entities by chunk: `Map<Long, Set<UUID>> entitiesByChunk`
  - Recreate entities on `ChunkLoadEvent` from persistent storage

**Sources:** [Bukkit Forums - Entity Despawn](https://bukkit.org/threads/entity-despawn-event.104728/)

### Chunk Unload Without Entity Save

- **Symptoms:** Collectibles vanish when chunks unload, entities lost on server restart, data inconsistency
- **Root cause:** Not handling `ChunkUnloadEvent` to persist entity state. Relying solely on `onDisable()` misses runtime chunk unloads.
- **Detection:** Check for entity tracking without `ChunkUnloadEvent` handler. Verify entity recreation logic in `ChunkLoadEvent`.
- **Fix:**
  - Save entity state in `ChunkUnloadEvent` (cancelled=false)
  - Use chunk-based storage: entities saved per-chunk, loaded per-chunk
  - Consider Paper's plugin chunk tickets to prevent unwanted unloads

**Sources:** [Spigot Chunk Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Chunk.html), [PaperMC World Config](https://docs.papermc.io/paper/reference/world-configuration/)

---

## Database/Storage

### HikariCP Connection Pool Exhaustion

- **Symptoms:** `Connection is not available, request timed out after 30000ms`, database operations hang, server freezes on high load
- **Root cause:** Connection leaks (not closing connections/statements/result sets), slow queries holding connections, pool too small for load.
- **Detection:** Enable HikariCP leak detection: `leakDetectionThreshold: 60000`. Check for `try` without `finally` for connection cleanup. Search for missing `close()` calls.
- **Fix:**
  - Always use try-with-resources: `try (Connection conn = dataSource.getConnection(); PreparedStatement ps = ...) { }`
  - Set appropriate pool size (typically 2-5 for plugins)
  - Add query timeouts: `statement.setQueryTimeout(30)`
  - Enable leak detection in development
  - Optimize slow queries (add indexes, batch operations)

**Sources:** [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP), [HikariCP Issue #1111](https://github.com/brettwooldridge/HikariCP/issues/1111)

### CompletableFuture Exception Swallowing

- **Symptoms:** Database operations silently fail, player data not saved, no error in console, mysterious "data loss"
- **Root cause:** `CompletableFuture` chains without exception handling. Unhandled exceptions complete the future exceptionally but don't propagate unless explicitly handled.
- **Detection:** Search for `CompletableFuture.supplyAsync` or `thenApply` without `.exceptionally()` or `.handle()`. Check for missing `.join()` or `.get()` calls.
- **Fix:**
  - Always add exception handler: `.exceptionally(ex -> { plugin.getLogger().severe("DB error: " + ex); return fallback; })`
  - Use `.whenComplete((result, ex) -> { if (ex != null) log(ex); })` for logging without recovery
  - Don't use `.exceptionally()` followed by `.thenApply()` that expects non-null (exceptionally returns null by default)
  - Consider `.handle()` for unified result/error processing

**Sources:** [SpigotMC Wiki - CompletableFutures](https://www.spigotmc.org/wiki/multithreading-completable-futures/), [Baeldung - CompletableFuture Exceptions](https://www.baeldung.com/java-exceptions-completablefuture)

### onDisable Data Loss

- **Symptoms:** Data not saved on server stop, async save operations cut off, corrupt state after restart
- **Root cause:** `onDisable()` doesn't wait for async operations. Server continues shutdown while CompletableFutures are still running. Class loader may close before async tasks complete.
- **Detection:** Check `onDisable()` for async operations without blocking. Search for `CompletableFuture` without `.join()` in shutdown path.
- **Fix:**
  - Block on critical saves in `onDisable()`: `saveFuture.join()`
  - Set reasonable timeout: `saveFuture.get(10, TimeUnit.SECONDS)`
  - Cancel non-critical tasks, block on critical ones
  - Consider saving synchronously in `onDisable()` even if async elsewhere
  - Save incrementally during runtime, not just on shutdown

**Sources:** [Bukkit Forums - Delay on Disable](https://bukkit.org/threads/delay-on-disable.320241/), [Paper Issue #776](https://github.com/PaperMC/Paper/issues/776)

---

## Configuration

### Config Static Reference After Reload

- **Symptoms:** Config changes don't take effect after `/reload`, NPE accessing config values, stale configuration
- **Root cause:** Storing `getConfig()` result in static field. After `reloadConfig()`, the static reference points to old (now invalid) config object.
- **Detection:** Search for `static.*getConfig()` or `static FileConfiguration`. Check if `reloadConfig()` is called without reassigning cached config.
- **Fix:**
  - Never store config in static field
  - Re-fetch config after every `reloadConfig()`: `this.settings = getConfig().getConfigurationSection("settings")`
  - Use getter method that always calls `getConfig()`
  - Consider ConfigLib or similar for type-safe config management

**Sources:** [Bukkit Wiki - Configuration API](https://bukkit.fandom.com/wiki/Configuration_API_Reference), [Bukkit Forums - Reloading Config](https://bukkit.org/threads/reloading-config.102916/)

### Null Config Values Without Defaults

- **Symptoms:** NPE when accessing missing config keys, crash on first run before config exists, `NumberFormatException` for missing numeric values
- **Root cause:** Not providing defaults in `config.yml` resource, not using `getXOrDefault()` methods, assuming keys exist.
- **Detection:** Search for `getConfig().getString/Int/etc()` without null checks. Check if `config.yml` exists in resources with all expected keys.
- **Fix:**
  - Always use default parameter: `getConfig().getString("key", "default")`
  - Include complete `config.yml` in `src/main/resources/`
  - Call `saveDefaultConfig()` in `onEnable()` before any config access
  - Validate config on load, provide sensible defaults for missing keys

**Sources:** [Bukkit Wiki - Configuration API](https://bukkit.fandom.com/wiki/Configuration_API_Reference)

---

## Event Handling

### Event Priority Misuse

- **Symptoms:** Protection plugins bypassed, event changes overwritten by other plugins, logging incorrect final state
- **Root cause:** Using wrong `EventPriority`. Modifying event at MONITOR (should only observe). Using LOWEST when other plugins need to see changes. Not understanding priority order.
- **Detection:** Check `@EventHandler` annotations. Verify MONITOR handlers don't modify events. Check if HIGH/HIGHEST is used appropriately.
- **Fix:**
  - Priority order: LOWEST -> LOW -> NORMAL -> HIGH -> HIGHEST -> MONITOR
  - MONITOR: observe only, never modify or cancel
  - LOWEST: for protection plugins that may cancel
  - NORMAL: default, most plugins
  - HIGHEST: for final say on outcome (use sparingly)
  - Check `event.isCancelled()` if you need to respect cancellation

**Sources:** [PaperMC Listeners Docs](https://docs.papermc.io/paper/dev/event-listeners/), [EventPriority Javadoc](https://jd.papermc.io/paper/1.21.5/org/bukkit/event/EventPriority.html)

### Missing ignoreCancelled

- **Symptoms:** Processing cancelled events, wasted computation, unexpected side effects when other plugins cancel events
- **Root cause:** Not setting `ignoreCancelled = true` when handler should skip cancelled events. Default is to receive all events regardless of cancellation.
- **Detection:** Search for `@EventHandler` without `ignoreCancelled = true` where handler assumes event will happen.
- **Fix:**
  - Add `@EventHandler(ignoreCancelled = true)` for most handlers
  - Explicitly check `if (event.isCancelled()) return;` when fine-grained control needed
  - Only omit `ignoreCancelled` when you need to see/modify cancelled events

**Sources:** [PaperMC Listeners Docs](https://docs.papermc.io/paper/dev/event-listeners/)

---

## World/Location

### Null World Reference

- **Symptoms:** NPE on `location.getWorld()`, NPE on server startup accessing locations, "world not found" errors
- **Root cause:** Accessing world before it's loaded (in `onEnable()`), storing `Location` when world may unload, deserializing location with non-existent world name.
- **Detection:** Search for `getWorld()` calls without null checks. Check `onEnable()` for world access. Check location deserialization from config/database.
- **Fix:**
  - Check before access: `if (location.isWorldLoaded()) { ... }`
  - Delay world access until after worlds load: use `WorldLoadEvent` or delayed task
  - Store world name separately, resolve to World object only when needed
  - Handle null world gracefully: log warning, use default world, skip operation

**Sources:** [Location Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Location.html), [Bukkit Forums - World Location NullPointer](https://bukkit.org/threads/world-location-nullpointer.166239/)

### Unloaded Chunk Operations

- **Symptoms:** Operations silently fail, entities not found, blocks return AIR
- **Root cause:** Operating on chunks that aren't loaded. Most Bukkit operations on unloaded chunks either fail silently or return incorrect data.
- **Detection:** Search for `getBlock()`, `getEntities()`, entity operations without chunk load verification.
- **Fix:**
  - Check chunk loaded: `location.getChunk().isLoaded()`
  - Load if needed: `location.getChunk().load()`
  - Use async chunk loading for non-critical operations: `PaperLib.getChunkAtAsync()`
  - Consider plugin chunk tickets to keep important chunks loaded

**Sources:** [Spigot Chunk Javadoc](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Chunk.html)

---

## Performance

### Particle/Packet Spam

- **Symptoms:** Client FPS drops, players kicked for "sending too many packets", network lag, TPS drops
- **Root cause:** Spawning too many particles, sending packets every tick, not batching visual updates.
- **Detection:** Search for `spawnParticle` in tight loops or high-frequency tasks. Check particle count parameters. Look for per-tick packet sending.
- **Fix:**
  - Limit particle rate: every 2-5 ticks instead of every tick
  - Reduce particle count per spawn: use `count` parameter wisely
  - Batch visual updates: collect changes, send periodically
  - Distance check: don't send particles to players far away
  - Consider Paper's packet rate limiting configuration

**Sources:** [Paper Global Config](https://docs.papermc.io/paper/reference/global-configuration/), [Paper Issue #5173](https://github.com/PaperMC/Paper/issues/5173)

### Expensive Operations in Event Handlers

- **Symptoms:** Server lag spikes, TPS drops during certain actions, "server overloaded" warnings
- **Root cause:** Database queries, file I/O, complex calculations, or external API calls in synchronous event handlers. Main thread blocks waiting for these operations.
- **Detection:** Check event handlers for: database calls, file operations, HTTP requests, complex loops over large collections.
- **Fix:**
  - Move expensive operations async: `Bukkit.getScheduler().runTaskAsynchronously()`
  - Cache frequently accessed data
  - Pre-compute values, update cache async
  - Return to main thread only for Bukkit API calls

**Sources:** [PaperMC Scheduling Docs](https://docs.papermc.io/paper/dev/scheduler/)

---

## UUID/Player Identity

### Storing Player Names Instead of UUIDs

- **Symptoms:** Data loss when players change names, duplicate entries for same player, permission/data inconsistency
- **Root cause:** Using player names as keys in databases/configs. Player names can change; UUIDs are permanent.
- **Detection:** Search database schema for `VARCHAR` columns storing player identifiers. Check config files for player name keys. Search for `player.getName()` used as map key.
- **Fix:**
  - Always store UUID: `player.getUniqueId().toString()`
  - Migrate existing data: add UUID column, populate from name lookup, switch to UUID key
  - Cache name-to-UUID mapping for display purposes only
  - Use UUID for all internal operations, name only for display

**Sources:** [Paper Issue #10954](https://github.com/PaperMC/Paper/issues/10954)

### Offline Mode UUID Inconsistency

- **Symptoms:** Player data lost when switching online/offline mode, permissions don't persist, different UUID for same player
- **Root cause:** Offline mode generates UUIDs from player name (deterministic but different from Mojang UUIDs). Switching modes changes UUIDs.
- **Detection:** Check if server runs in offline mode behind proxy. Verify UUID generation method matches deployment.
- **Fix:**
  - Use BungeeCord/Velocity with IP forwarding (consistent UUIDs)
  - Set `online-mode-bungee: false` in paper.yml for offline proxy setups
  - Don't support arbitrary offline mode switches
  - Document deployment requirements

**Sources:** [Paper Issue #645](https://github.com/PaperMC/Paper/issues/645), [Paper Issue #7700](https://github.com/PaperMC/Paper/issues/7700)

---

## Plugin Lifecycle

### Reload Command Corruption

- **Symptoms:** Duplicate listeners, memory leaks, inconsistent state after `/reload`, NPEs after reload
- **Root cause:** `/reload` doesn't cleanly restart plugins. Static state persists, listeners may duplicate, class loader issues, metadata cleared before `onDisable()`.
- **Detection:** Test plugin with `/reload`. Check for static mutable state. Verify listeners don't duplicate.
- **Fix:**
  - Document that `/reload` is unsupported (standard practice)
  - Clear all static state in `onDisable()`
  - Unregister all listeners explicitly: `HandlerList.unregisterAll(this)`
  - Cancel all tasks: `Bukkit.getScheduler().cancelTasks(this)`
  - Test reload scenario even if unsupported

**Sources:** [Paper Issue #776](https://github.com/PaperMC/Paper/issues/776), [PaperMC Basic Troubleshooting](https://docs.papermc.io/paper/basic-troubleshooting/)

### Cyclic Plugin Dependencies

- **Symptoms:** Server fails to start, "cyclic loading detected" error, plugins load in wrong order
- **Root cause:** Plugin A depends on B, B depends on A (directly or transitively). Paper's new plugin loader detects and rejects this.
- **Detection:** Check `paper-plugin.yml` or `plugin.yml` for `depend` and `softdepend`. Trace dependency graph.
- **Fix:**
  - Refactor to remove cycle: extract shared code to common library
  - Use `softdepend` instead of `depend` where possible
  - Use API/SPI pattern: depend on interface, not implementation
  - As last resort: `-Dpaper.useLegacyPluginLoading=true` (not future-proof)

**Sources:** [PaperMC Paper Plugins Docs](https://docs.papermc.io/paper/reference/paper-plugins/)

---

## Collectibles-Specific Concerns

Based on the project context (EQ2-style collectibles with spawning, GUIs, persistence), these bugs are particularly relevant:

### High Priority for This Plugin

1. **Player Join Data Race** - Async database load vs immediate GUI access
2. **Chunk Load Entity Race** - Collectible entities not found immediately after chunk load
3. **HikariCP Connection Exhaustion** - High player counts with frequent database operations
4. **CompletableFuture Exception Swallowing** - Known issue in project context
5. **Cooldown Map Memory Leak** - Known issue in project context
6. **Inventory Click Duplication** - Collection GUIs vulnerable
7. **Entity Despawn Cleanup** - Collectible entities need tracking
8. **Particle Performance** - Collectible highlighting effects

### Audit Checklist for This Plugin

- [ ] `PlayerJoinEvent` handler waits for async data load before enabling features
- [ ] Cooldown maps cleaned in `PlayerQuitEvent`
- [ ] All `CompletableFuture` chains have `.exceptionally()` handlers
- [ ] GUI click handlers cancel ALL click types
- [ ] Collectible entities tracked and cleaned on chunk unload
- [ ] Particle tasks respect distance and rate limits
- [ ] `onDisable()` blocks on critical saves
- [ ] No `Player` objects stored in long-lived maps
