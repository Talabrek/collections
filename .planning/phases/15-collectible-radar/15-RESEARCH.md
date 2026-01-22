# Phase 15: Collectible Radar - Research

**Researched:** 2026-01-23
**Domain:** Boss bar radar display, per-player UI, real-time collectible tracking
**Confidence:** HIGH

## Summary

Phase 15 implements a radar system using Adventure API's BossBar to show nearby collectibles to players wearing collector's helmets. The radar displays collectible count and directional indicators when players have appropriate goggles equipped. The existing codebase has strong foundations: `GoggleManager` already handles helmet detection and tier visibility, `ParticleTask` demonstrates efficient chunk-based nearby collectible queries, and `ArmorChangeListener` handles helmet equip/unequip events.

The core implementation pattern involves a new `RadarTask` scheduled task that periodically updates boss bars for helmet-wearing players. Boss bars are managed per-player using Adventure's native API (`player.showBossBar()` / `player.hideBossBar()`). Direction indication uses simple left/right/ahead symbols in the boss bar title, calculated from player yaw vs. collectible position angle.

**Primary recommendation:** Create a new `RadarTask` class following the established `ParticleTask` pattern, with a `RadarManager` to track active boss bars per player. Integrate with existing `ArmorChangeListener` to show/hide radar when helmet changes.

## Standard Stack

The codebase already uses the correct stack for this feature:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Adventure BossBar API | 4.x (via Paper) | Per-player boss bar display | Native Paper integration, no external deps |
| Paper API | 1.21.4-R0.1 | Player, location, scheduling | Already in use throughout codebase |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| GoggleManager | existing | Helmet detection, tier access | Reuse for radar activation check |
| SpawnManager | existing | Nearby collectible queries | Use getCollectiblesNearChunk() |
| ConfigManager | existing | Radar settings | Add radar config section |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| BossBar | ActionBar | ActionBar already used for "right-click to collect" prompts; BossBar is persistent and separate |
| BossBar | Scoreboard | Scoreboard harder to position, BossBar more prominent for radar |
| Scheduled task | Player move event | Event-driven too frequent, scheduled task (5-10 ticks) is sufficient |

**Installation:**
No new dependencies - Adventure BossBar is included with Paper API.

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/blockworlds/collections/
├── manager/
│   └── RadarManager.java        # Boss bar lifecycle management
├── task/
│   └── RadarTask.java           # Scheduled radar updates
└── listener/
    └── ArmorChangeListener.java # Already exists - extend for radar
