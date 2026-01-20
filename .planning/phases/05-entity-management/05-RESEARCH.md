# Phase 5: Entity Management - Research

**Researched:** 2026-01-21
**Domain:** Paper entity lifecycle, chunk events, entity tracking
**Confidence:** HIGH

## Summary

This phase addresses entity tracking synchronization between the plugin's in-memory `activeCollectibles` map and the actual world state. The plugin spawns Interaction entities as collectible hitboxes and tracks them by UUID. The core challenge is ensuring the tracking map stays accurate when entities are removed by various causes (chunk unload, despawn rules, admin commands, plugins).

The current implementation uses `ChunkLoadEvent`/`ChunkUnloadEvent` with a `spawned` flag on collectibles. However, entities can despawn without chunk unload (e.g., `/kill`, plugin removal, despawn distance rules), leaving orphaned tracking entries. Paper provides `EntityRemoveEvent` (since 1.20+) with detailed `Cause` enum that covers all removal scenarios including `UNLOAD`, `PLUGIN`, `DESPAWN`, and `DEATH`.

**Primary recommendation:** Add `EntityRemoveEvent` listener to catch all entity removal causes, verify tracking map entries against world entities periodically, and use entity-to-collectible index for O(1) lookups.

## Standard Stack

The established APIs for entity lifecycle management in Paper 1.21:

### Core
| API | Version | Purpose | Why Standard |
|-----|---------|---------|--------------|
| `EntityRemoveEvent` | Paper 1.20+ | Fires for ALL entity removal causes | Provides `Cause` enum distinguishing `UNLOAD`, `PLUGIN`, `DESPAWN`, etc. |
| `ChunkLoadEvent` | Bukkit | Chunk becomes loaded | Appropriate for entity recreation |
| `ChunkUnloadEvent` | Bukkit | Chunk being unloaded | Backup for entity state marking |
| `PersistentDataContainer` | Bukkit | Entity metadata storage | Data survives chunk save/load cycles |

### Supporting
| API | Version | Purpose | When to Use |
|-----|---------|---------|-------------|
| `EntitiesUnloadEvent` | Paper | Batch notification of entities being unloaded | Alternative to per-entity events when processing many |
| `EntitiesLoadEvent` | Paper | Batch notification of entities loaded with chunk | Alternative to ChunkLoadEvent for entity-focused logic |
| `RegionScheduler` | Paper | Folia-compatible delayed tasks | When deferring chunk load operations |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| EntityRemoveEvent | EntitiesUnloadEvent | Only catches chunk unloads, misses `/kill`, plugin removal |
| Per-entity tracking | Chunk-based tracking | More complex, but better for large collectible counts |
| Periodic validation | Event-driven only | Catches edge cases events miss |

## Architecture Patterns

### Recommended Tracking Structure
```
SpawnManager:
    activeCollectibles: Map<UUID, Collectible>     # collectibleId -> Collectible
    entityToCollectible: Map<UUID, UUID>           # entityId -> collectibleId (NEW)
    collectiblesByChunk: Map<Long, Set<UUID>>      # chunkKey -> collectibleIds (OPTIONAL)
```

### Pattern 1: Dual-Index Tracking
**What:** Maintain both collectible ID and entity UUID indexes
**When to use:** When frequent entity-to-collectible lookups occur (every interaction)
**Example:**
```java
// On spawn - add to both indexes
activeCollectibles.put(collectible.id(), collectible);
entityToCollectible.put(collectible.hitboxId(), collectible.id());

// On lookup - O(1) instead of O(n)
public Collectible getCollectibleByEntity(UUID entityId) {
    UUID collectibleId = entityToCollectible.get(entityId);
    return collectibleId != null ? activeCollectibles.get(collectibleId) : null;
}

// On despawn - remove from both
UUID collectibleId = entityToCollectible.remove(entityId);
if (collectibleId != null) {
    activeCollectibles.remove(collectibleId);
}
```

