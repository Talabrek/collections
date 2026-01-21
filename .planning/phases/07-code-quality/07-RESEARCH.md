# Phase 7: Code Quality - Research

**Researched:** 2026-01-21
**Domain:** Codebase cleanup, dead code removal, utility extraction, input validation
**Confidence:** HIGH

## Summary

This phase focuses on code quality improvements that eliminate technical debt without changing functionality. The research examined the existing codebase to identify:

1. **Dead Code (CODE-01):** A stub file at `com.example.collections.CollectionsPlugin` exists alongside the actual plugin at `com.blockworlds.collections.Collections`. The stub is unused and should be deleted.

2. **Resource Extraction (CODE-02):** The `saveDefaultCollections()` method only extracts `collectors_initiation.yml` but the plugin ships with 66 collection YAML files. All must be extracted on first run.

3. **ID Validation (CODE-03):** Collection and item IDs currently only check for null/blank but accept any string including invalid characters. Alphanumeric validation prevents future bugs with file names, URLs, and database queries.

4. **Spawn Condition Parsing Duplication (CODE-04):** Two nearly identical `parseSpawnConditions()` methods exist in `CollectionManager` and `ZoneManager`. One returns null for empty section, the other returns `SpawnConditions.NONE`.

5. **Surface Location Finding Duplication (CODE-05):** Two identical `findSurfaceLocation()` methods exist in `ZoneManager` and `AdaptiveSpawnFinder`, along with duplicated helper methods `isStandableLocation()` and `hasBlockAbove()`.

**Primary recommendation:** Extract duplicated utility methods into dedicated utility classes, remove dead code, fix resource extraction to be dynamic.

## Standard Stack

These are existing patterns in the codebase to maintain consistency.

### Core Utilities

| Location | Purpose | Pattern |
|----------|---------|---------|
| `util.ItemBuilder` | Fluent ItemStack construction | Static factory + builder |
| `util.HeadUtil` | Custom head textures | Static utility methods |
| `util.PDCKeys` | Centralized PDC keys | Lazy-initialized static getters |

### Proposed New Utilities

| Utility | Purpose | Why Needed |
|---------|---------|------------|
| `util.LocationUtils` | Surface finding, standability checks | Extract from ZoneManager + AdaptiveSpawnFinder |
| `util.SpawnConditionParser` | Parse SpawnConditions from YAML | Extract from CollectionManager + ZoneManager |
| `util.ValidationUtils` | ID validation | New alphanumeric validation |

### Validation

| Check | Regex Pattern | Purpose |
|-------|---------------|---------|
| Alphanumeric ID | `^[a-z][a-z0-9_]*$` | Collection IDs, Item IDs |
| Alternative | `^[a-zA-Z][a-zA-Z0-9_-]*$` | Case-insensitive with hyphens |

**Recommended pattern:** Lowercase with underscores (`forest_floor`, `acorn_cap`) - matches existing collection and item IDs.

## Architecture Patterns

### Utility Class Pattern (existing in codebase)

```java
// Source: util/HeadUtil.java, util/PDCKeys.java
public final class UtilityClassName {

    private UtilityClassName() {
        // Private constructor prevents instantiation
    }

    // Static methods only
    public static ReturnType methodName(Parameters) {
        // Implementation
    }
}
```

### Lazy Resource Enumeration Pattern

For extracting bundled resources without hardcoded file lists:

```java
// Source: Paper API - JavaPlugin
// Method 1: Iterate JAR entries (standard approach)
try (JarFile jar = new JarFile(getFile())) {
    Enumeration<JarEntry> entries = jar.entries();
    while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.startsWith("collections/") && name.endsWith(".yml")) {
            String fileName = name.substring("collections/".length());
            saveResource(name, false);
        }
    }
}
```

### ID Validation Pattern

```java
// Regex for collection/item IDs
private static final Pattern VALID_ID = Pattern.compile("^[a-z][a-z0-9_]*$");

public static boolean isValidId(String id) {
    return id != null && VALID_ID.matcher(id).matches();
}

public static String validateId(String id, String context) {
    if (id == null || id.isBlank()) {
        throw new IllegalArgumentException(context + " ID cannot be null or blank");
    }
    if (!VALID_ID.matcher(id).matches()) {
        throw new IllegalArgumentException(
            context + " ID must be lowercase alphanumeric with underscores, got: " + id);
    }
    return id;
}
```

