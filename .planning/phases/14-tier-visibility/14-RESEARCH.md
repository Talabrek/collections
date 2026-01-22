# Phase 14: Tier Visibility - Research

**Researched:** 2026-01-23
**Domain:** Per-player entity visibility, tier-based access control
**Confidence:** HIGH

## Summary

Phase 14 addresses tier visibility enforcement for collectibles. The current codebase already has a functional visibility system in `GoggleManager` that uses Paper's per-player entity visibility API (`Player.showEntity()` / `Player.hideEntity()`). However, there's a potential bug: the visibility logic in `canPlayerSeeTier()` only checks against explicit tier sets rather than implementing the progressive tier hierarchy described in the requirements (COMMON -> UNCOMMON -> RARE/EPIC/LEGENDARY).

The current implementation has **two goggle tiers** (UNCOMMON goggles showing UNCOMMON collectibles, RARE goggles showing UNCOMMON+RARE), but the requirements mention EPIC and LEGENDARY tiers which don't exist in `CollectibleTier.java`. This phase needs to:
1. Expand `CollectibleTier` enum to include EPIC and LEGENDARY
2. Update `GoggleManager.getVisibleTiers()` to implement proper tier hierarchy
3. Ensure visibility refresh works correctly on armor change events

**Primary recommendation:** Extend the tier enum and fix the visibility tier mapping in GoggleManager - no architectural changes needed, this is a data/logic bug fix.

## Standard Stack

The codebase already uses the correct stack for this feature:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Paper API | 1.21.4-R0.1 | Per-player entity visibility | Native API, no external deps |
| PlayerArmorChangeEvent | Paper | Armor change detection | Paper-specific event, reliable |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| PersistentDataContainer | Bukkit | Store goggle tier on items | Already used for goggle_tier |
| EntityScheduler | Paper | Delayed visibility refresh | Already used in ArmorChangeListener |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Per-player visibility | Global entity spawning | Per-player is correct for this use case |
| Paper armor event | InventoryClickEvent | Paper event is simpler and more reliable |

**Installation:**
Already present in the codebase.

## Architecture Patterns

### Existing Project Structure (Relevant Files)
```
src/main/java/com/blockworlds/collections/
├── model/
│   └── CollectibleTier.java     # Enum needing EPIC, LEGENDARY
├── manager/
│   └── GoggleManager.java       # Visibility logic to fix
├── listener/
│   └── ArmorChangeListener.java # Already handles helmet changes
└── task/
    ├── ParticleTask.java        # Already checks visibility
    └── ActionBarPromptTask.java # Already checks visibility
```

### Pattern 1: Tier Hierarchy with Progressive Visibility
**What:** Higher-tier goggles see all lower tiers automatically
**When to use:** Always - this is the intended design per requirements
**Example:**
```java
// Source: Existing pattern in codebase, needs extension
public Set<CollectibleTier> getVisibleTiers(CollectibleTier goggleTier) {
    return switch (goggleTier) {
        case UNCOMMON -> Set.of(CollectibleTier.UNCOMMON);  // Basic goggles
        case RARE -> Set.of(                                // Master goggles
            CollectibleTier.UNCOMMON,
            CollectibleTier.RARE,
            CollectibleTier.EPIC,      // NEW
            CollectibleTier.LEGENDARY  // NEW
        );
        default -> Set.of();
    };
}
```

### Pattern 2: Per-Player Entity Visibility
**What:** Use Paper's native `showEntity()`/`hideEntity()` for per-player visibility
**When to use:** Always for tier-gated visibility
**Example:**
```java
// Source: Already in GoggleManager.setCollectibleVisibilityForPlayer()
if (visible) {
    player.showEntity(plugin, entity);
} else {
    player.hideEntity(plugin, entity);
}
```

### Pattern 3: Immediate Visibility Refresh on Armor Change
**What:** Use `PlayerArmorChangeEvent` with one-tick delay for visibility refresh
**When to use:** When helmet is equipped/unequipped
**Example:**
```java
// Source: Already in ArmorChangeListener
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onArmorChange(PlayerArmorChangeEvent event) {
    if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.HEAD) return;

    // One-tick delay for inventory to settle
    player.getScheduler().run(plugin, task -> {
        goggleManager.refreshVisibilityForPlayer(player);
    }, null);
}
```

### Anti-Patterns to Avoid
- **Polling for helmet changes:** Use the event-driven approach already in place
- **Global entity visibility:** Must be per-player to support different goggle tiers
- **Checking visibility on every tick:** Only refresh on armor change events

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-player entity visibility | Packet manipulation | Paper's showEntity/hideEntity | Native API, maintained |
| Armor change detection | InventoryClickEvent + polling | PlayerArmorChangeEvent | Paper provides this |
| Entity persistence tracking | Custom tracking | Entity.setPersistent(false) | Built-in, reliable |

**Key insight:** The codebase already uses the correct patterns. This phase is primarily a data/logic fix, not an architectural change.

## Common Pitfalls

### Pitfall 1: Forgetting to Add New Tiers to the Enum
**What goes wrong:** Requirements mention EPIC and LEGENDARY but enum only has COMMON, UNCOMMON, RARE, EVENT
**Why it happens:** Initial implementation was simpler
**How to avoid:** Add EPIC and LEGENDARY tiers with appropriate particles and colors
**Warning signs:** Collections marked as EPIC/LEGENDARY would fall through to COMMON

### Pitfall 2: Visibility Not Updating When Chunk Loads
**What goes wrong:** Player in chunk when collectible spawns may not have correct visibility
**Why it happens:** setupInitialVisibility() must be called after spawn
**How to avoid:** Ensure `GoggleManager.setupInitialVisibility()` is called in `SpawnManager.spawnCollectible()` - already done
**Warning signs:** Players sometimes see collectibles they shouldn't

