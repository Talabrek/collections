# Phase 9: Testing & Verification - Research

**Researched:** 2026-01-21
**Domain:** MockBukkit unit testing, Paper plugin verification, JUnit 5 patterns
**Confidence:** HIGH

## Summary

This research examines how to implement unit tests for the Collections plugin's SpawnConditions and PlayerDataManager classes, verify existing tests pass, and create a manual verification checklist. The codebase already has a functional test suite using MockBukkit 4.14.0 and JUnit 5, though there is a known compilation issue that must be fixed before tests can run.

The primary challenges are:
1. **Compilation error in CollectionManager.java** - Uses `javaPlugin.getFile()` which has protected access. Must be fixed before any tests can run.
2. **SpawnConditions testing requires MockBukkit world/location mocking** - WorldMock supports biome setting, light levels, block manipulation, and time control.
3. **PlayerDataManager testing requires mock Storage interface** - Create simple mock that returns `CompletableFuture.completedFuture()` values.
4. **Pre-existing MockBukkit IncompatibleClassChangeError** - Documented in STATE.md, may affect full plugin integration tests but not unit tests of isolated classes.

**Primary recommendation:** Fix the compilation error first, then write targeted unit tests for SpawnConditions validation methods and PlayerDataManager lifecycle methods using mock Storage. Avoid integration tests that load the full plugin due to the known MockBukkit compatibility issue.

## Current Test Suite Analysis

### Existing Tests (6 test files)

| Test File | Tests | Status | Dependencies |
|-----------|-------|--------|--------------|
| `CollectionsPluginTest.java` | 15 integration tests | Affected by compilation error | Full plugin load |
| `PlayerProgressTest.java` | 10 unit tests | Pure unit tests | None |
| `CollectibleTierTest.java` | 4 unit tests | Pure unit tests | None |
| `CollectionTest.java` | 9 unit tests | Pure unit tests | None |
| `ItemBuilderTest.java` | 14 unit tests | Requires MockBukkit | WorldMock |
| `HeadUtilTest.java` | 8 unit tests | Requires MockBukkit | WorldMock |

### Compilation Blocker

**File:** `src/main/java/com/blockworlds/collections/manager/CollectionManager.java:90`
**Error:** `javaPlugin.getFile()` has protected access in JavaPlugin

```java
// Line 90 - problematic code
try (JarFile jar = new JarFile(javaPlugin.getFile())) {
```

**Fix required:** Use reflection or alternative method to access plugin JAR file. This blocks ALL test execution.

### Known MockBukkit Issue

From STATE.md: "Pre-existing MockBukkit test failure (IncompatibleClassChangeError) - does not affect functionality, only test suite"

This likely affects full plugin integration tests (`CollectionsPluginTest`) but should NOT affect:
- Pure unit tests of model classes
- Unit tests of utility classes with minimal Bukkit dependencies
- Unit tests with manual mocking (not loading full plugin)

## Standard Stack

### Core Testing
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit Jupiter | 5.11.0 | Test framework | Industry standard, excellent async support |
| MockBukkit | 4.14.0 | Bukkit API mocking | Official framework for Paper plugin testing |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Paper API | 1.21.1-R0.1-SNAPSHOT | Test compile | MockBukkit 4.14.0 compatibility |
| Mockito | Optional | Interface mocking | Custom mocks for Storage, Plugin |

**Note:** The project uses Paper API 1.21.1 for testing (not 1.21.4) because MockBukkit 4.14.0 is built against 1.21.1-R0.1-SNAPSHOT.

## Architecture Patterns

