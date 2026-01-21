package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.SpawnConditions;
import com.blockworlds.collections.model.SpawnZone;
import com.blockworlds.collections.util.LocationUtils;
import com.blockworlds.collections.util.SpawnConditionParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
     *
     * @param section The configuration section containing conditions
     * @return Parsed SpawnConditions, or NONE if section is null
     * @deprecated Use {@link SpawnConditionParser#parse(ConfigurationSection, java.util.logging.Logger)} directly
     */
    @Deprecated
    public SpawnConditions parseSpawnConditions(ConfigurationSection section) {
        return SpawnConditionParser.parse(section, plugin.getLogger());
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
        return LocationUtils.findSurfaceLocation(world, x, z, conditions);
    }

    /**
     * Check if a location is standable (solid block below, air at location).
     */
    private boolean isStandableLocation(Location loc) {
        return LocationUtils.isStandableLocation(loc);
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
