# Codebase Concerns

**Analysis Date:** 2026-01-20

## Tech Debt

**Dead/Unused Code:**
- Issue: Old stub file exists at a different package path
- Files: `src/main/java/com/example/collections/CollectionsPlugin.java`
- Impact: Confusion about which main class is used; increases codebase size unnecessarily
- Fix approach: Delete the stub file at `com.example.collections` package; actual plugin is at `com.blockworlds.collections.Collections`

**Inconsistent Null Return Patterns:**
- Issue: Many methods return `null` instead of using `Optional<T>` or throwing exceptions
- Files:
  - `src/main/java/com/blockworlds/collections/manager/CollectionManager.java` (lines 171, 504, 562, 716)
  - `src/main/java/com/blockworlds/collections/manager/SpawnManager.java` (lines 116, 249, 289, 319, 551, 672)
  - `src/main/java/com/blockworlds/collections/manager/ZoneManager.java` (lines 106, 224, 242, 276)
  - `src/main/java/com/blockworlds/collections/util/HeadUtil.java` (lines 114, 125, 141, 147)
  - `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java` (lines 334, 406, 423, 488)
- Impact: Null pointer exceptions possible at runtime; callers must defensively check returns
- Fix approach: Introduce `Optional<T>` for finder methods; add `@Nullable` annotations at minimum

**Missing saveDefaultCollections Implementation:**
- Issue: `saveDefaultCollections()` only saves `collectors_initiation.yml` but 67+ collection YAML files exist in resources
- Files: `src/main/java/com/blockworlds/collections/manager/CollectionManager.java` (lines 80-103)
- Impact: New installations may not have all default collections extracted; git shows many deleted files that need to be restored
- Fix approach: Either enumerate all collection YMLs in the resource folder or remove hardcoded list and use reflection/resource scanning

**Duplicated Spawn Condition Parsing:**
- Issue: Spawn condition parsing logic exists in both `CollectionManager` and `ZoneManager`
- Files:
  - `src/main/java/com/blockworlds/collections/manager/CollectionManager.java` (parseSpawnConditions, parseConditionsFromMap)
  - `src/main/java/com/blockworlds/collections/manager/ZoneManager.java` (parseSpawnConditions)
- Impact: Code duplication; maintenance burden if parsing logic needs updates
- Fix approach: Extract to shared utility class or use only ZoneManager's public method from CollectionManager

**Duplicated Surface Location Finding:**
- Issue: `findSurfaceLocation`, `isStandableLocation`, and `hasBlockAbove` methods duplicated across classes
- Files:
  - `src/main/java/com/blockworlds/collections/spawn/AdaptiveSpawnFinder.java` (lines 207-264)
  - `src/main/java/com/blockworlds/collections/manager/ZoneManager.java` (lines 248-304)
- Impact: Bug fixes need to be applied in multiple places; inconsistency risk
- Fix approach: Extract to a `LocationUtils` utility class

## Known Bugs

**Race Condition in PlayerDataManager.getProgress():**
- Symptoms: Returns null for recently joined players if async load not yet complete
- Files: `src/main/java/com/blockworlds/collections/manager/PlayerDataManager.java` (lines 76-78)
- Trigger: Call `getProgress()` immediately after player join before async load completes
- Workaround: Use `getProgressOrLoad()` which blocks, or check for null

**Cooldown Map Memory Leak:**
- Symptoms: `lastCollectTime` map in CollectibleInteractListener grows unbounded if `cleanupPlayer()` is not called
- Files: `src/main/java/com/blockworlds/collections/listener/CollectibleInteractListener.java` (line 45)
- Trigger: Players quit without proper listener cleanup; server restart needed to clear
- Workaround: Called in PlayerListener on quit, but verify all quit paths call it

## Security Considerations

**Command Execution in Rewards:**
- Risk: Collection rewards can execute arbitrary commands via console
- Files:
  - `src/main/java/com/blockworlds/collections/model/Collection.java` (CollectionRewards.commands field)
  - `src/main/java/com/blockworlds/collections/manager/RewardManager.java`
- Current mitigation: Commands defined in YAML by server admins only
- Recommendations: Add command whitelist option; document security implications in README

**No Input Validation on Collection IDs:**
- Risk: Malformed YAML collection IDs could cause path traversal or other issues
- Files: `src/main/java/com/blockworlds/collections/manager/CollectionManager.java` (line 113)
- Current mitigation: None - IDs are used as-is from YAML
- Recommendations: Validate collection/item IDs against alphanumeric pattern

## Performance Bottlenecks

**Particle Task Iterates All Players for All Collectibles:**
- Problem: O(players * collectibles) iteration on every particle tick
- Files: `src/main/java/com/blockworlds/collections/task/ParticleTask.java` (lines 61-88)
- Cause: Nested loop checking distance for every player-collectible pair
- Improvement path: Spatial indexing (chunk-based lookup); only process collectibles in loaded chunks near players

**Linear Search for Collectible by Entity:**
- Problem: `getCollectibleByEntity()` iterates all active collectibles
- Files: `src/main/java/com/blockworlds/collections/manager/SpawnManager.java` (lines 545-552)
- Cause: No index mapping entity UUID to collectible
- Improvement path: Add `Map<UUID, UUID>` (entityId -> collectibleId) index

