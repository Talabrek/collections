# Stack Research: v1.1 Operational Features

**Researched:** 2026-01-22
**Focus:** Stack additions for data export/import, progress notifications, admin force-complete, and metrics collection

## Summary

The v1.1 operational features require minimal stack additions. The plugin already has robust foundations: HikariCP for database pooling, Adventure API for rich text notifications, and Brigadier for commands. The primary additions are bStats for metrics collection. Critically, **Gson is already bundled with Paper servers** (v2.8.9+), so JSON serialization for data export/import requires no new dependencies - just a `compileOnly` reference. Progress notifications leverage the existing Adventure/MiniMessage stack. Admin force-complete extends existing command patterns.

## Recommendations

### Data Export/Import: Gson (Bundled)

**Recommendation:** Use Gson for JSON serialization - already bundled with Paper
**Version:** 2.8.9 (provided by Paper server at runtime)
**Dependency Scope:** `compileOnly` (DO NOT shade - already in server classloader)

**Rationale:**
- Gson is already shaded into Paper/Spigot servers, avoiding classloader conflicts
- PlayerProgress model is simple (UUID, maps of strings/booleans/longs) - no complex serialization needed
- Gson handles Java records and standard collections well out of the box
- Smaller JAR size vs shading Jackson (which would be redundant)
- The Adventure API uses Gson internally for component serialization

**Integration:**
Add to `build.gradle.kts`:
```kotlin
compileOnly("com.google.gson:gson:2.10.1") // For IDE completion, server provides runtime
```

**Export Format Design:**
```json
{
  "formatVersion": 1,
  "exportDate": "2026-01-22T15:30:00Z",
  "playerId": "uuid-string",
  "collections": {
    "forest_specimens": {
      "items": ["acorn", "maple_leaf"],
      "complete": true,
      "rewardClaimed": false,
      "completedDate": 1705939200000
    }
  },
  "stats": {
    "totalCollectiblesCollected": 42,
    "totalCollectionsCompleted": 3,
    "firstCollectionDate": 1705852800000,
    "lastActivityDate": 1705939200000
  }
}
```

### Progress Notifications: Adventure API (Existing)

**Recommendation:** Use existing Adventure API stack - no additions needed
**Components:** ActionBar, Title, MiniMessage formatting

**Rationale:**
- ActionBarPromptTask already exists for collectible proximity prompts
- Adventure API provides ActionBar, Title, BossBar, and Chat components
- MiniMessage already configured in config.yml with rich formatting
- Consistent with existing notification patterns

**Integration:**
Extend existing ConfigManager messages:
```yaml
messages:
  # New progress notification messages
  progress-notification: "<gray>Progress: <gold><count>/<total></gold> collected</gray>"
  progress-milestone: "<green>Milestone! <gold><count>/<total></gold> - keep going!</green>"
```

Notification options (configurable):
- **ActionBar:** Unobtrusive, already used for "Press F to collect"
- **Title:** More prominent for milestones (collection half-complete, etc.)
- **Chat:** Standard for detailed progress info

### Admin Force-Complete: Brigadier (Existing)

**Recommendation:** Extend existing CollectionsCommand - no additions needed
**Pattern:** Follow existing `completeCollection` command implementation

**Rationale:**
- `completeCollection` command already exists and works correctly
- Need to add optional `--with-rewards` flag variant
- PlayerDataManager has all required methods (`addItem`, `markComplete`, `claimReward`)
- Tab completion infrastructure exists for players and collections

**Integration:**
Extend existing command tree:
```java
// Already exists: /collections complete <player> <collection>
// Add: /collections complete <player> <collection> --with-rewards
```

### Metrics/Observability: bStats

**Recommendation:** bStats for plugin metrics
**Version:** 3.1.0
**Artifact:** `org.bstats:bstats-bukkit:3.1.0`

