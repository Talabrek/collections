# Phase 11: Admin Commands - Research

**Researched:** 2026-01-22
**Domain:** Minecraft Paper Plugin Admin Commands, Brigadier, Offline Player Resolution
**Confidence:** HIGH

## Summary

Research reveals that the existing codebase already has substantial admin command infrastructure in place. The current `CollectionsCommand.java` already implements several admin subcommands (`complete`, `reset`, `give progress`, `give item`, etc.) but they only work for online players using `ArgumentTypes.player()`. The phase requirements call for extending these capabilities to work with offline players using UUID or name lookup.

Paper provides `ArgumentTypes.playerProfiles()` for offline player resolution, which can look up players by name via Mojang API. The `PlayerDataManager` already supports operations by UUID without requiring players to be online. Audit logging should use the plugin logger (`JavaPlugin#getLogger()`) for consistent formatting with timestamps.

**Primary recommendation:** Add an `/collections admin` subcommand tree that uses `ArgumentTypes.playerProfiles()` for player lookup, delegates to existing `PlayerDataManager` methods by UUID, adds optional `--rewards` flag for force-complete, and logs all actions via the plugin logger.

## Existing Code Analysis

### CollectionsCommand Structure

The command is registered via Brigadier lifecycle events:

```java
// From CollectionsCommand.java
getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    Commands commands = event.registrar();
    new CollectionsCommand(this).register(commands);
});
```

**Existing admin subcommands (require `collections.admin`):**
| Subcommand | Function | Limitation |
|------------|----------|------------|
| `/collections complete <player> <collection>` | Marks collection complete | Online players only |
| `/collections reset <player> [collection]` | Resets progress | Online players only |
| `/collections give progress <player> <collection> <item>` | Adds item to journal | Online players only |
| `/collections give item <player> <collection> <item>` | Gives physical item | Online players only |
| `/collections reload` | Reloads config | N/A |
| `/collections spawn <zone>` | Force spawns collectible | N/A |
| `/collections clear [zone]` | Clears collectibles | N/A |
| `/collections debug` | Toggle debug mode | N/A |
| `/collections event start|end|list` | Manage events | N/A |

**Current pattern for online player resolution:**
```java
PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
List<Player> players = resolver.resolve(ctx.getSource());
if (players.isEmpty()) {
    sender.sendMessage(Component.text("No player found.", NamedTextColor.RED));
    return Command.SINGLE_SUCCESS;
}
Player target = players.get(0);
```

### PlayerDataManager Access

The `PlayerDataManager` already supports operations by UUID without requiring the player to be online:

```java
// All these methods work with UUID, not Player object
public boolean addItem(UUID playerId, String collectionId, String itemId)
public void markComplete(UUID playerId, String collectionId)
public void claimReward(UUID playerId, String collectionId)
public void resetPlayer(UUID playerId)
public void resetCollection(UUID playerId, String collectionId)
public boolean hasItem(UUID playerId, String collectionId, String itemId)
public boolean hasCompleted(UUID playerId, String collectionId)
public PlayerProgress getProgress(UUID playerId)  // Returns cached progress or null
```

**Key insight:** For offline players whose data isn't cached, we need to load from storage:
```java
// Storage interface provides async loading
CompletableFuture<PlayerProgress> loadPlayer(UUID playerId);
```

**Critical gap:** `addItem()` returns false if progress isn't cached (line 186-189):
```java
public boolean addItem(UUID playerId, String collectionId, String itemId) {
    PlayerProgress progress = cache.get(playerId);
    if (progress == null) {
        return false;  // Won't work for offline players!
    }
    // ...
}
```

**Solution needed:** Load-then-modify pattern for offline players:
```java
storage.loadPlayer(uuid)
    .thenApply(progress -> {
        // Modify progress
        progress.addItem(collectionId, itemId);
        return progress;
    })
    .thenCompose(progress -> storage.savePlayer(progress));
```

### RewardManager Integration

The `RewardManager.giveRewards(Player player, Collection collection)` method requires an online `Player` object:
- Gives experience (`player.giveExp()`)
- Gives items to inventory
- Executes commands with `%player%` placeholder
- Spawns fireworks
- Plays sounds

**Implication:** Rewards can only be granted when the player is online. For offline `force-complete`, rewards must either be:
1. Skipped (default behavior)
2. Queued for next login (complex)
3. Only granted if player is online at command execution time