### Pitfall 3: EVENT Tier Visibility Logic Conflict
**What goes wrong:** EVENT tier has special visibility rules (visible during events OR with goggles)
**Why it happens:** EVENT is a special case in `canPlayerSeeTier()`
**How to avoid:** Keep EVENT separate from the COMMON->LEGENDARY hierarchy
**Warning signs:** EVENT collectibles visible/invisible unexpectedly

### Pitfall 4: Race Condition on Rapid Helmet Switching
**What goes wrong:** Player rapidly equipping/unequipping sees flickering
**Why it happens:** Multiple visibility refreshes queued
**How to avoid:** One-tick delay already handles this; consider debouncing if needed
**Warning signs:** Visual flickering on helmet change

### Pitfall 5: Visibility Refresh Only Checks Spawned Collectibles
**What goes wrong:** Collectibles marked as unspawned (chunk unloaded) are skipped
**Why it happens:** `refreshVisibilityForPlayer` checks `collectible.spawned()`
**How to avoid:** This is correct behavior - unspawned entities don't need visibility updates
**Warning signs:** None - this is working correctly

## Code Examples

### Current Tier Enum (Needs Extension)
```java
// Source: model/CollectibleTier.java
public enum CollectibleTier {
    COMMON(Particle.HAPPY_VILLAGER, "Common", false, NamedTextColor.WHITE),
    UNCOMMON(Particle.ENCHANT, "Uncommon", true, NamedTextColor.GREEN),
    RARE(Particle.END_ROD, "Rare", true, NamedTextColor.BLUE),
    EVENT(Particle.FIREWORK, "Event", true, NamedTextColor.LIGHT_PURPLE);
    // Missing: EPIC, LEGENDARY
}
```

### Current Visibility Check (Needs Fix)
```java
// Source: manager/GoggleManager.java - canPlayerSeeTier()
// Current logic:
public boolean canPlayerSeeTier(Player player, CollectibleTier tier) {
    if (!configManager.isGogglesEnabled()) return true;
    if (tier == CollectibleTier.COMMON) return true;
    if (player.hasPermission("collections.bypass.goggles")) return true;

    // EVENT tier special handling...

    CollectibleTier goggleTier = getPlayerGoggleTier(player);
    if (goggleTier == null) return false;

    Set<CollectibleTier> visibleTiers = getVisibleTiers(goggleTier);
    return visibleTiers.contains(tier);  // Problem: EPIC/LEGENDARY not in any set
}
```

### Per-Player Entity Visibility (Already Working)
```java
// Source: manager/GoggleManager.java
public void setCollectibleVisibilityForPlayer(Player player, Collectible collectible, boolean visible) {
    if (collectible.hitboxId() != null) {
        Entity hitbox = Bukkit.getEntity(collectible.hitboxId());
        if (hitbox != null) {
            if (visible) {
                player.showEntity(plugin, hitbox);
            } else {
                player.hideEntity(plugin, hitbox);
            }
        }
    }
}
```

### Visibility Refresh on Armor Change (Already Working)
```java
// Source: listener/ArmorChangeListener.java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onArmorChange(PlayerArmorChangeEvent event) {
    if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.HEAD) return;

    Player player = event.getPlayer();
    GoggleManager goggleManager = plugin.getGoggleManager();
    if (goggleManager == null) return;

    player.getScheduler().run(plugin, task -> {
        goggleManager.refreshVisibilityForPlayer(player);
    }, null);
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Armor stand entities | Interaction entities | v1.x | Simpler, invisible hitbox only |
| Global visibility | Per-player visibility | v1.x | Supports tier gating |
| InventoryClick polling | PlayerArmorChangeEvent | Paper 1.16+ | Event-driven, reliable |

**Deprecated/outdated:**
- None - current patterns are up to date

## Open Questions

Things that couldn't be fully resolved:

1. **EPIC and LEGENDARY Tier Design**
   - What we know: Requirements mention these tiers, but they don't exist
   - What's unclear: What particle effects and colors should EPIC/LEGENDARY use?
   - Recommendation: Add them with distinctive particles (SOUL_FIRE_FLAME for EPIC, DRAGON_BREATH for LEGENDARY?)

2. **Goggle Naming Convention**
   - What we know: Current goggles are "Collector's" and "Master Collector's"
   - What's unclear: Should there be a third tier of goggles for EPIC/LEGENDARY?
   - Recommendation: Per requirements, "upgraded collector's helmet" (Master) should see all tiers - no third tier needed

3. **Config Extensibility**
   - What we know: config.yml has tier definitions for COMMON, UNCOMMON, RARE, EVENT
   - What's unclear: Should EPIC/LEGENDARY be config-driven?
   - Recommendation: Add EPIC/LEGENDARY to config tiers section for consistency

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `model/CollectibleTier.java`, `manager/GoggleManager.java`
- Codebase analysis: `listener/ArmorChangeListener.java`
- Codebase analysis: `task/ParticleTask.java`, `task/ActionBarPromptTask.java`
- [Spigot Entity API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Entity.html) - setVisibleByDefault, isVisibleByDefault

### Secondary (MEDIUM confidence)
- [Paper Display Entities Docs](https://docs.papermc.io/paper/dev/display-entities/) - Per-player visibility patterns

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Already implemented correctly in codebase
- Architecture: HIGH - Patterns are established and working
- Pitfalls: HIGH - Derived from codebase analysis

**Research date:** 2026-01-23
**Valid until:** 60 days (stable APIs, codebase-specific)