### Pattern 2: EntityRemoveEvent Handler
**What:** Listen for all entity removal causes and update tracking
**When to use:** Always - this catches removal causes that chunk events miss
**Example:**
```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onEntityRemove(EntityRemoveEvent event) {
    Entity entity = event.getEntity();

    // Quick check via PDC before map lookup
    if (!entity.getPersistentDataContainer().has(COLLECTIBLE_KEY)) {
        return;
    }

    UUID entityId = entity.getUniqueId();
    UUID collectibleId = entityToCollectible.remove(entityId);

    if (collectibleId != null) {
        Collectible collectible = activeCollectibles.remove(collectibleId);

        // Only persist removal if truly removed (not just unloading)
        if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD) {
            storage.removeCollectible(collectibleId);
        } else {
            // Mark unspawned, keep in tracking for chunk reload
            activeCollectibles.put(collectibleId, collectible.withSpawned(false));
        }
    }
}
```

### Pattern 3: Periodic Validation Task
**What:** Scan tracking map for orphaned entries
**When to use:** As safety net for edge cases events miss
**Example:**
```java
private void validateActiveCollectibles() {
    List<UUID> orphaned = new ArrayList<>();

    for (Collectible collectible : activeCollectibles.values()) {
        if (!collectible.spawned()) continue;

        Entity entity = Bukkit.getEntity(collectible.hitboxId());
        if (entity == null || entity.isDead()) {
            orphaned.add(collectible.id());
        }
    }

    for (UUID id : orphaned) {
        // Entity gone but we thought it was spawned - clean up
        despawnCollectible(id, true);
    }
}
```

### Anti-Patterns to Avoid
- **Relying only on ChunkUnloadEvent:** Misses plugin removal, `/kill`, despawn rules
- **Storing entity reference instead of UUID:** Entity objects become invalid after removal
- **Calling Bukkit.getEntity() in hot paths:** Expensive operation, use cached index
- **Modifying tracking in MONITOR priority handlers:** Other plugins may have already acted

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Entity removal detection | Custom tick-based checking | EntityRemoveEvent | Paper provides comprehensive event with cause |
| Chunk-entity association | Manual coordinate math | EntitiesLoadEvent/EntitiesUnloadEvent | Batch events with entity list |
| Entity validity checking | Polling entity.isValid() | EntityRemoveEvent + index | Event-driven is more reliable |
| Entity metadata storage | Plugin-side map | PersistentDataContainer | Survives save/load, entity-local |

**Key insight:** Paper's entity lifecycle events are comprehensive. The EntityRemoveEvent.Cause enum covers 13+ removal scenarios including UNLOAD, DESPAWN, PLUGIN, DEATH, TRANSFORMATION, etc. Don't try to detect all these scenarios manually.

## Common Pitfalls

### Pitfall 1: ChunkLoadEvent Fires Before Entities Available
**What goes wrong:** `chunk.getEntities()` returns empty array immediately after ChunkLoadEvent
**Why it happens:** Paper's async chunk loading populates entities after the event fires
**How to avoid:** Use `EntitiesLoadEvent` instead, or delay entity access by 1+ tick
**Warning signs:** Collectible entities "not found" immediately after chunk load

### Pitfall 2: Orphaned Tracking Entries
**What goes wrong:** `activeCollectibles` contains entries for entities that no longer exist
**Why it happens:** Entity removed by `/kill`, other plugins, or despawn rules without notification
**How to avoid:** Listen to EntityRemoveEvent AND run periodic validation
**Warning signs:** `getCollectibleByEntity()` returns null for entity that should be tracked

### Pitfall 3: Race Between Chunk Load and Entity Recreation
**What goes wrong:** Entity recreated before chunk fully loaded, fails silently
**Why it happens:** RegionScheduler task runs before chunk stability
**How to avoid:** Use EntitiesLoadEvent or delay recreation by 5+ ticks
**Warning signs:** Entities "disappear" after chunk load

### Pitfall 4: Entity UUID Changes on Recreation
**What goes wrong:** Recreated entity has new UUID, old hitboxId in Collectible is stale
**Why it happens:** Entities get new UUIDs when spawned
**How to avoid:** Update Collectible.hitboxId when recreating, update entityToCollectible index
**Warning signs:** Interactions fail with "collectible not found" for valid-looking entities

### Pitfall 5: UNLOAD Cause vs True Removal
**What goes wrong:** Collectibles removed from database when only temporarily unloaded
**Why it happens:** Not distinguishing EntityRemoveEvent.Cause.UNLOAD from other causes
**How to avoid:** Only persist deletion for non-UNLOAD causes; UNLOAD just marks spawned=false
**Warning signs:** Collectibles vanish permanently when chunks unload and reload

## Code Examples