### Test File Structure
```
src/test/java/com/blockworlds/collections/
├── CollectionsPluginTest.java      # Integration tests (affected by MockBukkit issue)
├── model/
│   ├── CollectionTest.java         # EXISTING - pure unit test
│   ├── CollectibleTierTest.java    # EXISTING - pure unit test
│   ├── PlayerProgressTest.java     # EXISTING - pure unit test
│   └── SpawnConditionsTest.java    # NEW - test validation methods
├── manager/
│   └── PlayerDataManagerTest.java  # NEW - test lifecycle with mock Storage
└── util/
    ├── ItemBuilderTest.java        # EXISTING - MockBukkit required
    └── HeadUtilTest.java           # EXISTING - MockBukkit required
```

### Pattern 1: Testing SpawnConditions Without Full Plugin

SpawnConditions has two categories of methods:
1. **Pure validation methods** - `isYValid()`, `isLightValid()` - No Bukkit dependencies
2. **Location-based check** - `check(Location)` - Requires MockBukkit world

For pure validation:
```java
// Source: Analysis of SpawnConditions.java
@Test
@DisplayName("isYValid returns true for Y within range")
void testIsYValidWithinRange() {
    SpawnConditions conditions = SpawnConditions.builder()
            .minY(0)
            .maxY(64)
            .build();

    assertTrue(conditions.isYValid(32));
    assertTrue(conditions.isYValid(0));
    assertTrue(conditions.isYValid(64));
}

@Test
@DisplayName("isYValid returns false for Y outside range")
void testIsYValidOutsideRange() {
    SpawnConditions conditions = SpawnConditions.builder()
            .minY(0)
            .maxY(64)
            .build();

    assertFalse(conditions.isYValid(-1));
    assertFalse(conditions.isYValid(65));
}
```

For location-based checks with MockBukkit:
```java
// Source: MockBukkit WorldMock documentation
private ServerMock server;
private WorldMock world;

@BeforeEach
void setUp() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
}

@AfterEach
void tearDown() {
    MockBukkit.unmock();
}

@Test
@DisplayName("check passes when all conditions met")
void testCheckPassesAllConditions() {
    SpawnConditions conditions = SpawnConditions.builder()
            .minY(0)
            .maxY(100)
            .minLight(0)
            .maxLight(15)
            .build();

    Location location = new Location(world, 0, 50, 0);
    assertTrue(conditions.check(location));
}
```

### Pattern 2: Testing PlayerDataManager with Mock Storage

Create a simple mock Storage implementation:
```java
// Source: Storage interface analysis
class MockStorage implements Storage {
    private final Map<UUID, PlayerProgress> data = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<PlayerProgress> loadPlayer(UUID playerId) {
        return CompletableFuture.completedFuture(
            data.computeIfAbsent(playerId, PlayerProgress::new)
        );
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerProgress progress) {
        data.put(progress.getPlayerId(), progress);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveCollectedItem(UUID playerId, String collectionId, String itemId) {
        PlayerProgress progress = data.get(playerId);
        if (progress != null) {
            progress.addItem(collectionId, itemId);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ... other methods return completedFuture(null) or empty collections
}
```

Then test lifecycle:
```java
// Source: PlayerDataManager analysis
@Test
@DisplayName("loadPlayer caches player data")
void testLoadPlayerCachesData() throws Exception {
    PlayerMock player = server.addPlayer();

    CompletableFuture<PlayerProgress> future = playerDataManager.loadPlayer(player);
    PlayerProgress progress = future.get(5, TimeUnit.SECONDS);

    assertNotNull(progress);
    assertEquals(player.getUniqueId(), progress.getPlayerId());
    assertTrue(playerDataManager.isLoaded(player.getUniqueId()));
}

@Test
@DisplayName("saveAndUnload removes from cache")
void testSaveAndUnloadRemovesFromCache() throws Exception {
    PlayerMock player = server.addPlayer();
    UUID playerId = player.getUniqueId();

    // Load first
    playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
    assertTrue(playerDataManager.isLoaded(playerId));

    // Save and unload
    playerDataManager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);
    assertFalse(playerDataManager.isLoaded(playerId));
}
```

### Pattern 3: CompletableFuture Testing Best Practice

