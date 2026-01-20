# Testing Patterns

**Analysis Date:** 2026-01-20

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) 5.11.0
- Config: `build.gradle.kts` (lines 65-67)

**Assertion Library:**
- JUnit Jupiter Assertions (`org.junit.jupiter.api.Assertions`)

**Mocking Framework:**
- MockBukkit v1.21 (4.14.0)
- Note: Uses Paper 1.21.1 for MockBukkit compatibility (not 1.21.4)

**Run Commands:**
```bash
./gradlew test              # Run all tests
./gradlew test --info       # Run with verbose output
```

## Test File Organization

**Location:**
- Co-located in `src/test/java/` mirroring main structure
- Test packages mirror main: `com.blockworlds.collections.*`

**Naming:**
- `{ClassName}Test.java` pattern
- Example: `CollectionManager` -> `CollectionManagerTest` (if exists)

**Structure:**
```
src/test/java/
└── com/blockworlds/collections/
    ├── CollectionsPluginTest.java      # Integration tests
    ├── model/
    │   ├── CollectionTest.java         # Unit tests
    │   ├── CollectibleTierTest.java
    │   └── PlayerProgressTest.java
    └── util/
        ├── ItemBuilderTest.java        # Utility tests
        └── HeadUtilTest.java
```

## Test Structure

**Suite Organization:**
```java
/**
 * Tests for {ClassName}.
 */
class ClassNameTest {

    private static ServerMock server;    // MockBukkit server (if needed)
    private ClassName instance;          // Class under test

    @BeforeAll
    static void setUp() {
        server = MockBukkit.mock();
        // Plugin load if needed
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setup() {
        // Per-test setup
    }

    // ==================== Category Tests ====================

    @Test
    @DisplayName("Descriptive test name in sentence form")
    void testMethodName() {
        // Arrange
        // Act
        // Assert
    }
}
```

**Patterns:**
- Use `@DisplayName` for readable test descriptions
- Group related tests with section comments: `// ==================== Section ====================`
- Single assertion focus per test when practical
- Descriptive method names: `testGetItemNotFound()`, `testAddDuplicateItem()`

## Mocking

**Framework:** MockBukkit

**Server Setup Pattern:**
```java
private static ServerMock server;
private static Collections plugin;

@BeforeAll
static void setUp() {
    server = MockBukkit.mock();
    plugin = MockBukkit.load(Collections.class);
}

@AfterAll
static void tearDown() {
    MockBukkit.unmock();
}
```

**Player Mocking:**
```java
// Create mock player
PlayerMock player = server.addPlayer();
PlayerMock namedPlayer = server.addPlayer("TestPlayer1");

// Execute ticks to allow async operations
server.getScheduler().performTicks(40);

// Test command execution
player.performCommand("collections help");
String message = player.nextMessage();
```

**What to Mock:**
- Server environment (via MockBukkit)
- Player instances
- Scheduler ticks for async operations
- World interactions

**What NOT to Mock:**
- Model classes (test actual behavior)
- Utility classes (test actual logic)
- Records (immutable, test directly)

## Fixtures and Factories

**Test Data:**
```java
@BeforeEach
void setup() {
    items = List.of(
            new CollectionItem("item1", "Item 1", Material.DIAMOND, List.of(), 10, true, null, null),
            new CollectionItem("item2", "Item 2", Material.GOLD_INGOT, List.of(), 5, true, null, null),
            new CollectionItem("item3", "Item 3", Material.IRON_INGOT, List.of(), 2, true, null, null)
    );

    collection = new Collection(
            "test_collection",
            "Test Collection",
            "A test collection",
            CollectibleTier.COMMON,
            items,
            Collection.CollectionRewards.EMPTY,
            List.of(),
            List.of(),
            "",
            Material.PAPER,
            null
    );
}
```

**Location:**
- Test data created inline in `@BeforeEach` methods
- No separate fixture files
- Reusable data stored as test class fields

## Coverage

**Requirements:** None enforced (no coverage configuration)

**View Coverage:**
```bash
./gradlew test jacocoTestReport    # If JaCoCo added
```

## Test Types

**Unit Tests:**
- `src/test/java/com/blockworlds/collections/model/CollectionTest.java`
- `src/test/java/com/blockworlds/collections/model/CollectibleTierTest.java`
- `src/test/java/com/blockworlds/collections/model/PlayerProgressTest.java`
- Tests isolated classes without plugin environment
- Direct instantiation of records and model classes

**Integration Tests:**
- `src/test/java/com/blockworlds/collections/CollectionsPluginTest.java`
- Uses MockBukkit to load full plugin
- Tests manager initialization, command handling, storage operations
- Requires server/plugin lifecycle

