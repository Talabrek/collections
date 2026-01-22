# Phase 12: Metrics & Observability - Research

**Researched:** 2026-01-22
**Domain:** Plugin metrics, analytics, PlaceholderAPI integration
**Confidence:** HIGH

## Summary

This phase adds observability through bStats community metrics and PlaceholderAPI placeholders. bStats 3.1.0 provides the standard mechanism for Minecraft plugin analytics with support for various chart types. PlaceholderAPI 2.11.7 enables player-facing statistics via placeholders usable in chat, scoreboard, and hologram plugins.

The codebase already has excellent hook points: `PlayerDataManager.addItem()` for item collection, `PlayerDataManager.markComplete()` for collection completion, and `SpawnManager.spawnCollectible()`/`SpawnResult` for spawn tracking. The Storage interface already includes `getTotalCollectiblesCollected()` and `getTotalCollectionsCompleted()` methods.

**Primary recommendation:** Create a MetricsManager with AtomicLong counters for runtime tracking, persist to database on shutdown/periodic intervals, and expose via bStats SimplePie/SingleLineChart and PlaceholderAPI expansion.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| bstats-bukkit | 3.1.0 | Community analytics | Official Minecraft plugin metrics service; used by thousands of plugins |
| PlaceholderAPI | 2.11.7 | Placeholder integration | De-facto standard for placeholder support; 1.7M+ downloads |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| java.util.concurrent.atomic.AtomicLong | JDK 21 | Thread-safe counters | Runtime counter increments from multiple threads |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| bStats | MCStats (defunct) | bStats is the only active option |
| PlaceholderAPI | Custom message formatting | PAPI has ecosystem integration with 240+ expansions |

**Installation (build.gradle.kts additions):**
```kotlin
repositories {
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // bStats for community metrics
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // PlaceholderAPI (optional - soft depend)
    compileOnly("me.clip:placeholderapi:2.11.7")
}

tasks.shadowJar {
    // Add relocation for bStats
    relocate("org.bstats", "com.blockworlds.collections.lib.bstats")
}
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/blockworlds/collections/
├── metrics/
│   ├── MetricsManager.java       # Main metrics orchestrator
│   ├── MetricsCounter.java       # Thread-safe counter wrapper
│   └── CollectionsExpansion.java # PlaceholderAPI expansion
```

### Pattern 1: Centralized MetricsManager
**What:** Single manager class that tracks all counters and exposes them via bStats and PAPI
**When to use:** When metrics need to be accessed from multiple locations (listeners, managers)
**Example:**
```java
public class MetricsManager {
    // Thread-safe counters for runtime tracking
    private final AtomicLong itemsCollected = new AtomicLong(0);
    private final AtomicLong collectionsCompleted = new AtomicLong(0);
    private final AtomicLong spawnAttempts = new AtomicLong(0);
    private final AtomicLong spawnSuccesses = new AtomicLong(0);
    private final AtomicLong spawnFailures = new AtomicLong(0);

    // Increment methods called from hook points
    public void recordItemCollected() {
        itemsCollected.incrementAndGet();
    }

    public void recordCollectionCompleted() {
        collectionsCompleted.incrementAndGet();
    }

    public void recordSpawnAttempt(boolean success) {
        spawnAttempts.incrementAndGet();
        if (success) {
            spawnSuccesses.incrementAndGet();
        } else {
            spawnFailures.incrementAndGet();
        }
    }

    // Getters for bStats and PAPI
    public long getItemsCollected() {
        return itemsCollected.get();
    }

    public double getSpawnSuccessRate() {
        long attempts = spawnAttempts.get();
        return attempts > 0 ? (double) spawnSuccesses.get() / attempts * 100 : 0;
    }
}
```

### Pattern 2: bStats Initialization
**What:** Initialize Metrics in onEnable() with plugin ID and custom charts
**When to use:** Always - required for bStats to work
**Example:**
```java
// In Collections.java onEnable()
int pluginId = XXXXX; // Get from bstats.org after adding plugin
Metrics metrics = new Metrics(this, pluginId);

// Simple pie for storage type
metrics.addCustomChart(new SimplePie("storage_type", () -> {
    return configManager.getDatabaseType(); // "sqlite" or "mysql"
}));

// Single line chart for items collected
metrics.addCustomChart(new SingleLineChart("items_collected", () -> {
    return (int) metricsManager.getItemsCollectedSinceLastReport();
}));
```

