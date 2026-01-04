package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.SpawnConditions;
import com.blockworlds.collections.model.SpawnZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages spawn zones for collectibles.
 */
public class ZoneManager {

    private final Plugin plugin;
    private final File zonesFile;
    private final Map<String, SpawnZone> zones;
    private final Map<String, List<SpawnZone>> zonesByWorld;

    public ZoneManager(Plugin plugin) {
        this.plugin = plugin;
        this.zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        this.zones = new HashMap<>();
        this.zonesByWorld = new HashMap<>();
    }

    /**
     * Load all zones from zones.yml.
     */
    public void loadZones() {
        zones.clear();
        zonesByWorld.clear();

        if (!zonesFile.exists()) {
            plugin.saveResource("zones.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(zonesFile);
        ConfigurationSection zonesSection = yaml.getConfigurationSection("zones");

        if (zonesSection == null) {
            plugin.getLogger().warning("No zones defined in zones.yml");
            return;
        }

        for (String zoneId : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
            if (zoneSection != null) {
                SpawnZone zone = parseZone(zoneId, zoneSection);
                if (zone != null && zone.enabled()) {
                    zones.put(zoneId, zone);
                    zonesByWorld.computeIfAbsent(zone.worldName(), k -> new ArrayList<>()).add(zone);
                }
            }
        }

        plugin.getLogger().info("Loaded " + zones.size() + " spawn zones");
    }

    /**
     * Parse a SpawnZone from configuration.
     */
    private SpawnZone parseZone(String id, ConfigurationSection section) {
        try {
            String name = section.getString("name", id);
            boolean enabled = section.getBoolean("enabled", true);
            String worldName = section.getString("world", "world");

            // Parse bounds (optional)
            SpawnZone.Bounds bounds = null;
            ConfigurationSection boundsSection = section.getConfigurationSection("bounds");
            if (boundsSection != null) {
                bounds = new SpawnZone.Bounds(
                        boundsSection.getInt("min-x", Integer.MIN_VALUE),
                        boundsSection.getInt("max-x", Integer.MAX_VALUE),
                        boundsSection.getInt("min-z", Integer.MIN_VALUE),
                        boundsSection.getInt("max-z", Integer.MAX_VALUE)
                );
            }

            // Parse conditions
            ConfigurationSection condSection = section.getConfigurationSection("conditions");
            SpawnConditions conditions = parseSpawnConditions(condSection);

            // Parse allowed collections
            List<String> collections = section.getStringList("collections");

            // Parse limits
            int maxCollectibles = section.getInt("max-collectibles", 5);
            int respawnDelay = section.getInt("respawn-delay", 60);

            return new SpawnZone(id, name, enabled, worldName, bounds, conditions,
                    collections, maxCollectibles, respawnDelay);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse zone: " + id, e);
            return null;
        }
    }

    /**
     * Parse spawn conditions from configuration.
     * This method is public so it can be used by CollectionManager as well.
     *
     * @param section The configuration section containing conditions
     * @return Parsed SpawnConditions, or NONE if section is null
     */
    public SpawnConditions parseSpawnConditions(ConfigurationSection section) {
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
                plugin.getLogger().warning("Unknown biome: " + biomeName);
            }
        }

        // Parse dimensions
        List<String> dimensionNames = section.getStringList("dimensions");
        Set<World.Environment> dimensions = new HashSet<>();
        for (String dimName : dimensionNames) {
            try {
                dimensions.add(World.Environment.valueOf(dimName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown dimension: " + dimName);
            }
        }

        // Parse time condition
        SpawnConditions.TimeCondition time = SpawnConditions.TimeCondition.ALWAYS;
        String timeStr = section.getString("time", "ALWAYS");
        try {
            time = SpawnConditions.TimeCondition.valueOf(timeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown time condition: " + timeStr);
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

    /**
     * Get a zone by ID.
     */
    public SpawnZone getZone(String id) {
        return zones.get(id);
    }

    /**
     * Get all zones.
     */
    public Map<String, SpawnZone> getAllZones() {
        return Map.copyOf(zones);
    }

    /**
     * Get zones for a specific world.
     */
    public List<SpawnZone> getZonesForWorld(String worldName) {
        return zonesByWorld.getOrDefault(worldName, List.of());
    }

    /**
     * Get zones for a specific world.
     */
    public List<SpawnZone> getZonesForWorld(World world) {
        return getZonesForWorld(world.getName());
    }

    /**
     * Find zones that contain a location.
     */
    public List<SpawnZone> getZonesAt(Location location) {
        List<SpawnZone> result = new ArrayList<>();
        if (location.getWorld() == null) {
            return result;
        }
        String worldName = location.getWorld().getName();

        for (SpawnZone zone : getZonesForWorld(worldName)) {
            if (zone.contains(location)) {
                result.add(zone);
            }
        }

        return result;
    }

    /**
     * Find a random valid spawn location within a zone.
     *
     * @param zone   The zone to search in
     * @param center Center point to search around
     * @param radius Search radius in blocks
     * @return A valid spawn location, or null if none found
     */
    public Location findSpawnLocation(SpawnZone zone, Location center, int radius) {
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) {
            return null;
        }

        // Try up to 50 random locations
        for (int attempt = 0; attempt < 50; attempt++) {
            int offsetX = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int offsetZ = ThreadLocalRandom.current().nextInt(-radius, radius + 1);

            int x = center.getBlockX() + offsetX;
            int z = center.getBlockZ() + offsetZ;

            // Find surface Y
            Location testLoc = findSurfaceLocation(world, x, z, zone.conditions());
            if (testLoc != null && isValidSpawnLocation(zone, testLoc)) {
                return testLoc;
            }
        }

        return null;
    }

    /**
     * Find the surface location at X,Z coordinates.
     */
    private Location findSurfaceLocation(World world, int x, int z, SpawnConditions conditions) {
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
            // Search from top down
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
     * Check if a location is standable (solid block below, air at location).
     */
    private boolean isStandableLocation(Location loc) {
        if (!loc.getBlock().getType().isAir()) {
            return false;
        }
        Location below = loc.clone().subtract(0, 1, 0);
        Material blockBelow = below.getBlock().getType();
        // Barrier blocks should be treated as air (not a valid spawn surface)
        return blockBelow.isSolid() && blockBelow != Material.BARRIER;
    }

    /**
     * Check if there's a solid block above (for underground check).
     */
    private boolean hasBlockAbove(Location loc) {
        for (int y = loc.getBlockY() + 1; y < loc.getWorld().getMaxHeight(); y++) {
            Material blockType = loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            // Barrier blocks should not count as solid ceiling
            if (blockType.isSolid() && blockType != Material.BARRIER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate a spawn location meets all zone conditions.
     */
    public boolean isValidSpawnLocation(SpawnZone zone, Location location) {
        if (!zone.contains(location)) {
            return false;
        }

        // Check zone conditions
        if (!zone.conditions().check(location)) {
            return false;
        }

        // Check if location is standable
        return isStandableLocation(location);
    }

    /**
     * Check if a location satisfies the given spawn conditions.
     * Used for collection and item-level condition checking.
     *
     * @param conditions The conditions to check
     * @param location   The location to validate
     * @return true if all conditions pass
     */
    public boolean checkConditions(SpawnConditions conditions, Location location) {
        if (conditions == null) {
            return true;
        }
        return conditions.check(location);
    }

    /**
     * Get zones that allow a specific collection.
     */
    public List<SpawnZone> getZonesForCollection(String collectionId) {
        List<SpawnZone> result = new ArrayList<>();
        for (SpawnZone zone : zones.values()) {
            if (zone.collections().isEmpty() || zone.collections().contains(collectionId)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Get the number of loaded zones.
     */
    public int getZoneCount() {
        return zones.size();
    }

    /**
     * Reload zones from disk.
     */
    public void reload() {
        loadZones();
    }
}