**Utility Tests:**
- `src/test/java/com/blockworlds/collections/util/ItemBuilderTest.java`
- `src/test/java/com/blockworlds/collections/util/HeadUtilTest.java`
- Tests builder patterns and utility methods
- Requires MockBukkit for Bukkit API access

**E2E Tests:**
- Not used (no Selenium/Playwright equivalent for Minecraft)
- Manual testing via `./gradlew runServer`

## Common Patterns

**Async Testing:**
```java
@Test
@DisplayName("Storage can save and load player progress")
void testStorageSaveLoad() throws Exception {
    UUID playerId = UUID.randomUUID();
    PlayerProgress progress = new PlayerProgress(playerId);
    progress.addItem("test_collection", "test_item");

    // Save (async with timeout)
    plugin.getStorage().savePlayer(progress).get(5, TimeUnit.SECONDS);

    // Load (async with timeout)
    PlayerProgress loaded = plugin.getStorage().loadPlayer(playerId).get(5, TimeUnit.SECONDS);

    assertNotNull(loaded, "Should load saved progress");
    assertTrue(loaded.hasItem("test_collection", "test_item"));
}
```

**Scheduler Tick Testing:**
```java
@Test
void testPlayerJoinLoadsData() {
    PlayerMock player = server.addPlayer();

    // Perform ticks to allow async operations
    server.getScheduler().performTicks(40);

    // Assert after async completes
    PlayerProgress progress = plugin.getPlayerDataManager().getProgress(player.getUniqueId());
    assertNotNull(progress);
}
```

**Error Testing:**
```java
@Test
@DisplayName("fromString returns COMMON for invalid input")
void testFromStringInvalid() {
    assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString("INVALID"));
    assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString(null));
    assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString(""));
    assertEquals(CollectibleTier.COMMON, CollectibleTier.fromString("  "));
}
```

**Weighted Random Testing:**
```java
@Test
@DisplayName("getRandomItem returns items based on weight")
void testGetRandomItemWeight() {
    Map<String, Integer> counts = new HashMap<>();
    int iterations = 1000;

    for (int i = 0; i < iterations; i++) {
        CollectionItem item = collection.getRandomItem();
        counts.merge(item.id(), 1, Integer::sum);
    }

    // Statistical assertion
    assertTrue(counts.get("item1") > counts.get("item3"),
            "item1 with weight 10 should appear more than item3 with weight 2");
}
```

**Command Testing:**
```java
@Test
@DisplayName("Collections command responds to help")
void testCollectionsHelpCommand() {
    PlayerMock player = server.addPlayer();
    server.getScheduler().performTicks(20);

    player.performCommand("collections help");

    String message = player.nextMessage();
    assertNotNull(message, "Should receive help message");
}
```

## Assertion Patterns

**Use Descriptive Messages:**
```java
assertNotNull(plugin.getConfigManager(), "ConfigManager should be initialized");
assertTrue(progress.hasItem("collection1", "item1"), "Should have the saved item");
assertEquals(CollectibleTier.COMMON, tier, "Invalid tier should default to COMMON");
```

**Assert DoesNotThrow:**
```java
@Test
void testPluginReload() {
    assertDoesNotThrow(() -> plugin.reload(), "Reload should not throw");
}
```

**Static Imports:**
```java
import static org.junit.jupiter.api.Assertions.*;
```

## Test Class Annotations

**Common Annotations:**
- `@Test` - Marks test method
- `@DisplayName("...")` - Human-readable test name
- `@BeforeAll` / `@AfterAll` - Class-level setup/teardown (static)
- `@BeforeEach` - Per-test setup

## MockBukkit Compatibility Note

**Version Mismatch Handling:**
```kotlin
// In build.gradle.kts
// MockBukkit v1.21 is built against 1.21.1, not 1.21.4
testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.14.0")
```

This means tests compile against Paper 1.21.1 while the main code targets 1.21.4. Be aware of API differences when testing new 1.21.4 features.

## Writing New Tests

**For Model Classes:**
1. Create `{ClassName}Test.java` in matching test package
2. No MockBukkit needed for pure data classes
3. Test record validation, methods, and edge cases

**For Manager Classes:**
1. Use MockBukkit setup pattern
2. Load plugin: `MockBukkit.load(Collections.class)`
3. Access managers via plugin getters
4. Use `performTicks()` for async operations

**For Utilities:**
1. Use MockBukkit if Bukkit API needed (ItemBuilder, HeadUtil)
2. Test builder chain patterns
3. Test static helper methods

---

*Testing analysis: 2026-01-20*
