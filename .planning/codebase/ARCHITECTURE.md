# Architecture

**Analysis Date:** 2026-01-20

## Pattern Overview

**Overall:** Plugin Architecture with Manager-based Service Layer

**Key Characteristics:**
- Single entry point (`Collections.java`) with dependency injection via constructor
- Manager classes encapsulate all business logic domains
- Record-based immutable data models
- Async-first database operations using CompletableFuture
- Event-driven player interactions via Bukkit listeners
- Folia-compatible scheduling using Paper's region schedulers

## Layers

**Entry Point Layer:**
- Purpose: Plugin lifecycle, dependency wiring, command/event registration
- Location: `src/main/java/com/blockworlds/collections/Collections.java`
- Contains: Main plugin class extending JavaPlugin
- Depends on: All manager classes
- Used by: Paper server

**Manager Layer:**
- Purpose: Business logic and state management
- Location: `src/main/java/com/blockworlds/collections/manager/`
- Contains: Domain-specific service classes
- Depends on: Model layer, Storage layer
- Used by: Commands, Listeners, GUI classes

Key managers:
- `CollectionManager.java` - Loads/manages collection definitions from YAML
- `SpawnManager.java` - Collectible lifecycle (spawn, despawn, tracking)
- `PlayerDataManager.java` - Player progress caching and persistence
- `ZoneManager.java` - Spawn zone definitions and location validation
- `GoggleManager.java` - Visibility system based on equipped goggles
- `DropSourceManager.java` - Alternative drop methods (mob kills, fishing, etc.)
- `RewardManager.java` - Collection completion rewards
- `EventManager.java` - Temporary event collectibles

**Model Layer:**
- Purpose: Immutable data structures
- Location: `src/main/java/com/blockworlds/collections/model/`
- Contains: Java records and enums
- Depends on: Nothing (pure data)
- Used by: All other layers

Key models:
- `Collection` - Collection definition with items, rewards, conditions
- `CollectionItem` - Single item within a collection
- `Collectible` - Active world-spawned collectible instance
- `PlayerProgress` - Player's collection journal state
- `SpawnZone` - Geographic zone with spawn rules
- `SpawnConditions` - Biome/time/Y-level/dimension filters
- `CollectibleTier` - COMMON/UNCOMMON/RARE/EVENT visibility tiers
- `DropSources` - Alternative drop configurations (mobs, blocks, fishing, loot)

**Storage Layer:**
- Purpose: Data persistence with async operations
- Location: `src/main/java/com/blockworlds/collections/storage/`
- Contains: Storage interface and SQLite implementation
- Depends on: Model layer, HikariCP
- Used by: PlayerDataManager, SpawnManager

Files:
- `Storage.java` - Interface defining all persistence operations
- `SQLiteStorage.java` - HikariCP-pooled SQLite implementation

**Listener Layer:**
- Purpose: Handle Bukkit events, translate to manager calls
- Location: `src/main/java/com/blockworlds/collections/listener/`
- Contains: Event handlers for player actions
- Depends on: Manager layer
- Used by: Bukkit event system

Key listeners:
- `CollectibleInteractListener.java` - Player clicks on collectible entity
- `ItemUseListener.java` - Right-click collection item to add to journal
- `GUIListener.java` - Inventory click handling for GUIs
- `ChunkListener.java` - Recreate entities on chunk load
- `PlayerListener.java` - Load/save player data on join/quit
- `MobDropListener.java` - Alternative drops from mob kills
- `BlockDropListener.java` - Alternative drops from block mining
- `FishingDropListener.java` - Alternative drops from fishing
- `LootDropListener.java` - Alternative drops from loot tables
- `ArmorChangeListener.java` - Update visibility when goggles equipped

**Command Layer:**
- Purpose: Admin and player commands via Brigadier
- Location: `src/main/java/com/blockworlds/collections/command/`
- Contains: Command registration and handlers
- Depends on: Manager layer, GUI layer
- Used by: Paper command system

Files:
- `CollectionsCommand.java` - All /collections subcommands

**GUI Layer:**
- Purpose: Inventory-based user interfaces
- Location: `src/main/java/com/blockworlds/collections/gui/`
- Contains: GUI builders and inventory holders
- Depends on: Manager layer, Model layer
- Used by: Commands, Listeners