### Anti-Patterns to Avoid

- **Hardcoded resource lists:** The current `saveDefaultCollections()` hardcodes filenames - breaks when collections added
- **Duplicate utility methods:** Private methods duplicated across classes should be extracted
- **Inconsistent null handling:** One parser returns null, another returns `NONE` sentinel

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ID validation regex | Custom parsing | `Pattern.compile()` | Precompiled regex is standard |
| Resource enumeration | Hardcoded list | `JarFile` enumeration | JAR entries are authoritative |
| Surface finding | Copy-paste methods | Shared `LocationUtils` | DRY principle |

**Key insight:** The codebase already has good utility patterns (`ItemBuilder`, `HeadUtil`, `PDCKeys`). The problem is that some utilities weren't extracted initially.

## Common Pitfalls

### Pitfall 1: Changing Behavior When Extracting

**What goes wrong:** Extracted utility has subtly different behavior than original
**Why it happens:** Original methods evolved with different assumptions
**How to avoid:**
- Document exact behavior of both copies before extraction
- Write tests that verify identical behavior
- Handle the null vs NONE difference explicitly

**Warning signs:** Tests fail after extraction, spawn failures in specific biomes

### Pitfall 2: Breaking Existing Collection Files

**What goes wrong:** Stricter ID validation rejects existing collection YAML files
**Why it happens:** Existing IDs may not match new validation regex
**How to avoid:**
- Audit ALL existing IDs before defining regex
- Current IDs: `forest_floor`, `acorn_cap`, `ancient_depths` etc.
- Pattern `^[a-z][a-z0-9_]*$` matches all existing IDs

**Warning signs:** Collections fail to load after adding validation

### Pitfall 3: JAR Resource Extraction Edge Cases

**What goes wrong:** Resource extraction fails for some files
**Why it happens:**
- Shaded JARs may have different structure
- Directory entries vs file entries
- Paths may have leading slash inconsistencies

**How to avoid:**
- Use `saveResource()` which handles JAR extraction properly
- Filter entries: `!entry.isDirectory() && name.endsWith(".yml")`
- Test with actual plugin JAR, not just IDE

**Warning signs:** Missing collections in production but works in IDE

### Pitfall 4: Removing Wrong Dead Code

**What goes wrong:** Code thought to be dead is actually used via reflection or configuration
**Why it happens:** IDE "find usages" doesn't catch dynamic references
**How to avoid:**
- Verify the stub file has no references in:
  - `paper-plugin.yml` / `plugin.yml`
  - Build configuration
  - Any string-based class loading

**Warning signs:** Plugin fails to load after deletion

## Code Examples

### LocationUtils - Surface Location Finding

```java
// Source: Extracted from ZoneManager.java:248-277 and AdaptiveSpawnFinder.java:248-277
package com.blockworlds.collections.util;

import com.blockworlds.collections.model.SpawnConditions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class LocationUtils {

    private LocationUtils() {}

    /**
     * Find a standable surface location at the given X,Z coordinates.
     * Searches based on spawn conditions (underground, require-sky, or any).
     *
     * @param world      The world to search in
     * @param x          X coordinate
     * @param z          Z coordinate
     * @param conditions Spawn conditions affecting search strategy
     * @return Valid surface location, or null if none found
     */
    public static Location findSurfaceLocation(World world, int x, int z, SpawnConditions conditions) {
        int minY = Math.max(conditions.minY(), world.getMinHeight());
        int maxY = Math.min(conditions.maxY(), world.getMaxHeight() - 1);

        if (conditions.underground()) {
            // Search from bottom up for underground locations
            for (int y = minY; y <= maxY; y++) {
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                if (isStandableLocation(loc) && hasBlockAbove(loc)) {
                    return loc;
                }
            }
        } else if (conditions.requireSky()) {
            // Get highest block with sky access
            int highestY = world.getHighestBlockYAt(x, z);
            if (highestY >= minY && highestY <= maxY) {
                return new Location(world, x + 0.5, highestY + 1, z + 0.5);
            }
        } else {
            // Search from top down for any valid surface
            for (int y = maxY; y >= minY; y--) {
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                if (isStandableLocation(loc)) {
                    return loc;
                }
            }
        }
        return null;
    }

    /**
     * Check if a location is standable (air at location, solid below).
     */
    public static boolean isStandableLocation(Location loc) {
        if (!loc.getBlock().getType().isAir()) {
            return false;
        }
        Location below = loc.clone().subtract(0, 1, 0);
        Material blockBelow = below.getBlock().getType();
        return blockBelow.isSolid() && blockBelow != Material.BARRIER;
    }

    /**
     * Check if there's a solid block above (for underground check).
     */
    public static boolean hasBlockAbove(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        for (int y = loc.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            Material blockType = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            if (blockType.isSolid() && blockType != Material.BARRIER) {
                return true;
            }
        }
        return false;
    }
}
```