```java
// Source: JUnit 5 + CompletableFuture best practices
@Test
@DisplayName("async load completes within timeout")
void testAsyncLoadWithTimeout() {
    assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        PlayerProgress progress = playerDataManager.loadPlayer(player).get();
        assertNotNull(progress);
    });
}

@Test
@DisplayName("load handles storage exception gracefully")
void testLoadHandlesException() throws Exception {
    // Mock storage that fails
    Storage failingStorage = mock(Storage.class);
    when(failingStorage.loadPlayer(any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DB error")));

    PlayerDataManager manager = new PlayerDataManager(plugin, failingStorage);

    // Should return new empty progress on failure, not throw
    PlayerProgress progress = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
    assertNotNull(progress);
    assertEquals(0, progress.getTotalCollectiblesCollected());
}
```

### Anti-Patterns to Avoid

- **Loading full plugin in unit tests:** Triggers MockBukkit IncompatibleClassChangeError. Use mock dependencies instead.
- **Testing async code without timeout:** Tests can hang indefinitely. Always use `get(timeout, unit)` or `assertTimeoutPreemptively()`.
- **Relying on real file system:** Use mock configuration, not actual config files.
- **Testing private methods directly:** Test through public API instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bukkit world mocking | Custom World stub | MockBukkit WorldMock | Full API implementation |
| Player mocking | Mockito mock | MockBukkit PlayerMock | Handles events, inventory |
| Server mocking | Manual stub | MockBukkit ServerMock | Scheduler, worlds, players |
| Async completion | Thread.sleep() | CompletableFuture.get(timeout) | Deterministic, proper sync |
| Test timeouts | Manual timing | @Timeout or assertTimeoutPreemptively | JUnit 5 native |

## Common Pitfalls

### Pitfall 1: Forgetting MockBukkit.unmock()

**What goes wrong:** Subsequent tests fail with "Server is already mocked" error.

**Why it happens:** MockBukkit maintains static state that persists between tests.

**How to avoid:** Always use `@AfterEach` or `@AfterAll` with `MockBukkit.unmock()`:
```java
@AfterEach
void tearDown() {
    MockBukkit.unmock();
}
```

### Pitfall 2: Testing Location checks without setting up WorldMock

**What goes wrong:** NullPointerException when SpawnConditions.check() accesses world.

**Why it happens:** Location needs a valid World reference with working block/biome data.

**How to avoid:** Create proper WorldMock and set up test data:
```java
WorldMock world = server.addSimpleWorld("test_world");
// WorldMock automatically has blocks, biomes, light levels
Location loc = new Location(world, 0, 64, 0);
```

### Pitfall 3: Not handling CompletableFuture exceptions in tests

**What goes wrong:** Test passes but hides real failures wrapped in ExecutionException.

**Why it happens:** `future.get()` wraps exceptions in ExecutionException.

**How to avoid:** Use assertDoesNotThrow or explicit exception handling:
```java
@Test
void testLoadDoesNotThrow() {
    assertDoesNotThrow(() -> {
        playerDataManager.loadPlayer(player).get(5, TimeUnit.SECONDS);
    });
}
```

### Pitfall 4: Using @BeforeAll with MockBukkit

**What goes wrong:** Tests may interfere with each other due to shared state.

**Why it happens:** MockBukkit server state accumulates (players, worlds).

**How to avoid:** Use `@BeforeEach`/`@AfterEach` for full isolation:
```java
@BeforeEach
void setUp() {
    server = MockBukkit.mock();
    // Fresh server for each test
}
```

### Pitfall 5: MockBukkit version mismatch with Paper API

**What goes wrong:** IncompatibleClassChangeError or NoSuchMethodError.

**Why it happens:** MockBukkit version is built against specific Paper API version.

**How to avoid:** Match versions in build.gradle.kts:
```kotlin
// MockBukkit 4.14.0 is built against Paper 1.21.1
testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.14.0")
```