**Rationale:**
- Industry standard for Minecraft plugin metrics (45,000+ plugins)
- Free, privacy-respecting, no external infrastructure needed
- Provides plugin usage dashboards on bstats.org
- Single server-wide request (efficient, doesn't spam)
- MIT licensed, actively maintained
- Custom charts for plugin-specific metrics (collections completed, items collected, etc.)

**Integration:**
Add to `build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks.shadowJar {
    // Add relocation alongside existing HikariCP/SQLite relocations
    relocate("org.bstats", "com.blockworlds.collections.lib.bstats")
}
```

Initialize in main plugin class:
```java
public class Collections extends JavaPlugin {
    private Metrics metrics;

    @Override
    public void onEnable() {
        // ... existing initialization ...

        // Initialize bStats (plugin ID obtained from bstats.org after registration)
        int pluginId = XXXXX; // Register plugin at bstats.org
        this.metrics = new Metrics(this, pluginId);

        // Custom charts
        metrics.addCustomChart(new SingleLineChart("total_collections",
            () -> collectionManager.getCollectionCount()));
        metrics.addCustomChart(new SimplePie("database_type",
            () -> getConfig().getString("database.type", "sqlite")));
    }
}
```

**Custom Metrics to Track:**
| Metric | Type | Value |
|--------|------|-------|
| `collection_count` | SingleLineChart | Number of configured collections |
| `zone_count` | SingleLineChart | Number of spawn zones |
| `database_type` | SimplePie | "sqlite" or "mysql" |
| `goggle_system_enabled` | SimplePie | "true" or "false" |
| `folia_mode` | SimplePie | Detects Folia vs Paper |

### Operational Logging: SLF4J (Existing)

**Recommendation:** Use existing plugin logger - no additions needed
**Pattern:** Leverage `plugin.getLogger()` with structured log formatting

**Rationale:**
- Paper provides SLF4J-backed logging through plugin.getLogger()
- Consistent with existing error handling patterns
- No additional dependencies needed
- Log levels already configured (INFO, WARNING, SEVERE)

**Integration:**
Add operational log messages for audit trail:
```java
// Export/Import operations
plugin.getLogger().info("Exported player data for " + playerId + " (" + itemCount + " items)");
plugin.getLogger().info("Imported player data for " + playerId + " (admin: " + adminName + ")");

// Admin operations
plugin.getLogger().info("Force-completed collection '" + collectionId + "' for " + playerId + " (admin: " + adminName + ")");
```

## Not Recommended

| Technology | Reason |
|------------|--------|
| Jackson | Overkill for simple data models; larger dependency; Gson already bundled |
| UnifiedMetrics | More complex setup; requires external infrastructure (InfluxDB/Prometheus); bStats sufficient for basic metrics |
| Spark | Profiling tool, not metrics collection; complementary but different purpose |
| Custom metrics server | Unnecessary complexity; bStats provides sufficient visibility |
| Shading Gson | Redundant - Paper already provides Gson at runtime; causes classloader conflicts |
| YAML export format | JSON more portable for migration between servers/plugins; Gson handles natively |
| BossBar for notifications | Too intrusive for frequent progress updates; reserve for major milestones only |

## Dependency Changes Summary

**New Dependencies (build.gradle.kts):**
```kotlin
dependencies {
    // Existing...
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.mysql:mysql-connector-j:9.1.0")

    // NEW for v1.1
    compileOnly("com.google.gson:gson:2.10.1")  // IDE support; runtime provided by Paper
    implementation("org.bstats:bstats-bukkit:3.1.0")  // Metrics collection
}

tasks.shadowJar {
    // Existing relocations...
    relocate("com.zaxxer.hikari", "com.blockworlds.collections.lib.hikari")
    relocate("org.sqlite", "com.blockworlds.collections.lib.sqlite")
    relocate("com.mysql", "com.blockworlds.collections.lib.mysql")

    // NEW for v1.1
    relocate("org.bstats", "com.blockworlds.collections.lib.bstats")
}
```

## Feature-to-Stack Mapping

| Feature | Stack Component | New? |
|---------|-----------------|------|
| Data export | Gson (compileOnly) | IDE support only |
| Data import | Gson (compileOnly) | IDE support only |
| Progress notifications | Adventure API (ActionBar/Title) | No |
| Admin force-complete | Brigadier commands | No |
| Admin force-complete with rewards | Brigadier + PlayerDataManager | No |
| Metrics collection | bStats 3.1.0 | Yes - new dependency |
| Operational logging | Plugin logger (SLF4J) | No |

## Confidence

**HIGH** - All recommendations verified:
- Gson availability confirmed via [SpigotMC wiki](https://www.spigotmc.org/wiki/included-libraries-in-spigot/) and [Paper docs](https://docs.papermc.io/adventure/serializer/gson/)
- bStats version 3.1.0 confirmed on [Maven Central](https://central.sonatype.com/artifact/org.bstats/bstats-bukkit) (released 2024-09-22)
- Adventure API capabilities verified via [PaperMC docs](https://docs.papermc.io/paper/dev/component-api/introduction/)
- Existing codebase patterns reviewed (CollectionsCommand.java, PlayerDataManager.java, ConfigManager)

## Sources

- [SpigotMC Wiki: Included Libraries](https://www.spigotmc.org/wiki/included-libraries-in-spigot/) - Confirms Gson 2.8.9 bundled
- [PaperMC Docs: Gson Serializer](https://docs.papermc.io/adventure/serializer/gson/) - Adventure Gson integration
- [PaperMC Docs: Component API](https://docs.papermc.io/paper/dev/component-api/introduction/) - Adventure API overview
- [bStats Getting Started](https://bstats.org/getting-started) - Integration guide
- [Maven Central: bstats-bukkit](https://central.sonatype.com/artifact/org.bstats/bstats-bukkit) - Version 3.1.0 confirmed
- [Baeldung: Jackson vs Gson](https://www.baeldung.com/jackson-vs-gson) - Library comparison (Gson preferred for simplicity)