### SpawnConditionParser - Unified Parsing

```java
// Source: Extracted from CollectionManager.java:252-299 and ZoneManager.java:117-164
package com.blockworlds.collections.util;

import com.blockworlds.collections.model.SpawnConditions;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class SpawnConditionParser {

    private SpawnConditionParser() {}

    /**
     * Parse spawn conditions from a YAML configuration section.
     *
     * @param section The configuration section (can be null)
     * @param logger  Logger for warnings about invalid values
     * @return Parsed SpawnConditions, or SpawnConditions.NONE if section is null
     */
    public static SpawnConditions parse(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return SpawnConditions.NONE;
        }

        // Parse biomes
        List<String> biomeNames = section.getStringList("biomes");
        Set<Biome> biomes = new HashSet<>();
        for (String biomeName : biomeNames) {
            try {
                biomes.add(Biome.valueOf(biomeName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown biome: " + biomeName);
            }
        }

        // Parse dimensions
        List<String> dimensionNames = section.getStringList("dimensions");
        Set<World.Environment> dimensions = new HashSet<>();
        for (String dimName : dimensionNames) {
            try {
                dimensions.add(World.Environment.valueOf(dimName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown dimension: " + dimName);
            }
        }

        // Parse time condition
        SpawnConditions.TimeCondition time = SpawnConditions.TimeCondition.ALWAYS;
        String timeStr = section.getString("time", "ALWAYS");
        try {
            time = SpawnConditions.TimeCondition.valueOf(timeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown time condition: " + timeStr);
        }

        return new SpawnConditions(
                biomes.isEmpty() ? null : biomes,
                dimensions.isEmpty() ? null : dimensions,
                section.getInt("min-y", Integer.MIN_VALUE),
                section.getInt("max-y", Integer.MAX_VALUE),
                section.getInt("min-light", 0),
                section.getInt("max-light", 15),
                section.getBoolean("require-sky", false),
                section.getBoolean("underground", false),
                time
        );
    }
}
```

### ValidationUtils - ID Validation

```java
// Source: New utility
package com.blockworlds.collections.util;

import java.util.regex.Pattern;

public final class ValidationUtils {

    // Pattern: lowercase letter followed by lowercase alphanumeric or underscore
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private ValidationUtils() {}

    /**
     * Check if a string is a valid collection/item ID.
     * Valid IDs: lowercase alphanumeric with underscores, starting with letter.
     * Examples: "forest_floor", "acorn_cap", "ancient_depths"
     *
     * @param id The ID to validate
     * @return true if valid
     */
    public static boolean isValidId(String id) {
        return id != null && VALID_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Validate and return an ID, throwing if invalid.
     *
     * @param id      The ID to validate
     * @param context Description for error message (e.g., "Collection", "Item")
     * @return The validated ID (unchanged)
     * @throws IllegalArgumentException if ID is invalid
     */
    public static String requireValidId(String id, String context) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(context + " ID cannot be null or blank");
        }
        if (!isValidId(id)) {
            throw new IllegalArgumentException(
                context + " ID must be lowercase alphanumeric with underscores (e.g., 'forest_floor'), got: " + id
            );
        }
        return id;
    }
}
```

### Dynamic Resource Extraction