### Pattern 3: PlaceholderExpansion Internal Class
**What:** Extend PlaceholderExpansion, override persist() to true, register in onEnable()
**When to use:** For plugins that bundle their own placeholders
**Example:**
```java
public class CollectionsExpansion extends PlaceholderExpansion {
    private final Collections plugin;

    @Override
    public String getIdentifier() { return "collections"; }

    @Override
    public String getAuthor() { return "BlockWorlds"; }

    @Override
    public String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override
    public boolean persist() { return true; } // Keep registered during /papi reload

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "completed" -> {
                PlayerProgress p = playerDataManager.getProgress(player.getUniqueId());
                return p != null ? String.valueOf(p.getTotalCollectionsCompleted()) : "0";
            }
            case "items" -> {
                PlayerProgress p = playerDataManager.getProgress(player.getUniqueId());
                return p != null ? String.valueOf(p.getTotalCollectiblesCollected()) : "0";
            }
            case "server_total" -> {
                return String.valueOf(metricsManager.getTotalItemsCollected());
            }
            case "server_completed" -> {
                return String.valueOf(metricsManager.getTotalCollectionsCompleted());
            }
            default -> { return null; }
        }
    }
}
```

### Anti-Patterns to Avoid
- **Synchronizing on counters:** Use AtomicLong instead of synchronized blocks
- **Blocking bStats callbacks:** Chart callbacks must return quickly
- **Not relocating bStats:** Must shade and relocate to avoid conflicts
- **Hardcoding plugin ID:** Store in config or constant, not magic number

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Analytics collection | Custom HTTP client to own server | bStats | Established ecosystem, privacy-respecting, free hosting |
| Placeholder parsing | Regex-based placeholder replacement | PlaceholderAPI | 240+ integrations, standard format, maintained |
| Thread-safe counters | Volatile + synchronized | AtomicLong | Lock-free, better performance, no deadlock risk |
| Chart visualization | Custom web dashboard | bStats dashboard | Free hosting, automatic aggregation |

**Key insight:** bStats handles all the complexity of data collection, aggregation, privacy, and visualization. PlaceholderAPI handles integration with chat, scoreboards, holograms, and hundreds of other plugins.

## Common Pitfalls

### Pitfall 1: Not Shading bStats
**What goes wrong:** ClassNotFoundException or NoClassDefFoundError at runtime; conflicts with other plugins using different bStats versions
**Why it happens:** bStats classes not included in JAR, or multiple plugins have same unrelicated package
**How to avoid:** Always shade AND relocate bStats in shadowJar task
**Warning signs:** Plugin fails to enable with bStats-related errors

### Pitfall 2: Blocking in bStats Callbacks
**What goes wrong:** Server lag spikes during metrics collection
**Why it happens:** bStats collects metrics on async thread but callbacks run sequentially
**How to avoid:** Return cached values from AtomicLong counters, never query database in callback
**Warning signs:** TPS drops correlating with metrics submission (every 30 minutes)

### Pitfall 3: PlaceholderExpansion Not Persisting
**What goes wrong:** Placeholders stop working after `/papi reload`
**Why it happens:** Default `persist()` returns false, expansion gets unregistered
**How to avoid:** Override `persist()` to return `true` for internal expansions
**Warning signs:** Placeholders work initially, fail after reload commands

### Pitfall 4: Counter Loss on Crash
**What goes wrong:** Counter values reset to 0 after server crash
**Why it happens:** Only saving counters on clean shutdown
**How to avoid:** Periodic saves (every 5 minutes) in addition to shutdown save
**Warning signs:** Metrics "reset" after crashes, cumulative totals don't match reality

### Pitfall 5: PAPI Not Installed
**What goes wrong:** NullPointerException when trying to register expansion
**Why it happens:** Not checking if PlaceholderAPI is present before registration
**How to avoid:** Check `Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")` before registering
**Warning signs:** Plugin fails to enable when PAPI not installed

## Code Examples