```

### Pattern 1: Per-Player Boss Bar Management
**What:** Track active boss bars in a Map<UUID, BossBar> for lifecycle management
**When to use:** Always - boss bars must be explicitly hidden when player removes helmet or disconnects
**Example:**
```java
// Source: Adventure API official pattern
public class RadarManager {
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    public void showRadar(Player player) {
        BossBar bar = BossBar.bossBar(
            Component.text("Radar Loading..."),
            0.0f,
            BossBar.Color.GREEN,
            BossBar.Overlay.PROGRESS
        );
        activeBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    public void hideRadar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void updateRadar(Player player, Component title, float progress, BossBar.Color color) {
        BossBar bar = activeBars.get(player.getUniqueId());
        if (bar != null) {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }
    }
}
```

### Pattern 2: Direction Calculation from Player Yaw
**What:** Calculate relative direction to collectible using player's facing direction
**When to use:** For directional indicators in radar title
**Example:**
```java
// Source: Common Minecraft compass implementation pattern
public String getDirectionIndicator(Player player, Location target) {
    Location playerLoc = player.getLocation();

    // Calculate angle to target (0 = +Z, 90 = -X, etc.)
    double dx = target.getX() - playerLoc.getX();
    double dz = target.getZ() - playerLoc.getZ();
    double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));

    // Normalize player yaw to 0-360
    float playerYaw = (playerLoc.getYaw() % 360 + 360) % 360;

    // Calculate relative angle (positive = right, negative = left)
    double relativeAngle = ((angleToTarget - playerYaw + 540) % 360) - 180;

    // Convert to indicator
    if (Math.abs(relativeAngle) < 30) {
        return "[^]";  // Ahead
    } else if (relativeAngle > 0) {
        return "[>]";  // Right
    } else {
        return "[<]";  // Left
    }
}
```

### Pattern 3: Efficient Nearby Query with Visibility Filter
**What:** Reuse SpawnManager's chunk-based query, filter by goggle tier
**When to use:** In RadarTask to find relevant collectibles
**Example:**
```java
// Source: Follows existing ParticleTask pattern
private List<Collectible> getNearbyVisibleCollectibles(Player player, int rangeBlocks) {
    Location loc = player.getLocation();
    int chunkRadius = (rangeBlocks >> 4) + 1;  // Convert blocks to chunks

    List<Collectible> nearby = spawnManager.getCollectiblesNearChunk(
        loc.getBlockX() >> 4,
        loc.getBlockZ() >> 4,
        chunkRadius
    );

    GoggleManager goggles = plugin.getGoggleManager();
    return nearby.stream()
        .filter(c -> c.spawned())
        .filter(c -> c.location().getWorld().equals(player.getWorld()))
        .filter(c -> c.location().distanceSquared(loc) <= rangeBlocks * rangeBlocks)
        .filter(c -> goggles.canPlayerSeeCollectible(player, c))
        .toList();
}
```

### Pattern 4: Scheduled Task with Folia Compatibility
**What:** Use GlobalRegionScheduler for radar updates
**When to use:** For periodic boss bar updates
**Example:**
```java
// Source: Follows existing ParticleTask pattern
public void start() {
    int intervalTicks = configManager.getRadarIntervalTicks();

    task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
        updateAllRadars();
    }, 20L, intervalTicks);
}
```

### Anti-Patterns to Avoid
- **Creating new BossBar each update:** Reuse the same BossBar instance, just update name/progress/color
- **Checking every tick:** 5-10 ticks (250-500ms) is sufficient for radar responsiveness
- **Forgetting cleanup on quit:** Must hide boss bar and remove from tracking on PlayerQuitEvent
- **Blocking main thread:** Radar calculations are simple math, no async needed

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Boss bar display | Packet manipulation | Adventure BossBar API | Native, stable, per-player |
| Helmet detection | Manual inventory check | GoggleManager.getPlayerGoggleTier() | Already implemented correctly |
| Nearby collectibles | O(n) iteration | SpawnManager.getCollectiblesNearChunk() | Chunk-indexed, O(1) per chunk |
| Direction to target | Complex trig | Simple atan2 + yaw comparison | Standard compass algorithm |

**Key insight:** The radar feature is a composition of existing patterns. Boss bar API handles display, GoggleManager handles permission, SpawnManager handles spatial queries. The new code is primarily coordination logic.

## Common Pitfalls

### Pitfall 1: Boss Bar Not Removed on Helmet Unequip
**What goes wrong:** Player removes helmet but radar persists
**Why it happens:** ArmorChangeListener only refreshes visibility, doesn't check radar
**How to avoid:** Extend ArmorChangeListener to call RadarManager.hideRadar() when goggle tier becomes null
**Warning signs:** Players see radar after removing helmet

### Pitfall 2: Boss Bar Not Removed on Player Quit
**What goes wrong:** Memory leak from orphaned boss bars
**Why it happens:** Boss bars exist independently of player connection
**How to avoid:** Clear player's boss bar in PlayerQuitEvent handler
**Warning signs:** Memory growth over time, errors on rejoin

### Pitfall 3: Radar Showing Collectibles Player Can't See
**What goes wrong:** Normal helmet radar shows RARE/EPIC/LEGENDARY collectibles
**Why it happens:** Not filtering by tier visibility when counting
**How to avoid:** Always pass through GoggleManager.canPlayerSeeCollectible() for each collectible
**Warning signs:** Radar shows count but player can't find collectibles

### Pitfall 4: Radar Updates Conflicting with Action Bar
**What goes wrong:** Boss bar and action bar fight for attention
**Why it happens:** Both updating simultaneously
**How to avoid:** Boss bar is separate from action bar - no conflict. ActionBarPromptTask shows "right-click to collect" which complements radar
**Warning signs:** None expected - separate display channels

### Pitfall 5: Direction Indicator Jumps When Facing Changes
**What goes wrong:** Direction indicator flickers between states
**Why it happens:** Boundary conditions at exactly 30 degrees
**How to avoid:** Add small hysteresis or wider "ahead" zone (30-45 degrees)
**Warning signs:** Visual jumping when slowly rotating

### Pitfall 6: Progress Bar Meaning Unclear
**What goes wrong:** Players don't understand what the bar fill represents
**Why it happens:** Unclear semantic mapping
**How to avoid:** Use progress to show nearest collectible distance (1.0 = at location, 0.0 = at max range), or simply fix at 1.0 and use color for tier
**Warning signs:** Player confusion about bar meaning

## Code Examples

### Adventure BossBar Creation and Management
```java
// Source: Adventure API / PaperMC documentation
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

// Create boss bar
BossBar radar = BossBar.bossBar(
    Component.text("3 collectibles nearby [<] [^] [>]", NamedTextColor.GOLD),
    1.0f,                        // Progress (0.0 to 1.0)
    BossBar.Color.GREEN,         // GREEN for common/uncommon visible
    BossBar.Overlay.PROGRESS     // Smooth bar (not segmented)
);

// Show to player
player.showBossBar(radar);

// Update dynamically
radar.name(Component.text("5 collectibles nearby"));
radar.progress(0.8f);
radar.color(BossBar.Color.PURPLE);  // Higher tier detected