## Code Examples

### SpawnConditions Unit Test (Pure)

```java
// No MockBukkit needed for pure validation methods
class SpawnConditionsTest {

    @Test
    @DisplayName("NONE constant has no restrictions")
    void testNoneHasNoRestrictions() {
        SpawnConditions none = SpawnConditions.NONE;

        assertTrue(none.isYValid(Integer.MIN_VALUE));
        assertTrue(none.isYValid(Integer.MAX_VALUE));
        assertTrue(none.isLightValid(0));
        assertTrue(none.isLightValid(15));
        assertNull(none.biomes());
        assertNull(none.dimensions());
    }

    @Test
    @DisplayName("builder creates conditions with correct values")
    void testBuilderCreatesCorrectValues() {
        SpawnConditions conditions = SpawnConditions.builder()
                .minY(10)
                .maxY(50)
                .minLight(5)
                .maxLight(10)
                .requireSky(true)
                .underground(false)
                .time(SpawnConditions.TimeCondition.DAY)
                .build();

        assertEquals(10, conditions.minY());
        assertEquals(50, conditions.maxY());
        assertEquals(5, conditions.minLight());
        assertEquals(10, conditions.maxLight());
        assertTrue(conditions.requireSky());
        assertFalse(conditions.underground());
        assertEquals(SpawnConditions.TimeCondition.DAY, conditions.time());
    }

    @Test
    @DisplayName("mergeWith uses other values when specified")
    void testMergeWithOverrides() {
        SpawnConditions base = SpawnConditions.builder()
                .minY(0)
                .maxY(100)
                .time(SpawnConditions.TimeCondition.ALWAYS)
                .build();

        SpawnConditions override = SpawnConditions.builder()
                .minY(50)  // Override
                .time(SpawnConditions.TimeCondition.NIGHT)  // Override
                .build();

        SpawnConditions merged = base.mergeWith(override);

        assertEquals(50, merged.minY());  // From override
        assertEquals(Integer.MAX_VALUE, merged.maxY());  // From override (default)
        assertEquals(SpawnConditions.TimeCondition.NIGHT, merged.time());  // From override
    }
}
```

### PlayerDataManager Lifecycle Test

```java
// With MockBukkit for Player handling
class PlayerDataManagerTest {

    private ServerMock server;
    private MockStorage storage;
    private Plugin plugin;
    private PlayerDataManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        storage = new MockStorage();
        manager = new PlayerDataManager(plugin, storage);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("loadPlayer returns cached data on second call")
    void testLoadPlayerUsesCacheOnSecondCall() throws Exception {
        PlayerMock player = server.addPlayer();

        PlayerProgress first = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        PlayerProgress second = manager.loadPlayer(player).get(5, TimeUnit.SECONDS);

        assertSame(first, second, "Should return same cached instance");
    }

    @Test
    @DisplayName("getProgress returns null before load")
    void testGetProgressBeforeLoad() {
        UUID unknownId = UUID.randomUUID();
        assertNull(manager.getProgress(unknownId));
    }

    @Test
    @DisplayName("isLoaded reflects cache state")
    void testIsLoadedReflectsCacheState() throws Exception {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();

        assertFalse(manager.isLoaded(playerId));

        manager.loadPlayer(player).get(5, TimeUnit.SECONDS);
        assertTrue(manager.isLoaded(playerId));

        manager.saveAndUnload(playerId).get(5, TimeUnit.SECONDS);
        assertFalse(manager.isLoaded(playerId));
    }

    @Test
    @DisplayName("clearCache removes all cached data")
    void testClearCacheRemovesAllData() throws Exception {
        PlayerMock player1 = server.addPlayer("Player1");
        PlayerMock player2 = server.addPlayer("Player2");

        manager.loadPlayer(player1).get(5, TimeUnit.SECONDS);
        manager.loadPlayer(player2).get(5, TimeUnit.SECONDS);

        assertEquals(2, manager.getCacheSize());

        manager.clearCache();

        assertEquals(0, manager.getCacheSize());
    }
}
```

