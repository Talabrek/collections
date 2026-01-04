package com.blockworlds.collections.model;

import org.bukkit.Material;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for dropping collection items from block breaks.
 *
 * @param blockTypes        Set of block types that can drop this item
 * @param chance            Base drop chance (0.0 to 1.0, e.g., 0.0001 = 0.01%)
 * @param toolRequired      Whether a proper tool is required (e.g., axe for logs)
 * @param silkTouchDisabled Whether silk touch prevents the drop
 * @param conditions        Spawn conditions (biome, time, dimension, etc.)
 * @param enchantBonus      Bonus chance per enchantment level (e.g., "fortune" -> 0.00002)
 */
public record BlockDropSource(
        Set<Material> blockTypes,
        double chance,
        boolean toolRequired,
        boolean silkTouchDisabled,
        SpawnConditions conditions,
        Map<String, Double> enchantBonus
) {
    /**
     * Check if this source matches the given block type.
     */
    public boolean matchesBlock(Material type) {
        return blockTypes != null && blockTypes.contains(type);
    }

    /**
     * Get the enchantment bonus for a specific enchantment.
     *
     * @param enchantName Enchantment name (lowercase, e.g., "fortune")
     * @return Bonus per level, or 0 if not configured
     */
    public double getEnchantBonus(String enchantName) {
        if (enchantBonus == null) return 0.0;
        return enchantBonus.getOrDefault(enchantName.toLowerCase(), 0.0);
    }
}