**Database Writes Not Batched:**
- Problem: Each item collection triggers individual INSERT statement
- Files: `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java` (lines 340-356)
- Cause: Single-item saveCollectedItem method with immediate commit
- Improvement path: Batch inserts; use transaction batching for player saves

**Grid Search Creates Many Location Objects:**
- Problem: `generateGridPoints()` creates potentially thousands of Location objects
- Files: `src/main/java/com/blockworlds/collections/spawn/AdaptiveSpawnFinder.java` (lines 176-201)
- Cause: Pre-allocating all grid points before search
- Improvement path: Use lazy iteration instead of pre-allocation

## Fragile Areas

**Chunk Load/Unload Entity Recreation:**
- Files:
  - `src/main/java/com/blockworlds/collections/manager/SpawnManager.java` (recreateEntities, markUnspawned)
  - `src/main/java/com/blockworlds/collections/listener/ChunkListener.java`
- Why fragile: Complex state synchronization between database, memory, and world entities
- Safe modification: Always verify entity state before operations; add comprehensive logging
- Test coverage: No unit tests for chunk load/unload scenarios

**GUI State Management:**
- Files:
  - `src/main/java/com/blockworlds/collections/gui/CollectionDetailGUI.java`
  - `src/main/java/com/blockworlds/collections/gui/CollectionMenuGUI.java`
  - `src/main/java/com/blockworlds/collections/gui/GUIManager.java`
- Why fragile: Player progress may change during GUI viewing; reward claiming may fail mid-transaction
- Safe modification: Re-fetch progress before any mutation; handle inventory full edge cases
- Test coverage: No GUI interaction tests

**Async Database Operations:**
- Files:
  - `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java`
  - `src/main/java/com/blockworlds/collections/manager/PlayerDataManager.java`
- Why fragile: CompletableFuture exception handling returns null silently; timeout failures may lose data
- Safe modification: Always check exceptionally() handlers log errors; never silently swallow
- Test coverage: Basic save/load tested in `CollectionsPluginTest.java`

## Scaling Limits

**In-Memory Collectible Tracking:**
- Current capacity: All active collectibles stored in `ConcurrentHashMap`
- Limit: Memory usage grows with collectible count; no limit enforcement
- Scaling path: Add configurable max total collectibles; consider database-only persistence for large deployments

**SQLite Connection Pool:**
- Current capacity: HikariCP pool with max 10 connections
- Limit: SQLite is single-writer; pool mostly helps read concurrency
- Scaling path: Add MySQL/PostgreSQL storage option for high-concurrency servers

**Single-threaded Spawn Task:**
- Current capacity: One spawn check across all zones per interval
- Limit: Large zone counts will slow spawn checks
- Scaling path: Parallelize zone checks; stagger zone processing

## Dependencies at Risk

**HikariCP Shaded and Relocated:**
- Risk: Must be properly relocated to avoid conflicts with other plugins
- Impact: ClassNotFoundException or version conflicts if not relocated
- Migration plan: Verify shadowJar relocation in build.gradle.kts; test with common plugins

**MockBukkit Test Dependency:**
- Risk: MockBukkit version must match Paper API version closely
- Impact: Tests may fail on Paper version updates
- Migration plan: Keep MockBukkit version synced with Paper API; use version ranges

## Missing Critical Features

**No Data Migration/Backup Command:**
- Problem: No way to export/import player data between servers
- Blocks: Server migration; data recovery scenarios
- Files: `src/main/java/com/blockworlds/collections/storage/SQLiteStorage.java` (backupPlayerData is stub)

**No Progress Notification System:**
- Problem: Players only see progress when opening GUI
- Blocks: Achievement-like "1/5 collected" notifications

**No Admin Force-Complete Command:**
- Problem: Admins cannot grant collection completion to players
- Blocks: Event recovery; support ticket resolution

## Test Coverage Gaps

**No Manager Unit Tests:**
- What's not tested: CollectionManager, SpawnManager, ZoneManager, PlayerDataManager business logic
- Files: All files under `src/main/java/com/blockworlds/collections/manager/`
- Risk: Logic bugs in spawn selection, condition checking, zone validation
- Priority: High - core business logic untested

**No Listener Tests:**
- What's not tested: Event handling, interaction logic, cooldown enforcement
- Files: All files under `src/main/java/com/blockworlds/collections/listener/`
- Risk: Event cancellation issues, race conditions in collect
- Priority: Medium - MockBukkit can simulate events

**No GUI Tests:**
- What's not tested: Inventory population, click handling, reward claiming flow
- Files: All files under `src/main/java/com/blockworlds/collections/gui/`
- Risk: Visual bugs, inventory manipulation issues
- Priority: Low - requires manual testing or complex mocking

**No Spawn Condition Tests:**
- What's not tested: Biome/dimension/time/Y-level/light condition evaluation
- Files: `src/main/java/com/blockworlds/collections/model/SpawnConditions.java`
- Risk: Items spawning in wrong locations
- Priority: High - core game mechanic

**No Alternative Drop Source Tests:**
- What's not tested: Mob drops, block drops, fishing drops, loot drops
- Files: All files under `src/main/java/com/blockworlds/collections/listener/*DropListener.java`
- Risk: Players not receiving drops when expected
- Priority: Medium - secondary acquisition method

---

*Concerns audit: 2026-01-20*