## Manual Verification Checklist

For dev server testing after all phases complete:

### Core Functionality
- [ ] Player joins - data loads without errors in console
- [ ] `/collections list` shows all collections
- [ ] `/collections stats` shows correct counts
- [ ] Collecting a collectible item works
- [ ] Collection progress persists after relog
- [ ] Collection completion triggers correctly
- [ ] Rewards claim without double-claim
- [ ] GUI opens without lag or errors

### Concurrency (Phase 2 fixes)
- [ ] Rapid join/quit doesn't cause errors
- [ ] Opening GUI immediately after join works
- [ ] Multiple players collecting same item works
- [ ] Database operations don't block main thread

### GUI Safety (Phase 3 fixes)
- [ ] Click spam doesn't duplicate rewards
- [ ] Shift-click doesn't extract items
- [ ] Number key swap blocked
- [ ] Drag operations blocked

### Memory (Phase 4 fixes)
- [ ] Player logout clears cached data
- [ ] Long server uptime doesn't increase memory

### Entity Management (Phase 5 fixes)
- [ ] Collectibles spawn in correct locations
- [ ] Chunk unload despawns collectibles
- [ ] Chunk reload restores collectibles
- [ ] No orphaned armor stands after time

### Performance (Phase 6 fixes)
- [ ] Spawn checks don't cause TPS drops
- [ ] Particle rendering is smooth
- [ ] Large number of players doesn't lag

### MySQL (Phase 8 fixes)
- [ ] MySQL connection works if configured
- [ ] SQLite still works as default
- [ ] Data persists across restarts

## Open Questions

1. **Should we fix the compilation error as part of this phase or prerequisite?**
   - What we know: `javaPlugin.getFile()` is protected, but `((JavaPlugin)plugin).getFile()` or reflection could work
   - What's unclear: Original intent of the extraction code
   - Recommendation: Fix as prerequisite (TEST-03 requires "all existing tests pass")

2. **Should we add integration tests despite MockBukkit compatibility issue?**
   - What we know: IncompatibleClassChangeError affects full plugin load
   - What's unclear: Which specific features trigger it
   - Recommendation: Focus on unit tests, skip integration tests until MockBukkit update

3. **How to test SpawnConditions.check() sky/underground logic?**
   - What we know: WorldMock supports block placement and getHighestBlockYAt
   - What's unclear: Full extent of WorldMock's sky access simulation
   - Recommendation: Test basic cases, mark complex sky/underground tests as integration-only

## Sources

### Primary (HIGH confidence)
- [MockBukkit GitHub](https://github.com/MockBukkit/MockBukkit) - Official repository and documentation
- [MockBukkit Docs](https://docs.mockbukkit.org/) - Official documentation site
- Direct code analysis of existing test files in project

### Secondary (MEDIUM confidence)
- [JUnit 5 Timeout Documentation](https://howtodoinjava.com/junit5/timeout/) - assertTimeout patterns
- [Baeldung CompletableFuture Testing](https://www.baeldung.com/java-completablefuture-unit-test) - Async testing patterns
- [LambdaTest MockBukkit Examples](https://www.lambdatest.com/automation-testing-advisor/selenium/methods/be.seeseemelk.mockbukkit.block.BlockMock.getLightLevel) - BlockMock.setLightLevel examples

### Tertiary (LOW confidence)
- Web search results for MockBukkit WorldMock capabilities - needs verification against actual API

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - MockBukkit version and JUnit 5 already in use, documented
- Architecture: HIGH - Patterns derived from existing test files in project
- Pitfalls: HIGH - Based on actual compilation error and documented MockBukkit issue

**Research date:** 2026-01-21
**Valid until:** 30 days (MockBukkit may receive updates affecting compatibility)
