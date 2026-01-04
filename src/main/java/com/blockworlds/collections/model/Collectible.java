package com.blockworlds.collections.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Represents an active collectible spawned in the world.
 *
 * @param id             Unique identifier for this collectible instance
 * @param hitboxId       UUID of the interaction entity (hitbox)
 * @param zoneId         ID of the zone this collectible spawned in
 * @param collectionId   ID of the collection this collectible belongs to
 * @param itemId         ID of the pre-selected item (selected based on spawn conditions)
 * @param location       World location of the collectible
 * @param tier           Visual tier of this collectible
 * @param spawnedAt      Timestamp when this collectible was spawned
 * @param spawned        Whether the entities are currently spawned in the world
 */
public record Collectible(
        UUID id,
        UUID hitboxId,
        String zoneId,
        String collectionId,
        String itemId,
        Location location,
        CollectibleTier tier,
        long spawnedAt,
        boolean spawned
) {
    /**
     * Create a new Collectible with a generated ID.
     */
    public static Collectible create(
            UUID hitboxId,
            String zoneId,
            String collectionId,
            String itemId,
            Location location,
            CollectibleTier tier
    ) {
        return new Collectible(
                UUID.randomUUID(),
                hitboxId,
                zoneId,
                collectionId,
                itemId,
                location.clone(),
                tier,
                System.currentTimeMillis(),
                true
        );
    }

    /**
     * Create a copy with spawned state changed.
     */
    public Collectible withSpawned(boolean spawned) {
        return new Collectible(id, hitboxId, zoneId, collectionId, itemId, location, tier, spawnedAt, spawned);
    }

    /**
     * Create a copy with new hitbox UUID (for respawning after chunk load).
     */
    public Collectible withHitbox(UUID hitboxId) {
        return new Collectible(id, hitboxId, zoneId, collectionId, itemId, location, tier, spawnedAt, true);
    }

    /**
     * Get the world name.
     */
    public String getWorldName() {
        return location.getWorld() != null ? location.getWorld().getName() : null;
    }

    /**
     * Get the chunk X coordinate.
     */
    public int getChunkX() {
        return location.getBlockX() >> 4;
    }

    /**
     * Get the chunk Z coordinate.
     */
    public int getChunkZ() {
        return location.getBlockZ() >> 4;
    }
}
