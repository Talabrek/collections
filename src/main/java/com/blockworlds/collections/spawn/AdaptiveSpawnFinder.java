package com.blockworlds.collections.spawn;

import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.SpawnConditions;
import com.blockworlds.collections.model.SpawnZone;
import com.blockworlds.collections.util.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Adaptive spawn location finder using grid-based search with expanding radius.
 * Replaces random sampling with a systematic approach that guarantees finding
 * valid locations when they exist.
 */
public class AdaptiveSpawnFinder {

    private final Plugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;

    // Default configuration values
    private static final int DEFAULT_GRID_SPACING = 8;
    private static final int DEFAULT_INITIAL_RADIUS = 32;
    private static final int DEFAULT_MAX_RADIUS = 128;
    private static final int DEFAULT_MAX_ATTEMPTS_PER_PASS = 200;
    private static final boolean DEFAULT_ALLOW_RELAXATION = true;

    public AdaptiveSpawnFinder(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
    }

    /**
     * Find a valid spawn location using adaptive grid-based search.
     *
     * @param zone The zone to search in
     * @return SpawnResult with location or failure statistics
     */
    public SpawnResult findLocation(SpawnZone zone) {
        return findLocation(zone, zone.conditions(), getAllowConditionRelaxation());
    }

    /**
     * Find a valid spawn location with specific conditions.
     *
     * @param zone       The zone to search in
     * @param conditions The spawn conditions to check
     * @param allowRelax Whether to try relaxed conditions on failure
     * @return SpawnResult with location or failure statistics
     */
    public SpawnResult findLocation(SpawnZone zone, SpawnConditions conditions, boolean allowRelax) {
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) {
            SpawnFailureStats stats = new SpawnFailureStats();
            stats.recordFailure("world-not-loaded");
            return SpawnResult.failure(stats);
        }

        SpawnFailureStats stats = new SpawnFailureStats();
        Location center = calculateZoneCenter(zone, world);
        int radius = getInitialRadius();
        int maxRadius = getMaxRadius();
        int gridSpacing = getGridSpacing();
        int maxPerPass = getMaxAttemptsPerPass();

