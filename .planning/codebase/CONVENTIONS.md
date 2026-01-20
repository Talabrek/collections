# Coding Conventions

**Analysis Date:** 2026-01-20

## Naming Patterns

**Files:**
- Java classes use PascalCase: `CollectionManager.java`, `PlayerProgress.java`
- Test classes append `Test` suffix: `CollectionTest.java`, `ItemBuilderTest.java`
- One class per file (standard Java convention)

**Functions:**
- camelCase for all methods: `getProgress()`, `loadPlayer()`, `handleClick()`
- Boolean getters use `is`/`has`/`can` prefix: `isComplete()`, `hasItem()`, `canPlayerSeeCollectible()`
- Private methods describe action: `checkCooldown()`, `processCollection()`
- Event handlers follow `onEventName` pattern: `onPlayerJoin()`, `onInteractEntity()`

**Variables:**
- camelCase for all variables: `playerId`, `collectionManager`, `configManager`
- Static final constants in SCREAMING_SNAKE_CASE: `EMPTY` (in records)
- Single-letter variables avoided except in loops

**Types:**
- PascalCase for classes, interfaces, enums, records
- Enums use SCREAMING_SNAKE_CASE values: `COMMON`, `UNCOMMON`, `RARE`, `EVENT`
- Interfaces have descriptive names without `I` prefix: `Storage`, `GUIHolder`
- Record types for immutable data: `Collection`, `CollectionItem`, `Collectible`

**Packages:**
- All lowercase: `com.blockworlds.collections`
- Functional grouping: `manager`, `model`, `listener`, `gui`, `storage`, `util`, `command`, `config`, `task`, `spawn`, `recipe`

## Code Style

**Formatting:**
- No explicit formatter config file (uses IDE defaults)
- 4-space indentation (standard Java)
- Braces on same line as control structures
- Max line length appears to be ~120 characters

**Linting:**
- No ESLint/Checkstyle configuration detected
- Relies on IDE inspections and compiler warnings
- Uses `@SuppressWarnings("unchecked")` when casting generic types from YAML

## Import Organization

**Order:**
1. Project imports (`com.blockworlds.collections.*`)
2. Paper/Bukkit imports (`org.bukkit.*`, `io.papermc.paper.*`)
3. External library imports (`net.kyori.adventure.*`, `com.mojang.brigadier.*`)
4. Java standard library imports (`java.util.*`, `java.io.*`, `java.sql.*`)
5. Static imports at end

**Path Aliases:**
- None - uses fully qualified package paths
- Wildcard imports avoided (explicit class imports preferred)

## Error Handling

**Patterns:**
- Use `try-catch` with specific exception types, not blanket `Exception`
- Log exceptions with context: `plugin.getLogger().log(Level.WARNING, "message", throwable)`
- Return sensible defaults on failure rather than throwing
- Use `CompletableFuture.exceptionally()` for async error handling

**Async Error Handling:**
```java
// Pattern from PlayerDataManager
storage.loadPlayer(id)
    .orTimeout(30, TimeUnit.SECONDS)
    .thenApply(progress -> {
        // Success handling
    })
    .exceptionally(throwable -> {
        plugin.getLogger().log(Level.WARNING,
                "Failed to load player data for " + id, throwable);
        // Return fallback value
        return new PlayerProgress(id);
    });
```

**Null Safety:**
- Explicit null checks before usage: `if (collection == null) return;`
- Use `Map.computeIfAbsent()` for safe map operations
- Record constructors validate and default null fields

## Logging

**Framework:** Bukkit Logger (`plugin.getLogger()`)

**Patterns:**
- Info level for startup/shutdown: `getLogger().info("Collections enabled!")`
- Warning level for recoverable issues: `getLogger().warning("Collection not found")`
- Use `log(Level.WARNING, message, throwable)` to include stack traces
- Debug logging gated by config: `if (configManager.isDebugMode())`

**When to Log:**
- Plugin enable/disable
- Configuration load results
- Errors during async operations
- Debug information (gated)

## Comments

