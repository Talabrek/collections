package com.blockworlds.collections.model;

import org.bukkit.Material;

import java.util.List;

/**
 * Represents a single item within a collection.
 *
 * @param id              Unique identifier for this item within the collection
 * @param name            Display name for the item
 * @param material        Minecraft material to use for the item icon
 * @param lore            Description lines shown on the item
 * @param weight          Relative spawn weight (higher = more common, 0 = no natural spawning)
 * @param soulbound       Whether this item is bound on pickup (non-tradeable)
 * @param spawnConditions Spawn conditions for this specific item (overrides collection conditions)
 * @param dropSources     Alternative drop sources (mob kills, block breaks, fishing, loot)
 */
public record CollectionItem(
        String id,
        String name,
        Material material,
        List<String> lore,
        int weight,
        boolean soulbound,
        SpawnConditions spawnConditions,
        DropSources dropSources
) {
    /**
     * Create a CollectionItem with validation.
     */
    public CollectionItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank");
        }
        if (material == null) {
            material = Material.PAPER;
        }
        if (lore == null) {
            lore = List.of();
        }
        // Weight can be 0 if item only comes from drop sources (no natural spawning)
        if (weight < 0) {
            weight = 0;
        }
        // spawnConditions can be null (no item-level restrictions, uses collection conditions)
        // dropSources can be null (no alternative drop sources)
        if (dropSources == null) {
            dropSources = DropSources.EMPTY;
        }
    }

    /**
     * Check if this item has any drop sources configured.
     */
    public boolean hasDropSources() {
        return dropSources != null && dropSources.hasAnySource();
    }

    /**
     * Check if this item participates in natural spawning.
     * Items with weight 0 only drop from alternative sources.
     */
    public boolean hasNaturalSpawning() {
        return weight > 0;
    }

    /**
     * Create a simple item with defaults.
     */
    public static CollectionItem simple(String id, String name, Material material) {
        return new CollectionItem(id, name, material, List.of(), 10, false, null, null);
    }
}