Files:
- `GUIManager.java` - Shared GUI utilities, button builders, open GUI tracking
- `GUIHolder.java` - Base interface for GUI inventory holders
- `GUIType.java` - Enum of GUI types
- `CollectionMenuGUI.java` - Main collection journal browser
- `CollectionDetailGUI.java` - Single collection item view
- `ConfirmAddGUI.java` - Confirmation dialog for adding items

**Task Layer:**
- Purpose: Scheduled background operations
- Location: `src/main/java/com/blockworlds/collections/task/`
- Contains: Repeating tasks using Folia-compatible schedulers
- Depends on: Manager layer
- Used by: Main plugin class

Files:
- `ParticleTask.java` - Spawns tier-appropriate particles around collectibles
- `ActionBarPromptTask.java` - Shows "Right-click to collect" prompts

**Spawn Algorithm Layer:**
- Purpose: Find valid spawn locations within zones
- Location: `src/main/java/com/blockworlds/collections/spawn/`
- Contains: Grid-based adaptive spawn location finder
- Depends on: Model layer (SpawnZone, SpawnConditions)
- Used by: SpawnManager

Files:
- `AdaptiveSpawnFinder.java` - Grid search with expanding radius
- `SpawnResult.java` - Result container with location or failure stats
- `SpawnFailureStats.java` - Tracks failure reasons for debugging

**Configuration Layer:**
- Purpose: Config loading and message formatting
- Location: `src/main/java/com/blockworlds/collections/config/`
- Contains: ConfigManager with MiniMessage support
- Depends on: Paper API
- Used by: All layers

Files:
- `ConfigManager.java` - Centralized config access, MiniMessage parsing

**Utility Layer:**
- Purpose: Cross-cutting helper classes
- Location: `src/main/java/com/blockworlds/collections/util/`
- Contains: Static utilities and builders
- Depends on: Paper API
- Used by: All layers

Files:
- `ItemBuilder.java` - Fluent ItemStack builder with MiniMessage support
- `PDCKeys.java` - Centralized PersistentDataContainer key definitions
- `HeadUtil.java` - Custom player head texture creation

**Recipe Layer:**
- Purpose: Custom crafting recipes for goggles
- Location: `src/main/java/com/blockworlds/collections/recipe/`
- Contains: Recipe registration and management
- Depends on: GoggleManager
- Used by: Main plugin class

Files:
- `GoggleRecipeManager.java` - Registers/unregisters goggle crafting recipes

## Data Flow

**Player Collects Collectible:**

1. Player right-clicks `Interaction` entity in world
2. `CollectibleInteractListener` receives `PlayerInteractAtEntityEvent`
3. Validates player can see tier (via `GoggleManager`)
4. Checks cooldown and acquires atomic lock (race condition prevention)
5. `SpawnManager.getCollectibleByEntity()` retrieves `Collectible` record
6. `CollectionManager.getCollection()` gets collection definition
7. Pre-selected `CollectionItem` retrieved from collectible
8. Physical `ItemStack` created via `ItemBuilder` with PDC tags
9. Item given to player's inventory
10. `SpawnManager.despawnCollectible()` removes entity and starts respawn timer
11. `Storage.removeCollectible()` async removes from database

**Player Adds Item to Journal:**

1. Player right-clicks held collection item
2. `ItemUseListener` reads PDC tags (collection_id, item_id)
3. `PlayerDataManager.hasItem()` checks for duplicates
4. `ConfirmAddGUI` opens for confirmation
5. Player clicks confirm button
6. `PlayerDataManager.addItem()` updates cached `PlayerProgress`
7. `Storage.saveCollectedItem()` async persists to database
8. Item removed from player's inventory
9. Check if collection now complete via `CollectionManager`
10. If complete, `PlayerDataManager.markComplete()` and fire completion event

**Collectible Spawning:**

1. `SpawnManager.startSpawnTask()` runs periodically via GlobalRegionScheduler
2. For each zone, check if under `maxCollectibles` limit
3. `AdaptiveSpawnFinder.findLocation()` searches for valid spawn point
4. Grid-based search with expanding radius
5. Each point validated against `SpawnConditions` (biome, Y, light, time, etc.)
6. Random collection selected from zone's allowed collections
7. Collection-level spawn conditions checked against location
8. Random item selected using weighted probability
9. Item-level spawn conditions checked against location
10. `Interaction` entity spawned at location with PDC metadata
11. `Collectible` record saved to database and added to tracking map
12. `ParticleTask` will display tier-appropriate particles to eligible players