// Hide from player
player.hideBossBar(radar);
```

### BossBar Color Mapping to Tier
```java
// Source: Application design decision
public BossBar.Color getRadarColor(CollectibleTier highestTier) {
    return switch (highestTier) {
        case COMMON -> BossBar.Color.WHITE;
        case UNCOMMON -> BossBar.Color.GREEN;
        case RARE -> BossBar.Color.BLUE;
        case EPIC -> BossBar.Color.PURPLE;
        case LEGENDARY -> BossBar.Color.YELLOW;  // No GOLD in BossBar.Color
        case EVENT -> BossBar.Color.PINK;
    };
}
```

### Radar Title Format
```java
// Source: Application design decision
public Component buildRadarTitle(int count, List<String> directions) {
    // Example: "3 nearby [<] [^] [>]"
    TextComponent.Builder builder = Component.text()
        .append(Component.text(count, NamedTextColor.GOLD))
        .append(Component.text(" nearby ", NamedTextColor.GRAY));

    for (String dir : directions) {
        builder.append(Component.text(dir + " ", NamedTextColor.AQUA));
    }

    return builder.build();
}
```

### Integration with ArmorChangeListener
```java
// Source: Extend existing pattern in ArmorChangeListener
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onArmorChange(PlayerArmorChangeEvent event) {
    if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.HEAD) return;

    Player player = event.getPlayer();

    player.getScheduler().run(plugin, task -> {
        // Existing visibility refresh
        GoggleManager goggleManager = plugin.getGoggleManager();
        if (goggleManager != null) {
            goggleManager.refreshVisibilityForPlayer(player);
        }

        // NEW: Radar show/hide
        RadarManager radarManager = plugin.getRadarManager();
        if (radarManager != null) {
            CollectibleTier goggleTier = goggleManager.getPlayerGoggleTier(player);
            if (goggleTier != null) {
                radarManager.showRadar(player, goggleTier);
            } else {
                radarManager.hideRadar(player);
            }
        }
    }, null);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Wither boss bar packets | Adventure BossBar API | Paper 1.16+ | Clean API, no packet manipulation |
| Single global boss bar | Per-player boss bars | Adventure API | Each player sees their own radar |
| Fixed title text | Dynamic Component | Adventure API | Rich formatting, real-time updates |

**Deprecated/outdated:**
- `Bukkit.createBossBar()`: Old API, prefer Adventure's `BossBar.bossBar()`
- Wither boss bar hacks: Completely obsolete since boss bar API

## Configuration Design

Recommended config.yml additions:

```yaml
radar:
  enabled: true
  range-blocks: 32              # Detection range
  update-interval-ticks: 10     # ~500ms update rate
  show-direction-indicators: true
  show-count: true
  # Colors follow BossBar.Color enum: BLUE, GREEN, PINK, PURPLE, RED, WHITE, YELLOW
```

## Open Questions

Things that couldn't be fully resolved:

1. **Direction Indicator Style**
   - What we know: Common patterns use arrows like [<] [^] [>] or unicode arrows
   - What's unclear: Exact visual style preferred
   - Recommendation: Start with simple ASCII [<] [^] [>], make configurable later

2. **Multiple Collectible Directions**
   - What we know: Could show direction to nearest, or aggregate directions
   - What's unclear: How to handle many collectibles in different directions
   - Recommendation: Show count + direction to nearest collectible only (simpler, less noise)

3. **Progress Bar Usage**
   - What we know: Progress can be 0.0-1.0
   - What's unclear: What should progress represent?
   - Recommendation: Option A: Distance to nearest (full = close). Option B: Fixed at 1.0, use color only. Recommend Option B for simplicity.

4. **Normal vs Master Helmet Range**
   - What we know: Normal sees COMMON+UNCOMMON, Master sees all tiers
   - What's unclear: Should range differ between helmet types?
   - Recommendation: Same range for both, tier visibility is the differentiator

## Sources

### Primary (HIGH confidence)
- [Adventure BossBar API Javadocs](https://jd.advntr.dev/api/4.21.0/net/kyori/adventure/bossbar/BossBar.html) - Full API reference
- [PaperMC Boss Bars Documentation](https://docs.papermc.io/adventure/bossbar/) - Integration guide
- Codebase analysis: `GoggleManager.java`, `ParticleTask.java`, `ArmorChangeListener.java`
- Codebase analysis: `SpawnManager.getCollectiblesNearChunk()` for spatial queries

### Secondary (MEDIUM confidence)
- [SimpleCompass Plugin](https://github.com/arboriginal/SimpleCompass) - Reference implementation for compass in boss bar
- [QuestPointers Plugin](https://modrinth.com/plugin/questpointers) - Direction indicator patterns
- [FEATURES.md research](file:.planning/research/FEATURES.md) - Prior boss bar research

### Tertiary (LOW confidence)
- Minecraft Wiki Locator Bar - Vanilla direction indicator (experimental, removed)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Adventure BossBar is well-documented, native to Paper
- Architecture: HIGH - Follows existing codebase patterns (ParticleTask, GoggleManager)
- Pitfalls: HIGH - Derived from API constraints and codebase analysis
- Direction algorithm: HIGH - Standard trigonometry, well-known pattern

**Research date:** 2026-01-23
**Valid until:** 90 days (stable APIs, established patterns)
