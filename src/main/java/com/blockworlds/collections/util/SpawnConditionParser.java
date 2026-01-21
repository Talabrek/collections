package com.blockworlds.collections.util;

import com.blockworlds.collections.model.SpawnConditions;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Utility class for parsing spawn conditions from YAML configuration.
 */
public final class SpawnConditionParser {

    private SpawnConditionParser() {
        // Private constructor prevents instantiation
    }

    /**
     * Parse spawn conditions from a YAML configuration section.
     *
     * @param section The configuration section (can be null)
     * @param logger  Logger for warnings about invalid biome/dimension/time values
     * @return Parsed SpawnConditions, or SpawnConditions.NONE if section is null
     */
    public static SpawnConditions parse(ConfigurationSection section, Logger logger) {
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
                logger.warning("Unknown biome: " + biomeName);
            }
        }

        // Parse dimensions
        List<String> dimensionNames = section.getStringList("dimensions");
        Set<World.Environment> dimensions = new HashSet<>();
        for (String dimName : dimensionNames) {
            try {
                dimensions.add(World.Environment.valueOf(dimName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown dimension: " + dimName);
            }
        }

        // Parse time condition
        SpawnConditions.TimeCondition time = SpawnConditions.TimeCondition.ALWAYS;
        String timeStr = section.getString("time", "ALWAYS");
        try {
            time = SpawnConditions.TimeCondition.valueOf(timeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown time condition: " + timeStr);
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
     * Parse spawn conditions, returning null for null section.
     * Use this when null has semantic meaning (e.g., "no item-level restrictions").
     *
     * @param section The configuration section (can be null)
     * @param logger  Logger for warnings
     * @return Parsed SpawnConditions, or null if section is null
     */
    public static SpawnConditions parseOrNull(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return null;
        }
        return parse(section, logger);
    }
}
