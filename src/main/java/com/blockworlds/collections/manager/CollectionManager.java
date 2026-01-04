package com.blockworlds.collections.manager;

import com.blockworlds.collections.model.BlockDropSource;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.model.DropSources;
import com.blockworlds.collections.model.FishingDropSource;
import com.blockworlds.collections.model.LootDropSource;
import com.blockworlds.collections.model.MobDropSource;
import com.blockworlds.collections.model.SpawnConditions;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages loading and accessing collection definitions from YAML files.
 */
public class CollectionManager {

    private final Plugin plugin;
    private final File collectionsFolder;
    private final Map<String, Collection> collections;

    public CollectionManager(Plugin plugin) {
        this.plugin = plugin;
        this.collectionsFolder = new File(plugin.getDataFolder(), "collections");
        this.collections = new ConcurrentHashMap<>();
    }

    /**
     * Load all collections from the collections folder.
     */
    public void loadCollections() {
        collections.clear();

        // Create folder if it doesn't exist
        if (!collectionsFolder.exists()) {
            if (!collectionsFolder.mkdirs()) {
                plugin.getLogger().warning("Failed to create collections folder");
                return;
            }
        }

        // Save default collections if folder is empty
        saveDefaultCollections();

        // Load all YAML files
        File[] files = collectionsFolder.listFiles((dir, name) ->
                name.endsWith(".yml") || name.endsWith(".yaml"));

        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No collection files found in collections folder");
            return;
        }

        for (File file : files) {
            loadCollectionFile(file);
        }