        // Constrain search radius to zone bounds if specified
        if (zone.bounds() != null) {
            SpawnZone.Bounds bounds = zone.bounds();
            int zoneWidth = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ());
            maxRadius = Math.min(maxRadius, zoneWidth / 2 + gridSpacing);
        }

        boolean debug = configManager.isDebugMode();

        while (radius <= maxRadius) {
            // Use lazy iterator with reservoir sampling instead of pre-allocating full List
            GridPointIterator iterator = new GridPointIterator(
                    center.getBlockX(), center.getBlockZ(), radius, gridSpacing, zone);

            // Reservoir sample to get random subset
            List<int[]> sampledPoints = reservoirSample(iterator, maxPerPass, ThreadLocalRandom.current());

            if (debug) {
                logger.info("Spawn search: radius=" + radius + ", sampled=" + sampledPoints.size());
            }

            for (int[] point : sampledPoints) {
                int x = point[0];
                int z = point[1];

                // Find standable surface at this X,Z
                Location surfaceLoc = findSurfaceLocation(world, x, z, conditions);
                if (surfaceLoc == null) {
                    stats.recordFailure("no-surface");
                    continue;
                }

                // Check zone bounds
                if (!zone.contains(surfaceLoc)) {
                    stats.recordFailure("out-of-bounds");
                    continue;
                }

                // Check spawn conditions with detailed tracking
                String failReason = checkConditionsDetailed(surfaceLoc, conditions);
                if (failReason != null) {
                    stats.recordFailure(failReason);
                    continue;
                }

                // Found valid location!
                stats.recordSuccess();
                if (debug) {
                    logger.info("Spawn found at " + formatLocation(surfaceLoc) + " after " + stats.getTotalAttempts() + " attempts");
                }
                return SpawnResult.success(surfaceLoc, stats);
            }

            // Expand search radius
            radius *= 2;
        }

        // Try with relaxed conditions if allowed
        if (allowRelax && conditions.requireSky()) {
            if (debug) {
                logger.info("Retrying spawn search with relaxed sky-access requirement");
            }
            SpawnConditions relaxed = relaxConditions(conditions);
            SpawnResult relaxedResult = findLocation(zone, relaxed, false);
            if (relaxedResult.success()) {
                return SpawnResult.successRelaxed(relaxedResult.location(), stats);
            }
        }

        if (debug) {
            logger.warning("Spawn search failed after " + stats.getTotalAttempts() + " attempts: " + stats.getSummary());
        }

        return SpawnResult.failure(stats);
    }

    /**
     * Calculate the center point of a zone.
     */
    private Location calculateZoneCenter(SpawnZone zone, World world) {
        if (zone.bounds() != null) {
            SpawnZone.Bounds bounds = zone.bounds();
            int centerX = (bounds.minX() + bounds.maxX()) / 2;
            int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
            return new Location(world, centerX, 64, centerZ);
        }

        // No bounds - use world spawn or find from online players
        List<Location> playerLocs = new ArrayList<>();
        for (var player : world.getPlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                playerLocs.add(player.getLocation());
            }
        }

        if (!playerLocs.isEmpty()) {
            return playerLocs.get(0);
        }

        return world.getSpawnLocation();
    }

    /**
     * Reservoir sampling: select k random elements from an iterator without knowing size upfront.
     * This provides randomness without materializing the full iterator into a List.
     *
     * @param iterator Source iterator
     * @param k Maximum number of elements to select
     * @param random Random source
     * @return List of randomly selected elements (up to k)
     */
    private List<int[]> reservoirSample(Iterator<int[]> iterator, int k, java.util.Random random) {
        List<int[]> reservoir = new ArrayList<>(k);
        int count = 0;

        while (iterator.hasNext()) {
            int[] item = iterator.next();
            count++;

            if (reservoir.size() < k) {
                // Fill reservoir
                reservoir.add(item);
            } else {
                // Randomly replace elements with decreasing probability
                int j = random.nextInt(count);
                if (j < k) {
                    reservoir.set(j, item);
                }
            }
        }

        // Shuffle the reservoir for additional randomness in iteration order
        Collections.shuffle(reservoir, random);
        return reservoir;
    }

    /**
     * Generate grid points within the specified radius.
     * @deprecated Use {@link GridPointIterator} with {@link #reservoirSample} instead for reduced memory allocation.
     */
    @Deprecated
    private List<Location> generateGridPoints(Location center, int radius, int spacing, SpawnZone zone) {
        List<Location> points = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) return points;

        int minX = center.getBlockX() - radius;
        int maxX = center.getBlockX() + radius;
        int minZ = center.getBlockZ() - radius;
        int maxZ = center.getBlockZ() + radius;

        // Constrain to zone bounds if specified
        if (zone.bounds() != null) {
            SpawnZone.Bounds bounds = zone.bounds();
            minX = Math.max(minX, bounds.minX());
            maxX = Math.min(maxX, bounds.maxX());
            minZ = Math.max(minZ, bounds.minZ());
            maxZ = Math.min(maxZ, bounds.maxZ());
        }

        for (int x = minX; x <= maxX; x += spacing) {
            for (int z = minZ; z <= maxZ; z += spacing) {
                points.add(new Location(world, x + 0.5, 64, z + 0.5));
            }
        }

        return points;
    }

    /**
     * Find the surface location at X,Z coordinates.
     */
    private Location findSurfaceLocation(World world, int x, int z, SpawnConditions conditions) {
        return LocationUtils.findSurfaceLocation(world, x, z, conditions);
    }

    /**
     * Check if a location is standable (solid block below, air at location).
     */
    private boolean isStandableLocation(Location loc) {
        return LocationUtils.isStandableLocation(loc);
    }

    /**
     * Check if there's a solid block above (for underground check).
     */
    private boolean hasBlockAbove(Location loc) {
        return LocationUtils.hasBlockAbove(loc);
    }

    /**
     * Check spawn conditions and return the first failure reason, or null if all pass.
     */
    private String checkConditionsDetailed(Location location, SpawnConditions conditions) {
        World world = location.getWorld();
        if (world == null) {
            return "world";
        }

        // Check dimension
        if (conditions.dimensions() != null && !conditions.dimensions().contains(world.getEnvironment())) {
            return "dimension";
        }

        // Check Y level
        int y = location.getBlockY();
        if (!conditions.isYValid(y)) {
            return "y-level";
        }

        // Check biome
        if (conditions.biomes() != null) {
            if (!conditions.biomes().contains(world.getBiome(location))) {
                return "biome";
            }
        }

        // Check light level
        int lightLevel = location.getBlock().getLightLevel();
        if (!conditions.isLightValid(lightLevel)) {
            return "light";
        }

        // Check sky access
        if (conditions.requireSky()) {
            int highestY = world.getHighestBlockYAt(location);
            if (location.getBlockY() <= highestY) {
                return "sky-access";
            }
        }

        // Check underground (has solid blocks above)
        if (conditions.underground()) {
            if (!hasBlockAbove(location)) {
                return "underground";
            }
        }

        // Check time of day
        if (conditions.time() != SpawnConditions.TimeCondition.ALWAYS) {
            long worldTime = world.getTime() % 24000;
            boolean isDay = worldTime >= 0 && worldTime < 12000;
            if (conditions.time() == SpawnConditions.TimeCondition.DAY && !isDay) {
                return "time-day";
            }
            if (conditions.time() == SpawnConditions.TimeCondition.NIGHT && isDay) {
                return "time-night";
            }
        }

        return null; // All conditions passed
    }

    /**
     * Create relaxed spawn conditions (disable sky-access requirement).
     */
    private SpawnConditions relaxConditions(SpawnConditions conditions) {
        return new SpawnConditions(
                conditions.biomes(),
                conditions.dimensions(),
                conditions.minY(),
                conditions.maxY(),
                conditions.minLight(),
                conditions.maxLight(),
                false,  // Relax sky-access
                conditions.underground(),
                conditions.time()
        );
    }

    /**
     * Format a location for logging.
     */
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // Configuration getters with defaults

    private int getGridSpacing() {
        return configManager.getInt("spawn.grid-spacing", DEFAULT_GRID_SPACING);
    }

    private int getInitialRadius() {
        return configManager.getInt("spawn.initial-radius", DEFAULT_INITIAL_RADIUS);
    }

    private int getMaxRadius() {
        return configManager.getInt("spawn.max-radius", DEFAULT_MAX_RADIUS);
    }

    private int getMaxAttemptsPerPass() {
        return configManager.getInt("spawn.max-attempts-per-pass", DEFAULT_MAX_ATTEMPTS_PER_PASS);
    }

    private boolean getAllowConditionRelaxation() {
        return configManager.getBoolean("spawn.allow-condition-relaxation", DEFAULT_ALLOW_RELAXATION);
    }

    /**
     * Lazy iterator over grid points - generates X,Z coordinates on demand.
     * Avoids allocating thousands of Location objects upfront.
     */
    private static class GridPointIterator implements Iterator<int[]> {
        private final int minX, maxX, minZ, maxZ, spacing;
        private int currentX, currentZ;
        private boolean hasNext;

        GridPointIterator(int centerX, int centerZ, int radius, int spacing, SpawnZone zone) {
            this.spacing = spacing;

            // Calculate bounds
            int calcMinX = centerX - radius;
            int calcMaxX = centerX + radius;
            int calcMinZ = centerZ - radius;
            int calcMaxZ = centerZ + radius;

            // Constrain to zone bounds if specified
            if (zone.bounds() != null) {
                SpawnZone.Bounds bounds = zone.bounds();
                calcMinX = Math.max(calcMinX, bounds.minX());
                calcMaxX = Math.min(calcMaxX, bounds.maxX());
                calcMinZ = Math.max(calcMinZ, bounds.minZ());
                calcMaxZ = Math.min(calcMaxZ, bounds.maxZ());
            }

            this.minX = calcMinX;
            this.maxX = calcMaxX;
            this.minZ = calcMinZ;
            this.maxZ = calcMaxZ;

            this.currentX = this.minX;
            this.currentZ = this.minZ;
            this.hasNext = currentX <= maxX && currentZ <= maxZ;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public int[] next() {
            if (!hasNext) {
                throw new java.util.NoSuchElementException();
            }

            int[] result = new int[] { currentX, currentZ };

            // Advance to next point
            currentX += spacing;
            if (currentX > maxX) {
                currentX = minX;
                currentZ += spacing;
            }
            hasNext = currentZ <= maxZ;

            return result;
        }

        /**
         * Get the total number of points this iterator will produce.
         * Used for reservoir sampling.
         */
        int totalPoints() {
            int countX = (maxX - minX) / spacing + 1;
            int countZ = (maxZ - minZ) / spacing + 1;
            return Math.max(0, countX * countZ);
        }
    }
}