**State Management:**

- In-memory: `ConcurrentHashMap` in managers for active data
- Cached player data loaded on join, saved on quit
- Collectibles persisted to SQLite, recreated on chunk load
- Collection definitions loaded from YAML files at startup/reload

## Key Abstractions

**Collectible (Active World Instance):**
- Purpose: Represents a spawned collectible in the world
- Examples: `src/main/java/com/blockworlds/collections/model/Collectible.java`
- Pattern: Immutable Java record with `withX()` copy methods

**Collection (Definition):**
- Purpose: Template defining items, rewards, and spawn rules
- Examples: `src/main/java/com/blockworlds/collections/model/Collection.java`
- Pattern: Immutable Java record loaded from YAML

**PlayerProgress (Player State):**
- Purpose: Tracks player's collected items and completions
- Examples: `src/main/java/com/blockworlds/collections/model/PlayerProgress.java`
- Pattern: Mutable class with nested `CollectionProgress` records

**SpawnConditions (Filter):**
- Purpose: Define where collectibles/items can spawn
- Examples: `src/main/java/com/blockworlds/collections/model/SpawnConditions.java`
- Pattern: Immutable record with `check(Location)` validation method

**Storage (Persistence Interface):**
- Purpose: Abstract database operations
- Examples: `src/main/java/com/blockworlds/collections/storage/Storage.java`
- Pattern: Interface with `CompletableFuture` return types for async

## Entry Points

**Plugin Lifecycle:**
- Location: `src/main/java/com/blockworlds/collections/Collections.java`
- Triggers: Server start/stop, `/collections reload`
- Responsibilities: Initialize managers in dependency order, register commands/listeners

**Command Entry:**
- Location: `src/main/java/com/blockworlds/collections/command/CollectionsCommand.java`
- Triggers: `/collections`, `/col`, `/collect` commands
- Responsibilities: Parse arguments, validate permissions, delegate to managers/GUIs

**Event Entry:**
- Location: `src/main/java/com/blockworlds/collections/listener/*.java`
- Triggers: Bukkit events (player join, entity interact, inventory click, etc.)
- Responsibilities: Extract context, validate, delegate to managers

**Scheduled Entry:**
- Location: `src/main/java/com/blockworlds/collections/task/*.java`
- Triggers: Periodic timer via Folia-compatible schedulers
- Responsibilities: Particle spawning, validity checks, spawn attempts

## Error Handling

**Strategy:** Log-and-continue with graceful degradation

**Patterns:**
- Database operations use `CompletableFuture.exceptionally()` for error handling
- Timeout protection on all async operations (30 seconds default)
- Missing collections/items logged as warnings, not thrown
- Race conditions prevented via `AtomicBoolean` locks on collectible interactions
- Invalid YAML configurations logged but don't crash plugin load

**Example:**
```java
storage.savePlayer(progress)
    .orTimeout(30, TimeUnit.SECONDS)
    .exceptionally(throwable -> {
        plugin.getLogger().log(Level.SEVERE, "Failed to save player data", throwable);
        return null;
    });
```

## Cross-Cutting Concerns

**Logging:**
- Standard Java logging via `plugin.getLogger()`
- Debug mode toggle in config for verbose spawn/collection logging
- Spawn failure statistics for troubleshooting

**Validation:**
- PDC tags validate collection items are authentic
- Cooldown system prevents macro abuse
- Atomic locks prevent double-collection race conditions
- Permission checks on all commands

**Authentication:**
- Permission-based via Bukkit permission system
- `collections.use` - Basic access
- `collections.admin` - Admin commands
- `collections.bypass.goggles` - See all tiers without goggles
- `collections.bypass.cooldown` - No collection cooldown

**Async Safety:**
- All database operations run async via `CompletableFuture`
- Results processed on main thread when needed
- Folia-compatible schedulers for multi-threaded region support
- `ConcurrentHashMap` for thread-safe in-memory state

---

*Architecture analysis: 2026-01-20*