### bStats Setup with Custom Charts
```java
// Source: bStats official documentation (bstats.org/docs/custom-charts)
public void setupBStats() {
    int pluginId = 12345; // Replace with actual ID from bstats.org
    Metrics metrics = new Metrics(this, pluginId);

    // Storage type distribution
    metrics.addCustomChart(new SimplePie("storage_type", () ->
        configManager.getDatabaseType()
    ));

    // Number of configured collections
    metrics.addCustomChart(new SimplePie("collection_count", () -> {
        int count = collectionManager.getCollectionCount();
        if (count <= 5) return "1-5";
        if (count <= 10) return "6-10";
        if (count <= 20) return "11-20";
        return "20+";
    }));

    // Spawn success rate
    metrics.addCustomChart(new SimplePie("spawn_success_rate", () -> {
        double rate = metricsManager.getSpawnSuccessRate();
        if (rate >= 90) return "90-100%";
        if (rate >= 70) return "70-89%";
        if (rate >= 50) return "50-69%";
        return "Below 50%";
    }));

    // Items collected (line chart - shows trend over time)
    metrics.addCustomChart(new SingleLineChart("items_collected_hourly", () -> {
        return (int) metricsManager.getAndResetHourlyItemCount();
    }));
}
```

### PlaceholderAPI Expansion Registration
```java
// Source: PlaceholderAPI Wiki (wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/)
@Override
public void onEnable() {
    // ... other initialization ...

    // Register PlaceholderAPI expansion if available
    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
        new CollectionsExpansion(this).register();
        getLogger().info("PlaceholderAPI expansion registered");
    }
}
```

### Thread-Safe Counter Pattern
```java
// Source: Java Concurrency in Practice (Baeldung guide)
public class MetricsCounter {
    private final AtomicLong total = new AtomicLong(0);
    private final AtomicLong sessionCount = new AtomicLong(0);

    public void increment() {
        total.incrementAndGet();
        sessionCount.incrementAndGet();
    }

    public long getTotal() {
        return total.get();
    }

    public long getAndResetSession() {
        return sessionCount.getAndSet(0);
    }

    public void setTotal(long value) {
        total.set(value);
    }
}
```

### Gradle Configuration for bStats
```kotlin
// Source: bStats documentation + GradleUp Shadow plugin
plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks.shadowJar {
    // CRITICAL: Relocate bStats to avoid conflicts with other plugins
    relocate("org.bstats", "com.blockworlds.collections.lib.bstats")
}
```

## Hook Points in Existing Code

### Item Collection (METRICS-03)
**Location:** `PlayerDataManager.addItem()` (line 187-207)
**Hook point:** After `boolean added = progress.addItem(collectionId, itemId);` when `added == true`
**Counter:** `itemsCollected.incrementAndGet()`

### Collection Completion (METRICS-02)
**Location:** `PlayerDataManager.markComplete()` (line 214-232)
**Hook point:** After `progress.markComplete(collectionId);`
**Counter:** `collectionsCompleted.incrementAndGet()`

### Spawn Success/Failure (METRICS-04)
**Location:** `SpawnManager.attemptSpawnInZone()` (line 233-258)
**Hook point:** After `SpawnResult result = findSpawnLocation(zone);`
**Counters:**
- Always: `spawnAttempts.incrementAndGet()`
- If `result.success()`: `spawnSuccesses.incrementAndGet()`
- If `!result.success()`: `spawnFailures.incrementAndGet()`

### Alternative Hook Point for Item Collection
**Location:** `ConfirmAddGUI.confirmAdd()` (line 145-213)
**Hook point:** After `boolean added = playerDataManager.addItem(...)` when `added == true`
**Note:** This is the player-facing action, more appropriate for "items added to journal" metric

## Counter Persistence

### Storage Schema Addition
```sql
-- New table for server-wide metrics
CREATE TABLE IF NOT EXISTS metrics (
    key VARCHAR(64) PRIMARY KEY,
    value BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Storage Interface Addition
```java
// Add to Storage interface
CompletableFuture<Long> getMetric(String key);
CompletableFuture<Void> setMetric(String key, long value);
CompletableFuture<Map<String, Long>> getAllMetrics();
```

### Persistence Strategy
1. **On startup:** Load persisted counters from database into AtomicLong
2. **Runtime:** Increment AtomicLong counters (lock-free, fast)
3. **Periodic:** Save counters to database every 5 minutes (async)
4. **On shutdown:** Final save of all counters (blocking)

```java
public void loadCounters() {
    storage.getAllMetrics().thenAccept(metrics -> {
        itemsCollected.set(metrics.getOrDefault("items_collected", 0L));
        collectionsCompleted.set(metrics.getOrDefault("collections_completed", 0L));
        spawnAttempts.set(metrics.getOrDefault("spawn_attempts", 0L));
        // ... etc
    });
}

