# Codebase Structure

**Analysis Date:** 2026-01-20

## Directory Layout

```
Collections/
├── src/
│   ├── main/
│   │   ├── java/com/blockworlds/collections/
│   │   │   ├── Collections.java           # Main plugin entry point
│   │   │   ├── command/                   # Brigadier commands
│   │   │   ├── config/                    # Configuration management
│   │   │   ├── gui/                       # Inventory GUIs
│   │   │   ├── listener/                  # Bukkit event handlers
│   │   │   ├── manager/                   # Business logic services
│   │   │   ├── model/                     # Data structures
│   │   │   ├── recipe/                    # Custom crafting recipes
│   │   │   ├── spawn/                     # Spawn location algorithms
│   │   │   ├── storage/                   # Database persistence
│   │   │   ├── task/                      # Scheduled background tasks
│   │   │   └── util/                      # Utilities and helpers
│   │   └── resources/
│   │       ├── paper-plugin.yml           # Plugin metadata
│   │       ├── plugin.yml                 # Legacy plugin metadata
│   │       ├── config.yml                 # Main configuration
│   │       ├── zones.yml                  # Spawn zone definitions
│   │       └── collections/               # Collection YAML definitions
│   └── test/
│       └── java/com/blockworlds/collections/
│           ├── CollectionsPluginTest.java # Integration tests
│           ├── model/                     # Model unit tests
│           └── util/                      # Utility unit tests
├── build.gradle.kts                       # Gradle build configuration
├── settings.gradle.kts                    # Gradle settings
├── gradlew                                # Gradle wrapper (Unix)
├── gradlew.bat                            # Gradle wrapper (Windows)
└── .planning/                             # GSD planning documents
    └── codebase/                          # Codebase analysis documents
```

## Directory Purposes

**`src/main/java/com/blockworlds/collections/`**
- Purpose: All plugin Java source code
- Contains: Main class and package directories
- Key files: `Collections.java` (plugin entry point)

**`command/`**
- Purpose: Brigadier command definitions
- Contains: Command handlers with tab completion
- Key files: `CollectionsCommand.java`

**`config/`**
- Purpose: Configuration loading and MiniMessage parsing
- Contains: ConfigManager for centralized config access
- Key files: `ConfigManager.java`

**`gui/`**
- Purpose: Inventory-based user interfaces
- Contains: GUI classes, holders, type enums
- Key files: `GUIManager.java`, `CollectionMenuGUI.java`, `CollectionDetailGUI.java`

**`listener/`**
- Purpose: Bukkit event handlers
- Contains: One listener per event domain
- Key files: `CollectibleInteractListener.java`, `ItemUseListener.java`, `GUIListener.java`

**`manager/`**
- Purpose: Core business logic and state management
- Contains: Domain-specific service classes
- Key files: `CollectionManager.java`, `SpawnManager.java`, `PlayerDataManager.java`

**`model/`**
- Purpose: Immutable data structures (records/enums)
- Contains: Pure data classes with no business logic
- Key files: `Collection.java`, `Collectible.java`, `PlayerProgress.java`

**`recipe/`**
- Purpose: Custom crafting recipe management
- Contains: Recipe registration for goggles
- Key files: `GoggleRecipeManager.java`

**`spawn/`**
- Purpose: Spawn location finding algorithms
- Contains: Adaptive grid-based spawn finder
- Key files: `AdaptiveSpawnFinder.java`, `SpawnResult.java`

**`storage/`**
- Purpose: Database persistence layer
- Contains: Storage interface and SQLite implementation
- Key files: `Storage.java`, `SQLiteStorage.java`

**`task/`**
- Purpose: Scheduled background operations
- Contains: Periodic tasks for particles and prompts
- Key files: `ParticleTask.java`, `ActionBarPromptTask.java`

**`util/`**
- Purpose: Shared utilities across layers
- Contains: ItemBuilder, PDC keys, texture helpers
- Key files: `ItemBuilder.java`, `PDCKeys.java`, `HeadUtil.java`

**`src/main/resources/`**
- Purpose: Plugin configuration and data files
- Contains: YAML configs bundled with JAR
- Key files: `paper-plugin.yml`, `config.yml`, `zones.yml`

**`src/main/resources/collections/`**
- Purpose: Collection definition YAML files
- Contains: One YAML file per collection
- Key files: `forest_floor.yml`, `nether_wastes.yml`, etc. (65+ collections)

**`src/test/java/`**
- Purpose: Unit and integration tests
- Contains: MockBukkit-based tests
- Key files: `CollectionsPluginTest.java`, `model/CollectionTest.java`

## Key File Locations

**Entry Points:**
- `src/main/java/com/blockworlds/collections/Collections.java`: Plugin main class
- `src/main/java/com/blockworlds/collections/command/CollectionsCommand.java`: All commands

**Configuration:**
- `src/main/resources/config.yml`: Main plugin settings
- `src/main/resources/zones.yml`: Spawn zone definitions
- `src/main/resources/paper-plugin.yml`: Plugin metadata and permissions
- `build.gradle.kts`: Build configuration and dependencies