**Recommendation:** Make `--rewards` flag only work if target player is online, otherwise warn admin that rewards will not be granted.

### CollectionManager Access

Provides read-only access to collection definitions:
```java
public Collection getCollection(String id)
public Map<String, Collection> getAllCollections()
public boolean hasCollection(String id)
```

## Implementation Approach

### Command Structure

**Proposed hierarchy:**
```
/collections admin
    inspect <player>              # View player's progress summary
    complete <player> <collection> [--rewards]  # Force complete
    reset <player> [collection]   # Reset progress (already exists, needs offline support)
```

**Alternative:** Keep existing commands but add offline player support directly to them.

**Recommendation:** Add new `admin` subcommand tree that:
1. Uses `ArgumentTypes.playerProfiles()` for player lookup
2. Clearly groups admin functionality
3. Keeps existing commands for backwards compatibility with online players

### Offline Player Resolution

**Paper provides `ArgumentTypes.playerProfiles()`:**
```java
.then(Commands.argument("target", ArgumentTypes.playerProfiles())
    .executes(this::adminInspect))
```

**Resolution pattern:**
```java
PlayerProfileListResolver resolver = ctx.getArgument("target", PlayerProfileListResolver.class);
Collection<PlayerProfile> profiles = resolver.resolve(ctx.getSource());

if (profiles.isEmpty()) {
    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
    return Command.SINGLE_SUCCESS;
}

PlayerProfile profile = profiles.iterator().next();
UUID targetUuid = profile.getId();
String targetName = profile.getName();  // May be null for never-joined players
```

**Important notes:**
1. `playerProfiles()` can make Mojang API calls - consider async resolution
2. Returns `Collection<PlayerProfile>` - use first entry for single-player commands
3. `profile.getName()` may return null if player has never joined server
4. `profile.getId()` is the UUID we need for database operations

**Alternative approach - manual name/UUID parsing:**
```java
// Use StringArgumentType.word() and parse manually
String input = ctx.getArgument("target", String.class);
UUID uuid;
try {
    uuid = UUID.fromString(input);
} catch (IllegalArgumentException e) {
    // Not a UUID, try as player name
    OfflinePlayer offline = Bukkit.getOfflinePlayer(input);
    uuid = offline.getUniqueId();
    // Warning: creates fake UUID for unknown names
}
```

**Recommendation:** Use `ArgumentTypes.playerProfiles()` as primary method with tab completion, but also accept raw UUID strings as fallback.

### Audit Logging

**Standard approach - use plugin logger:**
```java
// In command handler
String executor = sender instanceof Player p ? p.getName() : "CONSOLE";
String timestamp = Instant.now().toString();

plugin.getLogger().info(String.format(
    "[ADMIN] %s executed by %s on player %s: %s",
    action,           // "FORCE_COMPLETE", "RESET", etc.
    executor,
    targetName != null ? targetName : targetUuid.toString(),
    details           // "collection=forest_specimens", etc.
));
```

**Output format in server log:**
```
[10:23:45 INFO]: [Collections] [ADMIN] FORCE_COMPLETE executed by AdminPlayer on player TargetPlayer: collection=forest_specimens, rewards=false
```

**Why plugin logger over file/database:**
1. Integrates with existing server logging infrastructure
2. No additional dependencies or configuration
3. Timestamps automatically added by logging framework
4. Can be captured by external log aggregation tools
5. Admins already monitor server logs

## Technical Details

### Brigadier Patterns

**Existing pattern from codebase (follow this style):**
```java
.then(Commands.literal("admin")
    .requires(src -> src.getSender().hasPermission("collections.admin"))
    .then(Commands.literal("inspect")
        .then(Commands.argument("target", ArgumentTypes.playerProfiles())
            .executes(this::adminInspect)))
    .then(Commands.literal("complete")
        .then(Commands.argument("target", ArgumentTypes.playerProfiles())
            .then(Commands.argument("collection", StringArgumentType.word())
                .suggests(this::suggestCollections)
                .executes(ctx -> adminComplete(ctx, false))
                .then(Commands.literal("--rewards")
                    .executes(ctx -> adminComplete(ctx, true)))))))
```

**Suggestion provider (reuse existing):**
```java
private CompletableFuture<Suggestions> suggestCollections(
        CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
    for (String id : collectionManager.getAllCollections().keySet()) {
        builder.suggest(id);
    }
    return builder.buildFuture();
}
```

### Permission Structure

