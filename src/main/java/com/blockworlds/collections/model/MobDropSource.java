package com.blockworlds.collections.model;

import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for dropping collection items from mob kills.
 *
 * @param entities           Set of entity types that can drop this item
 * @param chance             Base drop chance (0.0 to 1.0, e.g., 0.00001 = 0.001%)
 * @param playerKillRequired Whether the mob must be killed by a player
 * @param conditions         Spawn conditions (biome, time, dimension, etc.)
 * @param enchantBonus       Bonus chance per enchantment level (e.g., "looting" -> 0.02)
 */
public record MobDropSource(
        Set<EntityType> entities,
        double chance,
        boolean playerKillRequired,
        SpawnConditions conditions,
        Map<String, Double> enchantBonus
) {
    /**
     * Check if this source matches the given entity type.
     */
    public boolean matchesEntity(EntityType type) {
        return entities != null && entities.contains(type);
    }

    /**
     * Get the enchantment bonus for a specific enchantment.
     *
     * @param enchantName Enchantment name (lowercase, e.g., "looting")
     * @return Bonus per level, or 0 if not configured
     */
    public double getEnchantBonus(String enchantName) {
        if (enchantBonus == null) return 0.0;
        return enchantBonus.getOrDefault(enchantName.toLowerCase(), 0.0);
    }
}