**Core Logic:**
- `src/main/java/com/blockworlds/collections/manager/CollectionManager.java`: Collection loading/querying
- `src/main/java/com/blockworlds/collections/manager/SpawnManager.java`: Collectible lifecycle
- `src/main/java/com/blockworlds/collections/manager/PlayerDataManager.java`: Player progress
- `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java`: Database operations

**Testing:**
- `src/test/java/com/blockworlds/collections/CollectionsPluginTest.java`: Integration tests
- `src/test/java/com/blockworlds/collections/model/`: Model unit tests

## Naming Conventions

**Files:**
- Classes: PascalCase (e.g., `CollectionManager.java`)
- Package names: lowercase (e.g., `manager`, `model`)
- Config files: lowercase with hyphens (e.g., `paper-plugin.yml`)
- Collection YAMLs: snake_case (e.g., `forest_floor.yml`)

**Classes:**
- Manager suffix: Business logic services (e.g., `SpawnManager`, `GoggleManager`)
- Listener suffix: Event handlers (e.g., `ChunkListener`, `GUIListener`)
- GUI suffix: Inventory interfaces (e.g., `CollectionMenuGUI`)
- Task suffix: Scheduled operations (e.g., `ParticleTask`)

**Methods:**
- camelCase (e.g., `loadCollections()`, `getProgressOrLoad()`)
- Getters: `getX()` or `isX()` for booleans
- Boolean checks: `hasX()`, `canX()`, `isX()`

**Variables:**
- camelCase (e.g., `collectionId`, `playerProgress`)
- Constants: UPPER_SNAKE_CASE (e.g., `COLLECTIBLE_KEY`)
- Records use lowercase field names (e.g., `id`, `name`, `tier`)

**Directories:**
- Singular names for packages (e.g., `model` not `models`)
- Plural for resource folders with multiple items (e.g., `collections/`)

## Where to Add New Code

**New Feature (e.g., trading system):**
- Manager: `src/main/java/com/blockworlds/collections/manager/TradingManager.java`
- Models: `src/main/java/com/blockworlds/collections/model/Trade.java`
- Listener: `src/main/java/com/blockworlds/collections/listener/TradeListener.java`
- GUI: `src/main/java/com/blockworlds/collections/gui/TradingGUI.java`
- Wire in `Collections.java` constructor and `onEnable()`

**New Command Subcommand:**
- Add to `src/main/java/com/blockworlds/collections/command/CollectionsCommand.java`
- Chain new `.then()` in the Brigadier builder
- Add corresponding handler method

**New Event Handler:**
- Create: `src/main/java/com/blockworlds/collections/listener/NewEventListener.java`
- Implement `Listener` interface
- Register in `Collections.registerListeners()`

**New Model:**
- Create: `src/main/java/com/blockworlds/collections/model/NewModel.java`
- Use Java record for immutable data
- Add validation in compact constructor if needed

**New Configuration Section:**
- Add to `src/main/resources/config.yml`
- Add caching field and getter in `ConfigManager.java`
- Load in `ConfigManager.reload()`

**New Collection Definition:**
- Create: `src/main/resources/collections/new_collection.yml`
- Follow existing YAML structure (id, name, tier, items, rewards)
- Auto-loaded by `CollectionManager.loadCollections()`

**New Drop Source Type:**
- Add model: `src/main/java/com/blockworlds/collections/model/NewDropSource.java`
- Update `DropSources.java` to include new list
- Update `CollectionManager` parsing
- Create listener: `src/main/java/com/blockworlds/collections/listener/NewDropListener.java`
- Update `DropSourceManager` to index new source

**Utilities:**
- Shared helpers: `src/main/java/com/blockworlds/collections/util/`
- Keep utilities stateless when possible

## Special Directories

**`run/`**
- Purpose: Development server runtime files
- Generated: Yes (by `./gradlew runServer`)
- Committed: No (in .gitignore)

**`build/`**
- Purpose: Compiled classes and JAR output
- Generated: Yes (by `./gradlew build`)
- Committed: No (in .gitignore)

**`.gradle/`**
- Purpose: Gradle build cache
- Generated: Yes
- Committed: No (in .gitignore)

**`.planning/`**
- Purpose: GSD codebase analysis documents
- Generated: By Claude Code
- Committed: Yes

**`src/main/resources/collections/`**
- Purpose: Collection YAML definitions
- Generated: No (hand-authored or tooling-generated)
- Committed: Yes
- Note: Contains 65+ collection files, auto-copied to plugin data folder on first run

## Import Organization

**Order:**
1. Plugin internal imports (`com.blockworlds.collections.*`)
2. Paper/Bukkit API imports (`org.bukkit.*`, `io.papermc.*`)
3. Adventure API imports (`net.kyori.adventure.*`)
4. Third-party libraries (`com.zaxxer.hikari.*`, `com.mojang.brigadier.*`)
5. Java standard library (`java.util.*`, `java.sql.*`)

**Path Aliases:**
- None configured (standard Java imports)

---

*Structure analysis: 2026-01-20*