public CompletableFuture<Void> saveCounters() {
    return CompletableFuture.allOf(
        storage.setMetric("items_collected", itemsCollected.get()),
        storage.setMetric("collections_completed", collectionsCompleted.get()),
        storage.setMetric("spawn_attempts", spawnAttempts.get())
        // ... etc
    );
}
```

## bStats Plugin ID

### How to Obtain
1. Go to [bstats.org](https://bstats.org/)
2. Sign in with GitHub
3. Click "Add Plugin"
4. Fill in plugin name: "Collections"
5. Select platform: "Bukkit"
6. Submit and receive numeric plugin ID

### Configuration
Store the plugin ID as a constant:
```java
public class MetricsManager {
    private static final int BSTATS_PLUGIN_ID = XXXXX; // Replace after registration
}
```

Or in config.yml for flexibility:
```yaml
metrics:
  enabled: true
  bstats-id: XXXXX
```

## Placeholder Reference

### Player Placeholders (require player context)
| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%collections_completed%` | Player's completed collections | "5" |
| `%collections_items%` | Player's total items collected | "47" |
| `%collections_progress_<id>%` | Progress in specific collection | "3/8" |

### Server Placeholders (no player context needed)
| Placeholder | Description | Example Output |
|-------------|-------------|----------------|
| `%collections_server_total%` | Total items collected server-wide | "1234" |
| `%collections_server_completed%` | Total collections completed server-wide | "89" |
| `%collections_server_active%` | Currently spawned collectibles | "42" |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| MCStats/Plugin-Metrics | bStats | 2017 | MCStats defunct, bStats is only option |
| Copy-paste Metrics class | Maven/Gradle dependency | bStats 2.0 | Cleaner build, easier updates |
| Volatile counters | AtomicLong | Java 5+ | Better performance, no blocking |

**Deprecated/outdated:**
- MCStats: Service shut down years ago
- Copy-pasting Metrics.java: Use dependency instead
- PluginMetrics: Replaced by bStats

## Open Questions

1. **bStats Plugin ID**
   - What we know: Need to register at bstats.org to get ID
   - What's unclear: Exact ID value (requires manual registration)
   - Recommendation: Document registration process, use placeholder until ID obtained

2. **Spawn Failure Reason Tracking**
   - What we know: SpawnFailureStats tracks reasons with counts
   - What's unclear: Whether to expose all reasons via bStats or aggregate
   - Recommendation: Use DrilldownPie for failure reasons in bStats; too detailed for PAPI

## Sources

### Primary (HIGH confidence)
- [Maven Central - bstats-bukkit 3.1.0](https://central.sonatype.com/artifact/org.bstats/bstats-bukkit) - Version and coordinates verified
- [PlaceholderAPI Wiki - Creating Expansions](https://wiki.placeholderapi.com/developers/creating-a-placeholderexpansion/) - Official documentation
- [PlaceholderAPI Wiki - Using PAPI](https://wiki.placeholderapi.com/developers/using-placeholderapi/) - Dependency setup
- [bStats Custom Charts Documentation](https://bstats.org/docs/custom-charts) - Chart types and examples

### Secondary (MEDIUM confidence)
- [WorldGuard bStats Integration](https://github.com/EngineHub/WorldGuard/commit/0818b3c) - Real-world Gradle example
- [Baeldung - Atomic Variables](https://www.baeldung.com/java-atomic-variables) - AtomicLong best practices

### Tertiary (LOW confidence)
- WebSearch results for version numbers - Cross-verified with Maven Central

## Metadata

**Confidence breakdown:**
- bStats integration: HIGH - Official documentation verified, version confirmed
- PlaceholderAPI integration: HIGH - Official wiki, version verified via releases
- Counter patterns: HIGH - Standard Java concurrency, well-documented
- Hook points: HIGH - Direct examination of existing codebase
- Persistence strategy: MEDIUM - Pattern extrapolated from existing Storage interface

**Research date:** 2026-01-22
**Valid until:** 90 days (stable APIs, mature libraries)