**When to Comment:**
- Javadoc on all public methods with `@param` and `@return`
- Section comments using `// ========== Section Name ==========`
- Inline comments for non-obvious logic
- TODO/FIXME comments for known issues

**JSDoc/TSDoc (Javadoc):**
```java
/**
 * Load player data asynchronously. Called on player join.
 *
 * @param player The player to load data for
 * @return CompletableFuture containing the player's progress
 */
public CompletableFuture<PlayerProgress> loadPlayer(Player player) {
```

## Function Design

**Size:** Methods kept reasonably short (< 50 lines typical)

**Parameters:**
- Prefer few parameters (1-3)
- Use record types for complex parameter groups
- Validate parameters at start of method

**Return Values:**
- Use `CompletableFuture<T>` for async operations
- Return `null` for "not found" scenarios
- Return empty collections rather than null for lists

## Module Design

**Exports:**
- Public methods for external API
- Package-private (default) for internal helpers
- Private for implementation details

**Barrel Files:**
- Not used - Java doesn't have this pattern

## Class Design Patterns

**Manager Classes:**
- Hold plugin reference and dependencies
- Initialize in dependency order in main plugin class
- Provide business logic methods
- Example: `CollectionManager`, `PlayerDataManager`, `SpawnManager`

**Listener Classes:**
- One listener per concern
- Store manager references in constructor
- Use `@EventHandler` with explicit priority
- Example: `PlayerListener`, `GUIListener`, `CollectibleInteractListener`

**Model Classes (Records):**
- Use Java records for immutable data
- Validation in compact constructor
- Provide utility methods as needed
- Example: `Collection`, `CollectionItem`, `PlayerProgress.CollectionProgress`

**Builder Pattern:**
- Fluent builder for complex object construction
- Static factory methods: `ItemBuilder.of(Material)`
- Build method returns completed object
- Example: `ItemBuilder`

## Text Handling

**Adventure API Exclusively:**
```java
// Use Component API
player.sendMessage(Component.text("Hello", NamedTextColor.GREEN));

// Use MiniMessage for formatting
MiniMessage.miniMessage().deserialize("<gold>Item Name</gold>");
```

**Never use:**
- ChatColor
- Legacy `&` or `§` codes
- String concatenation for colored text

## Persistent Data

**PersistentDataContainer Pattern:**
- Centralize keys in `PDCKeys` utility class
- Lazy initialization for NamespacedKey instances
- Use typed getters/setters
- Example:
```java
// Store data
ItemBuilder.of(material)
    .data(PDCKeys.COLLECTION_ID(), collectionId)
    .data(PDCKeys.ITEM_ID(), itemId)
    .build();

// Read data
String collectionId = ItemBuilder.getData(item, PDCKeys.COLLECTION_ID());
```

## Async Operations

**Threading Rules:**
- Never access Bukkit API from async threads
- Use `CompletableFuture` for database operations
- Schedule back to main thread for Bukkit operations:
```java
Bukkit.getScheduler().runTask(plugin, () -> {
    // Main thread code here
});
```

**Database Pattern:**
```java
public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
    return CompletableFuture.supplyAsync(() -> {
        // Database query
    }).thenApply(result -> {
        // Transform result
    });
}
```

## Command Registration

**Brigadier Pattern:**
```java
getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
    Commands commands = event.registrar();
    new CollectionsCommand(this).register(commands);
});
```

**Command Structure:**
```java
Commands.literal("commandname")
    .requires(src -> src.getSender().hasPermission("permission"))
    .then(Commands.argument("arg", StringArgumentType.word())
        .suggests(this::suggestValues)
        .executes(this::handleCommand))
    .build();
```

## GUI Pattern

**Interface-Based:**
- `GUIHolder` interface extends `InventoryHolder`
- `GUIType` enum for type identification
- Implement `handleClick()` and `handleClose()` methods

**Example:**
```java
public interface GUIHolder extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
    void handleClose(InventoryCloseEvent event);
    GUIType getType();
}
```

---

*Convention analysis: 2026-01-20*