### EntityRemoveEvent.Cause Handling
```java
// Source: Paper API 1.21.11 - EntityRemoveEvent.Cause enum
switch (event.getCause()) {
    case UNLOAD:
        // Chunk unloading - keep in tracking, mark unspawned
        markUnspawned(collectible);
        break;
    case PLUGIN:
    case DEATH:
    case DESPAWN:
    case OUT_OF_WORLD:
        // True removal - clean up fully
        despawnCollectible(collectible.id(), true);
        break;
    default:
        // Other causes (PICKUP, MERGE, etc.) - shouldn't apply to Interaction
        despawnCollectible(collectible.id(), true);
}
```

### Interaction Entity Spawn with Tracking
```java
// Spawn and register in both indexes
Interaction hitbox = world.spawn(location, Interaction.class, interaction -> {
    interaction.setInteractionWidth(1.0f);
    interaction.setInteractionHeight(1.5f);
    interaction.setPersistent(false); // Plugin manages lifecycle

    PersistentDataContainer pdc = interaction.getPersistentDataContainer();
    pdc.set(COLLECTIBLE_KEY, PersistentDataType.BOOLEAN, true);
    pdc.set(COLLECTIBLE_ID_KEY, PersistentDataType.STRING, collectibleId.toString());
});

activeCollectibles.put(collectibleId, collectible);
entityToCollectible.put(hitbox.getUniqueId(), collectibleId);
```

### Safe Entity Lookup
```java
// O(1) lookup via index
public Collectible getCollectibleByEntity(UUID entityId) {
    UUID collectibleId = entityToCollectible.get(entityId);
    if (collectibleId == null) return null;

    Collectible collectible = activeCollectibles.get(collectibleId);
    if (collectible == null) {
        // Index desync - clean up stale entry
        entityToCollectible.remove(entityId);
    }
    return collectible;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ChunkUnloadEvent only | EntityRemoveEvent + ChunkUnloadEvent | Paper 1.20 | Catches all removal causes |
| EntitiesLoadEvent/UnloadEvent optional | Still supported | Ongoing | Batch alternative available |
| Manual entity polling | Event-driven tracking | Paper 1.17+ | More reliable, less CPU |

**Deprecated/outdated:**
- Relying solely on ChunkUnloadEvent: Misses many removal scenarios
- Using `chunk.getEntities()` immediately in ChunkLoadEvent: Race condition

## Open Questions

Things that couldn't be fully resolved:

1. **Interaction entity natural despawn behavior**
   - What we know: Interaction entities are marker-type, typically don't follow mob despawn rules
   - What's unclear: Exact behavior with `setPersistent(false)` in edge cases
   - Recommendation: Keep periodic validation as safety net, monitor logs

2. **Paper 1.21+ Moonrise chunk changes**
   - What we know: Some reports of ChunkLoadEvent/UnloadEvent not firing
   - What's unclear: Whether this affects current Paper 1.21.4 stable
   - Recommendation: Rely on EntityRemoveEvent as primary, chunk events as backup

## Sources

### Primary (HIGH confidence)
- [Paper API EntityRemoveEvent.Cause](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityRemoveEvent.Cause.html) - Full cause enum documentation
- [Paper API EntityRemoveEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/entity/EntityRemoveEvent.html) - Event class documentation
- [EntitiesUnloadEvent Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/world/EntitiesUnloadEvent.html) - Batch unload event
- [PaperMC PDC Docs](https://docs.papermc.io/paper/dev/pdc/) - PersistentDataContainer guide

### Secondary (MEDIUM confidence)
- [Paper Issue #6280](https://github.com/PaperMC/Paper/issues/6280) - Entity unload event discussion, confirmed EntityRemoveFromWorldEvent works
- [Paper Issue #8448](https://github.com/PaperMC/Paper/issues/8448) - ChunkUnloadEvent entity removal issues
- [Paper World Configuration](https://docs.papermc.io/paper/reference/world-configuration/) - Despawn range configuration

### Tertiary (LOW confidence)
- Community reports of ChunkLoadEvent/UnloadEvent issues in Paper 1.21+ with Moonrise - needs validation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Paper API documentation is authoritative
- Architecture: HIGH - Patterns derived from official event documentation
- Pitfalls: MEDIUM - Some based on community issue reports

**Research date:** 2026-01-21
**Valid until:** 2026-02-21 (30 days - stable domain)
