package com.blockworlds.collections.util;

import com.blockworlds.collections.model.SpawnConditions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Utility class for location-related operations like finding valid spawn surfaces.
 */
public final class LocationUtils {

    private LocationUtils() {
        // Private constructor prevents instantiation
    }

    /**
     * Find a standable surface location at the given X,Z coordinates.
     * Searches based on spawn conditions (underground, require-sky, or any).
     *
     * @param world      The world to search in
     * @param x          X coordinate (block)
     * @param z          Z coordinate (block)
     * @param conditions Spawn conditions affecting search strategy
     * @return Valid surface location (centered in block), or null if none found
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
     * Check if a location is standable (air at location, solid block below).
     * Barrier blocks are not considered valid spawn surfaces.
     *
     * @param loc The location to check
     * @return true if the location is standable
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
     * Check if there's a solid block above the location (for underground check).
     * Barrier blocks are not considered solid ceilings.
     *
     * @param loc The location to check from
     * @return true if there's at least one solid block above
     */
    public static boolean hasBlockAbove(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }

        for (int y = loc.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            Material blockType = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            if (blockType.isSolid() && blockType != Material.BARRIER) {
                return true;
            }
        }
        return false;
    }
}