| Permission | Description |
|------------|-------------|
| `collections.admin` | All admin commands (existing) |
| `collections.admin.inspect` | View any player's progress (optional granularity) |
| `collections.admin.complete` | Force complete collections (optional granularity) |

**Recommendation:** Keep using single `collections.admin` permission for simplicity. The existing commands already use this pattern.

### Async Considerations

**Problem:** `playerProfiles()` may make Mojang API calls, and storage operations are async.

**Solution:** Run resolution and storage operations asynchronously:
```java
private int adminInspect(CommandContext<CommandSourceStack> ctx) {
    var sender = ctx.getSource().getSender();

    // Resolve profiles (may be async internally)
    PlayerProfileListResolver resolver = ctx.getArgument("target", PlayerProfileListResolver.class);

    Bukkit.getAsyncScheduler().runNow(plugin, task -> {
        try {
            Collection<PlayerProfile> profiles = resolver.resolve(ctx.getSource());
            // ... handle profiles ...

            // Load player data
            storage.loadPlayer(uuid).thenAccept(progress -> {
                // Send response back on main thread
                Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                    sender.sendMessage(...);
                });
            });
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        }
    });

    return Command.SINGLE_SUCCESS;
}
```

## Risks and Considerations

### Risk 1: Mojang API Rate Limits
**What:** `playerProfiles()` queries Mojang servers for unknown players
**Impact:** Potential slowdown or rate limiting with many lookups
**Mitigation:** Cache results, prefer UUID input for bulk operations

### Risk 2: Offline Player Data Not Cached
**What:** `PlayerDataManager` cache only contains online players
**Impact:** `addItem()`, `markComplete()` fail silently for offline players
**Mitigation:** Add new methods that load-then-modify-then-save for offline players

### Risk 3: Rewards for Offline Players
**What:** `RewardManager` requires online `Player` for rewards
**Impact:** Cannot grant XP, items, fireworks to offline players
**Mitigation:** Document limitation, only grant rewards if player online

### Risk 4: Name vs UUID Confusion
**What:** Players can change names; names aren't unique
**Impact:** Admin might modify wrong player
**Mitigation:** Always log and confirm UUID, warn if player never joined

### Risk 5: Breaking Existing Commands
**What:** Modifying existing `/collections complete` might break workflows
**Impact:** Admins relying on current behavior affected
**Mitigation:** Add new `/collections admin` tree, keep existing commands

## Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Command Structure | HIGH | Based on actual codebase analysis |
| PlayerDataManager Integration | HIGH | Verified by reading source code |
| Offline Player Resolution | MEDIUM | Paper docs confirm playerProfiles(), exact async behavior needs testing |
| Audit Logging | HIGH | Standard Java logging pattern, used throughout codebase |
| RewardManager Limitation | HIGH | Verified Player parameter requirement in source |

## Open Questions

1. **Should existing commands be modified or new `admin` tree added?**
   - Recommendation: Add new tree for clarity, deprecate old commands over time

2. **How to handle never-joined players?**
   - Recommendation: Allow, but warn that no data exists; create empty progress

3. **Should rewards be queued for offline players?**
   - Recommendation: No, too complex; only grant if online at command time

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `CollectionsCommand.java`, `PlayerDataManager.java`, `RewardManager.java`, `Storage.java`
- [ArgumentTypes JavaDoc (Paper 1.21.4)](https://jd.papermc.io/paper/1.21.4/io/papermc/paper/command/brigadier/argument/ArgumentTypes.html)

### Secondary (MEDIUM confidence)
- [PaperMC Docs - Arguments and literals](https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/)
- [PaperMC Docs - Entity and Player Arguments](https://docs.papermc.io/paper/dev/command-api/arguments/entity-player/)
- [Paper GitHub Issue #10954 - OfflinePlayer.getName() behavior](https://github.com/PaperMC/Paper/issues/10954)

### Tertiary (LOW confidence)
- [Logging in Minecraft - Best Practices Blog](https://chojo.dev/blog/2023/08/10/logging-in-minecraft---the-good-and-better-way/)
- [AuditTrail Plugin - SpigotMC](https://www.spigotmc.org/resources/audittrail.15140/)

## Metadata

**Research date:** 2026-01-22
**Valid until:** 30 days (stable APIs, minimal changes expected)

**Confidence breakdown:**
- Standard stack: HIGH - Using existing Paper Brigadier patterns
- Architecture: HIGH - Extending existing command structure
- Pitfalls: HIGH - Identified key issues with offline player data access
