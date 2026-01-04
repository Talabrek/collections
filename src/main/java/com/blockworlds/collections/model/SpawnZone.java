package com.blockworlds.collections.model;

import org.bukkit.Location;

import java.util.List;

/**
 * Represents a spawn zone where collectibles can appear.
 *
 * @param id              Unique identifier for this zone
 * @param name            Display name
 * @param enabled         Whether spawning is enabled in this zone
 * @param worldName       Name of the world this zone is in (null for any world)
 * @param bounds          Coordinate bounds (null for unbounded)
 * @param conditions      Spawn conditions that must be met
 * @param collections     IDs of collections that can spawn here (empty = all)
 * @param maxCollectibles Maximum active collectibles in this zone
 * @param respawnDelay    Seconds before a new collectible spawns after collection
 */
public record SpawnZone(
        String id,
        String name,
        boolean enabled,
        String worldName,
        Bounds bounds,
        SpawnConditions conditions,
        List<String> collections,
        int maxCollectibles,
        int respawnDelay
) {
    /**
     * Create a SpawnZone with validation.
     */
    public SpawnZone {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Zone id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        if (collections == null) {
            collections = List.of();
        }
        if (maxCollectibles < 1) {
            maxCollectibles = 5;
        }
        if (respawnDelay < 0) {
            respawnDelay = 60;
        }
        if (conditions == null) {
            conditions = SpawnConditions.NONE;
        }
    }

    /**
     * Check if a location is within this zone's bounds.
     */
    public boolean contains(Location location) {
        // Check world
        if (location.getWorld() == null) {
            return false;
        }
        if (worldName != null && !worldName.equals(location.getWorld().getName())) {
            return false;
        }

        // Check bounds
        if (bounds != null) {
            return bounds.contains(location.getBlockX(), location.getBlockZ());
        }

        return true;
    }

    /**
     * Coordinate bounds for a zone.
     */
    public record Bounds(
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
}
