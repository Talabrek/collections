package com.blockworlds.collections.task;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.RadarManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled task that updates radar boss bars for all players wearing collector's helmets.
 * Runs periodically to show nearby collectibles count and direction.
 */
public class RadarTask {

    private final Collections plugin;
    private final SpawnManager spawnManager;
    private final GoggleManager goggleManager;
    private final RadarManager radarManager;
    private final ConfigManager configManager;

    private ScheduledTask task;

    public RadarTask(Collections plugin, RadarManager radarManager) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.goggleManager = plugin.getGoggleManager();
        this.radarManager = radarManager;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Start the radar update task.
     */
    public void start() {
        int intervalTicks = configManager.getRadarUpdateIntervalTicks();

        // Use GlobalRegionScheduler for Folia compatibility
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            updateAllRadars();
        }, 20L, intervalTicks);

        if (configManager.isDebugMode()) {
            plugin.getLogger().info("RadarTask started with interval " + intervalTicks + " ticks");
        }
    }

    /**
     * Stop the radar update task.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;

            if (configManager.isDebugMode()) {
                plugin.getLogger().info("RadarTask stopped");
            }
        }
    }

    /**
     * Update radar displays for all players with active radars.
     */
    private void updateAllRadars() {
        int rangeBlocks = configManager.getRadarRangeBlocks();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only update players who have an active radar
            if (!radarManager.hasRadar(player)) {
                continue;
            }

            // Get nearby visible collectibles
            List<Collectible> nearby = getNearbyVisibleCollectibles(player, rangeBlocks);

            // Calculate display info
            int count = nearby.size();
            String directionIndicator = "";
            CollectibleTier highestTier = null;

            if (!nearby.isEmpty()) {
                // Find nearest collectible for direction
                Collectible nearest = findNearest(player.getLocation(), nearby);
                directionIndicator = getDirectionIndicator(player, nearest.location());

                // Find highest tier among visible collectibles
                highestTier = findHighestTier(nearby);
            }

            // Update the radar display
            radarManager.updateRadar(player, count, directionIndicator, highestTier);
        }
    }

    /**
     * Get nearby collectibles that the player can see based on goggles.
     *
     * @param player     The player
     * @param rangeBlocks Detection range in blocks
     * @return List of visible collectibles within range
     */
    private List<Collectible> getNearbyVisibleCollectibles(Player player, int rangeBlocks) {
        List<Collectible> result = new ArrayList<>();

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) {
            return result;
        }

        // Calculate chunk search radius
        int chunkRadius = (rangeBlocks >> 4) + 1;
        int playerChunkX = playerLoc.getBlockX() >> 4;
        int playerChunkZ = playerLoc.getBlockZ() >> 4;

        // Get collectibles from nearby chunks
        List<Collectible> candidates = spawnManager.getCollectiblesNearChunk(
                playerChunkX, playerChunkZ, chunkRadius);

        double rangeSquared = rangeBlocks * rangeBlocks;

        for (Collectible collectible : candidates) {
            // Skip if different world
            if (!world.equals(collectible.location().getWorld())) {
                continue;
            }

            // Skip if out of range
            if (playerLoc.distanceSquared(collectible.location()) > rangeSquared) {
                continue;
            }

            // Skip if player can't see this collectible (goggle check)
            if (!goggleManager.canPlayerSeeCollectible(player, collectible)) {
                continue;
            }

            result.add(collectible);
        }

        return result;
    }

    /**
     * Find the nearest collectible to a location.
     *
     * @param from        The reference location
     * @param collectibles List of collectibles to search
     * @return The nearest collectible
     */
    private Collectible findNearest(Location from, List<Collectible> collectibles) {
        Collectible nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Collectible c : collectibles) {
            double distSq = from.distanceSquared(c.location());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = c;
            }
        }

        return nearest;
    }

    /**
     * Find the highest tier among a list of collectibles.
     *
     * @param collectibles List of collectibles
     * @return The highest tier
     */
    private CollectibleTier findHighestTier(List<Collectible> collectibles) {
        CollectibleTier highest = CollectibleTier.COMMON;

        for (Collectible c : collectibles) {
            if (c.tier().ordinal() > highest.ordinal()) {
                highest = c.tier();
            }
        }

        return highest;
    }

    /**
     * Calculate direction indicator based on player facing direction and target location.
     * Returns "[^]" for ahead, "[<]" for left, "[>]" for right.
     *
     * @param player The player
     * @param target The target location
     * @return Direction indicator string
     */
    private String getDirectionIndicator(Player player, Location target) {
        Location playerLoc = player.getLocation();

        // Calculate angle to target
        double dx = target.getX() - playerLoc.getX();
        double dz = target.getZ() - playerLoc.getZ();
        double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));

        // Normalize player yaw to 0-360
        float playerYaw = playerLoc.getYaw();
        playerYaw = ((playerYaw % 360) + 360) % 360;

        // Calculate relative angle (-180 to 180, 0 = directly ahead)
        double relativeAngle = ((angleToTarget - playerYaw + 540) % 360) - 180;

        // Return direction indicator
        if (Math.abs(relativeAngle) < 45) {
            return "[^]"; // Ahead
        } else if (relativeAngle > 0) {
            return "[<]"; // Left
        } else {
            return "[>]"; // Right
        }
    }
}