        plugin.getLogger().info("Loaded " + collections.size() + " collections");
    }

    /**
     * Save default collection files if they don't exist.
     */
    private void saveDefaultCollections() {
        String[] defaults = {"collectors_initiation.yml"};

        for (String fileName : defaults) {
            File file = new File(collectionsFolder, fileName);
            if (!file.exists()) {
                try (InputStream in = plugin.getResource("collections/" + fileName)) {
                    if (in != null) {
                        try (OutputStream out = Files.newOutputStream(file.toPath())) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                        }
                        plugin.getLogger().info("Saved default collection: " + fileName);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to save default collection: " + fileName, e);
                }
            }
        }
    }

    /**
     * Load a single collection file.
     *
     * @param file The YAML file to load
     */
    private void loadCollectionFile(File file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = yaml.getString("id");

            if (id == null || id.isBlank()) {
                plugin.getLogger().warning("Collection file missing id: " + file.getName());
                return;
            }

            Collection collection = parseCollection(id, yaml);
            if (collection != null) {
                collections.put(id, collection);
                plugin.getLogger().info("Loaded collection: " + id +
                        " (" + collection.items().size() + " items)");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load collection file: " + file.getName(), e);
        }
    }

    /**
     * Parse a Collection from YAML configuration.
     *
     * @param id   The collection ID
     * @param yaml The YAML configuration
     * @return The parsed Collection, or null if invalid
     */
    private Collection parseCollection(String id, YamlConfiguration yaml) {
        String name = yaml.getString("name", id);
        String description = yaml.getString("description", "");

        // Parse tier
        String tierStr = yaml.getString("tier", "COMMON").toUpperCase();
        CollectibleTier tier;
        try {
            tier = CollectibleTier.valueOf(tierStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid tier for collection " + id + ": " + tierStr);
            tier = CollectibleTier.COMMON;
        }

        // Parse items
        List<CollectionItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection != null) {
                    CollectionItem item = parseItem(itemId, itemSection);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        if (items.isEmpty()) {
            plugin.getLogger().warning("Collection " + id + " has no valid items");
            return null;
        }

        // Parse rewards
        Collection.CollectionRewards rewards = parseRewards(yaml.getConfigurationSection("rewards"));

        // Parse meta-collection requirements
        List<String> requiredCollections = yaml.getStringList("requires");

        // Parse zones
        List<String> allowedZones = yaml.getStringList("zones");

        // Parse head texture
        String headTexture = yaml.getString("head-texture", "");

        // Parse icon
        String iconStr = yaml.getString("icon", "PAPER");
        Material icon;
        try {
            icon = Material.valueOf(iconStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid icon material for collection " + id + ": " + iconStr);
            icon = Material.PAPER;
        }

        // Parse spawn conditions (collection-level)
        SpawnConditions spawnConditions = parseSpawnConditions(yaml.getConfigurationSection("conditions"));

        return new Collection(
                id,
                name,
                description,
                tier,
                items,
                rewards,
                requiredCollections,
                allowedZones,
                headTexture,
                icon,
                spawnConditions
        );
    }

    /**
     * Parse a CollectionItem from YAML configuration.
     *
     * @param id      The item ID
     * @param section The YAML section
     * @return The parsed item, or null if invalid
     */
    private CollectionItem parseItem(String id, ConfigurationSection section) {
        String name = section.getString("name", id);

        String materialStr = section.getString("material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material for item " + id + ": " + materialStr);
            material = Material.PAPER;
        }

        List<String> lore = section.getStringList("lore");
        int weight = section.getInt("weight", 10);
        boolean soulbound = section.getBoolean("soulbound", true);

        // Parse spawn conditions (item-level)
        SpawnConditions spawnConditions = parseSpawnConditions(section.getConfigurationSection("conditions"));

        // Parse drop sources (alternative drop methods)
        DropSources dropSources = parseDropSources(section.getConfigurationSection("drop-sources"));

        return new CollectionItem(id, name, material, lore, weight, soulbound, spawnConditions, dropSources);
    }

    /**
     * Parse spawn conditions from configuration.
     *
     * @param section The configuration section containing conditions
     * @return Parsed SpawnConditions, or null if section is null (no restrictions)
     */
    private SpawnConditions parseSpawnConditions(ConfigurationSection section) {
        if (section == null) {
            return null; // No conditions specified = no restrictions at this level
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
     * Parse drop sources from configuration.
     *
     * @param section The drop-sources configuration section
     * @return Parsed DropSources, or EMPTY if section is null
     */
    private DropSources parseDropSources(ConfigurationSection section) {
        if (section == null) {
            return DropSources.EMPTY;
        }

        List<MobDropSource> mobs = parseMobDropSources(section.getMapList("mobs"));
        List<BlockDropSource> blocks = parseBlockDropSources(section.getMapList("blocks"));
        List<FishingDropSource> fishing = parseFishingDropSources(section.getMapList("fishing"));
        List<LootDropSource> loot = parseLootDropSources(section.getMapList("loot"));

        return new DropSources(mobs, blocks, fishing, loot);
    }

    /**
     * Parse mob drop sources from YAML list.
     */
    @SuppressWarnings("unchecked")
    private List<MobDropSource> parseMobDropSources(List<Map<?, ?>> sourceList) {
        List<MobDropSource> sources = new ArrayList<>();

        for (Map<?, ?> sourceMap : sourceList) {
            try {
                // Parse entity types
                Set<EntityType> entities = new HashSet<>();
                Object entitiesObj = sourceMap.get("entities");
                if (entitiesObj instanceof List<?> entityList) {
                    for (Object entity : entityList) {
                        try {
                            entities.add(EntityType.valueOf(entity.toString().toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown entity type: " + entity);
                        }
                    }
                }

                if (entities.isEmpty()) continue;

                // Parse chance
                double chance = parseDouble(sourceMap.get("chance"), 0.01);

                // Parse player kill required
                boolean playerKillRequired = parseBoolean(sourceMap.get("player-kill-required"), true);

                // Parse conditions
                SpawnConditions conditions = parseConditionsFromMap(
                        (Map<String, Object>) sourceMap.get("conditions"));

                // Parse enchant bonus
                Map<String, Double> enchantBonus = parseEnchantBonus(
                        (Map<?, ?>) sourceMap.get("enchant-bonus"));

                sources.add(new MobDropSource(entities, chance, playerKillRequired, conditions, enchantBonus));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse mob drop source: " + e.getMessage());
            }
        }

        return sources;
    }

    /**
     * Parse block drop sources from YAML list.
     */
    @SuppressWarnings("unchecked")
    private List<BlockDropSource> parseBlockDropSources(List<Map<?, ?>> sourceList) {
        List<BlockDropSource> sources = new ArrayList<>();

        for (Map<?, ?> sourceMap : sourceList) {
            try {
                // Parse block types
                Set<Material> blockTypes = new HashSet<>();
                Object typesObj = sourceMap.get("types");
                if (typesObj instanceof List<?> typeList) {
                    for (Object type : typeList) {
                        try {
                            Material mat = Material.valueOf(type.toString().toUpperCase());
                            if (mat.isBlock()) {
                                blockTypes.add(mat);
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Unknown block type: " + type);
                        }
                    }
                }

                if (blockTypes.isEmpty()) continue;

                // Parse chance
                double chance = parseDouble(sourceMap.get("chance"), 0.01);

                // Parse tool required
                boolean toolRequired = parseBoolean(sourceMap.get("tool-required"), false);

                // Parse silk touch disabled
                boolean silkTouchDisabled = parseBoolean(sourceMap.get("silk-touch-disabled"), false);

                // Parse conditions
                SpawnConditions conditions = parseConditionsFromMap(
                        (Map<String, Object>) sourceMap.get("conditions"));

                // Parse enchant bonus
                Map<String, Double> enchantBonus = parseEnchantBonus(
                        (Map<?, ?>) sourceMap.get("enchant-bonus"));

                sources.add(new BlockDropSource(blockTypes, chance, toolRequired, silkTouchDisabled,
                        conditions, enchantBonus));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse block drop source: " + e.getMessage());
            }
        }

        return sources;
    }

    /**
     * Parse fishing drop sources from YAML list.
     */
    @SuppressWarnings("unchecked")
    private List<FishingDropSource> parseFishingDropSources(List<Map<?, ?>> sourceList) {
        List<FishingDropSource> sources = new ArrayList<>();

        for (Map<?, ?> sourceMap : sourceList) {
            try {
                // Parse chance
                double chance = parseDouble(sourceMap.get("chance"), 0.01);

                // Parse catch type
                FishingDropSource.CatchType catchType = FishingDropSource.CatchType.ANY;
                Object catchTypeObj = sourceMap.get("catch-type");
                if (catchTypeObj != null) {
                    try {
                        catchType = FishingDropSource.CatchType.valueOf(
                                catchTypeObj.toString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown catch type: " + catchTypeObj);
                    }
                }

                // Parse conditions
                SpawnConditions conditions = parseConditionsFromMap(
                        (Map<String, Object>) sourceMap.get("conditions"));

                // Parse enchant bonus
                Map<String, Double> enchantBonus = parseEnchantBonus(
                        (Map<?, ?>) sourceMap.get("enchant-bonus"));

                sources.add(new FishingDropSource(chance, catchType, conditions, enchantBonus));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse fishing drop source: " + e.getMessage());
            }
        }

        return sources;
    }

    /**
     * Parse loot drop sources from YAML list.
     */
    @SuppressWarnings("unchecked")
    private List<LootDropSource> parseLootDropSources(List<Map<?, ?>> sourceList) {
        List<LootDropSource> sources = new ArrayList<>();

        for (Map<?, ?> sourceMap : sourceList) {
            try {
                // Parse chance
                double chance = parseDouble(sourceMap.get("chance"), 0.01);

                // Parse loot tables
                Set<String> lootTables = new HashSet<>();
                Object tablesObj = sourceMap.get("loot-tables");
                if (tablesObj instanceof List<?> tableList) {
                    for (Object table : tableList) {
                        lootTables.add(table.toString().toUpperCase());
                    }
                }

                if (lootTables.isEmpty()) continue;

                // Parse conditions
                SpawnConditions conditions = parseConditionsFromMap(
                        (Map<String, Object>) sourceMap.get("conditions"));

                sources.add(new LootDropSource(chance, lootTables, conditions));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse loot drop source: " + e.getMessage());
            }
        }

        return sources;
    }

    /**
     * Parse spawn conditions from a raw map (for drop source conditions).
     */
    @SuppressWarnings("unchecked")
    private SpawnConditions parseConditionsFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        // Parse biomes
        Set<Biome> biomes = new HashSet<>();
        Object biomesObj = map.get("biomes");
        if (biomesObj instanceof List<?> biomeList) {
            for (Object biome : biomeList) {
                try {
                    biomes.add(Biome.valueOf(biome.toString().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown biome: " + biome);
                }
            }
        }

        // Parse dimensions
        Set<World.Environment> dimensions = new HashSet<>();
        Object dimsObj = map.get("dimensions");
        if (dimsObj instanceof List<?> dimList) {
            for (Object dim : dimList) {
                try {
                    dimensions.add(World.Environment.valueOf(dim.toString().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown dimension: " + dim);
                }
            }
        }

        // Parse time condition
        SpawnConditions.TimeCondition time = SpawnConditions.TimeCondition.ALWAYS;
        Object timeObj = map.get("time");
        if (timeObj != null) {
            try {
                time = SpawnConditions.TimeCondition.valueOf(timeObj.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown time condition: " + timeObj);
            }
        }

        return new SpawnConditions(
                biomes.isEmpty() ? null : biomes,
                dimensions.isEmpty() ? null : dimensions,
                parseInteger(map.get("min-y"), Integer.MIN_VALUE),
                parseInteger(map.get("max-y"), Integer.MAX_VALUE),
                parseInteger(map.get("min-light"), 0),
                parseInteger(map.get("max-light"), 15),
                parseBoolean(map.get("require-sky"), false),
                parseBoolean(map.get("underground"), false),
                time
        );
    }

    /**
     * Parse enchant bonus map from YAML.
     */
    private Map<String, Double> parseEnchantBonus(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        Map<String, Double> bonus = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String enchant = entry.getKey().toString().toLowerCase().replace("_", "-");
            double value = parseDouble(entry.getValue(), 0.0);
            bonus.put(enchant, value);
        }
        return bonus;
    }

    /**
     * Parse a double value from an object.
     */
    private double parseDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse an integer value from an object.
     */
    private int parseInteger(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parse a boolean value from an object.
     */
    private boolean parseBoolean(Object obj, boolean defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(obj.toString());
    }

    /**
     * Parse CollectionRewards from YAML configuration.
     *
     * @param section The rewards section
     * @return The parsed rewards
     */
    private Collection.CollectionRewards parseRewards(ConfigurationSection section) {
        if (section == null) {
            return Collection.CollectionRewards.EMPTY;
        }

        int experience = section.getInt("experience", 0);
        List<String> commands = section.getStringList("commands");
        String message = section.getString("message", "");
        boolean fireworks = section.getBoolean("fireworks", false);
        String sound = section.getString("sound", null);

        // Parse item rewards
        List<Collection.RewardItem> items = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    String materialStr = itemSection.getString("material", "DIAMOND");
                    Material material;
                    try {
                        material = Material.valueOf(materialStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        material = Material.DIAMOND;
                    }

                    int amount = itemSection.getInt("amount", 1);
                    String itemName = itemSection.getString("name", "");
                    List<String> lore = itemSection.getStringList("lore");

                    items.add(new Collection.RewardItem(material, amount, itemName, lore));
                }
            }
        }

        return new Collection.CollectionRewards(experience, items, commands, message, fireworks, sound);
    }

    /**
     * Get a collection by ID.
     *
     * @param id The collection ID
     * @return The collection, or null if not found
     */
    public Collection getCollection(String id) {
        return collections.get(id);
    }

    /**
     * Get all loaded collections.
     *
     * @return Unmodifiable map of collections
     */
    public Map<String, Collection> getAllCollections() {
        return Map.copyOf(collections);
    }

    /**
     * Get collections by tier.
     *
     * @param tier The tier to filter by
     * @return List of collections with that tier
     */
    public List<Collection> getCollectionsByTier(CollectibleTier tier) {
        return collections.values().stream()
                .filter(c -> c.tier() == tier)
                .toList();
    }

    /**
     * Get collections allowed in a specific zone.
     *
     * @param zoneId The zone ID
     * @return List of collections allowed in that zone
     */
    public List<Collection> getCollectionsForZone(String zoneId) {
        return collections.values().stream()
                .filter(c -> c.allowedZones().isEmpty() || c.allowedZones().contains(zoneId))
                .toList();
    }

    /**
     * Check if a collection exists.
     *
     * @param id The collection ID
     * @return true if the collection exists
     */
    public boolean hasCollection(String id) {
        return collections.containsKey(id);
    }

    /**
     * Get a specific item from a collection.
     *
     * @param collectionId The collection ID
     * @param itemId       The item ID
     * @return The item, or null if not found
     */
    public CollectionItem getItem(String collectionId, String itemId) {
        Collection collection = collections.get(collectionId);
        if (collection == null) {
            return null;
        }

        return collection.items().stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the number of loaded collections.
     *
     * @return The count
     */
    public int getCollectionCount() {
        return collections.size();
    }

    /**
     * Reload all collections from disk.
     */
    public void reload() {
        loadCollections();
    }
}