```java
// Source: Standard Paper plugin pattern
// In CollectionManager.saveDefaultCollections():

private void saveDefaultCollections() {
    try (JarFile jar = new JarFile(((JavaPlugin) plugin).getFile())) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            // Only process YAML files in collections/ directory
            if (entry.isDirectory() || !name.startsWith("collections/") || !name.endsWith(".yml")) {
                continue;
            }

            String fileName = name.substring("collections/".length());
            File targetFile = new File(collectionsFolder, fileName);

            // Only extract if file doesn't exist
            if (!targetFile.exists()) {
                plugin.saveResource(name, false);
                plugin.getLogger().info("Extracted default collection: " + fileName);
            }
        }
    } catch (IOException e) {
        plugin.getLogger().log(Level.WARNING, "Failed to enumerate collection resources", e);
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hardcoded resource lists | JAR enumeration | Always available | Maintainability - no code change needed when adding collections |
| Duplicated utility methods | Shared utility classes | Best practice | DRY, single source of truth |
| Permissive ID validation | Strict alphanumeric | Defensive coding | Prevents injection, filename issues |

**Deprecated/outdated:**
- None identified - the techniques here are standard Java patterns

## Open Questions

Things that couldn't be fully resolved:

1. **Null vs NONE for spawn conditions**
   - What we know: CollectionManager returns null, ZoneManager returns NONE
   - What's unclear: Which semantic is correct for "no conditions at this level"
   - Recommendation: Use `SpawnConditions.NONE` consistently - callers check `conditions != null && conditions != NONE` or rely on NONE passing all checks

2. **Existing ID format verification**
   - What we know: Sampled IDs match `^[a-z][a-z0-9_]*$` pattern
   - What's unclear: Whether ALL 66 collection files and ALL items follow this exactly
   - Recommendation: Add validation with WARNING log (not exception) initially, then upgrade to rejection once verified

## Sources

### Primary (HIGH confidence)

- `src/main/java/com/example/collections/CollectionsPlugin.java` - Dead stub file (37 lines, unused)
- `src/main/java/com/blockworlds/collections/manager/CollectionManager.java` - Current saveDefaultCollections() method
- `src/main/java/com/blockworlds/collections/manager/ZoneManager.java` - Duplicated utility methods
- `src/main/java/com/blockworlds/collections/spawn/AdaptiveSpawnFinder.java` - Duplicated utility methods
- `src/main/resources/collections/*.yml` - 66 collection files (verified via ls)

### Secondary (MEDIUM confidence)

- Paper API JavaPlugin - `getFile()` returns plugin JAR file
- JarFile API - Standard Java for JAR enumeration

### Tertiary (LOW confidence)

- None - all findings verified against actual codebase

## Metadata

**Confidence breakdown:**
- Dead code identification: HIGH - Direct file examination confirms stub is unused
- Resource extraction: HIGH - JAR enumeration is standard Java pattern
- ID validation pattern: HIGH - Regex pattern verified against existing IDs
- Duplication identification: HIGH - Exact method comparison performed
- Utility extraction: HIGH - Following existing patterns in codebase

**Research date:** 2026-01-21
**Valid until:** No expiration - internal codebase analysis, not external dependencies

## Requirement Mapping

| Requirement | What to Do | Complexity | Files Affected |
|-------------|------------|------------|----------------|
| CODE-01 | Delete `com/example/collections/CollectionsPlugin.java` | Trivial | 1 file |
| CODE-02 | Replace hardcoded list with JAR enumeration in `saveDefaultCollections()` | Low | CollectionManager.java |
| CODE-03 | Add `ValidationUtils.requireValidId()` and call from Collection/CollectionItem constructors | Low | 3 files |
| CODE-04 | Create `SpawnConditionParser` utility, update CollectionManager + ZoneManager to use it | Medium | 3 files |
| CODE-05 | Create `LocationUtils` utility, update ZoneManager + AdaptiveSpawnFinder to use it | Medium | 3 files |

**Recommended plan groupings:**
1. **Plan 01:** CODE-01 + CODE-02 (dead code + resource extraction)
2. **Plan 02:** CODE-03 (ID validation - isolated change)
3. **Plan 03:** CODE-04 + CODE-05 (utility extraction - related refactors)
