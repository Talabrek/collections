package com.blockworlds.collections.model;

import java.util.Map;

/**
 * Configuration for dropping collection items from fishing.
 *
 * @param chance       Base drop chance (0.0 to 1.0, e.g., 0.001 = 0.1%)
 * @param catchType    Type of catch required (FISH, TREASURE, JUNK, or ANY)
 * @param conditions   Spawn conditions (biome, time, dimension, etc.)
 * @param enchantBonus Bonus chance per enchantment level (e.g., "luck-of-the-sea" -> 0.0005)
 */
public record FishingDropSource(
        double chance,
        CatchType catchType,
        SpawnConditions conditions,
        Map<String, Double> enchantBonus
) {
    /**
     * Types of fishing catches.
     */
    public enum CatchType {
        FISH,      // Regular fish (cod, salmon, tropical, pufferfish)
        TREASURE,  // Treasure items (enchanted books, saddles, name tags, etc.)
        JUNK,      // Junk items (leather boots, bowls, sticks, etc.)
        ANY        // Any catch type
    }

    /**
     * Check if the given catch type matches this source's requirement.
     */
    public boolean matchesCatchType(CatchType type) {
        return catchType == CatchType.ANY || catchType == type;
    }

    /**
     * Get the enchantment bonus for a specific enchantment.
     *
     * @param enchantName Enchantment name (lowercase, e.g., "luck-of-the-sea")
     * @return Bonus per level, or 0 if not configured
     */
    public double getEnchantBonus(String enchantName) {
        if (enchantBonus == null) return 0.0;
        return enchantBonus.getOrDefault(enchantName.toLowerCase(), 0.0);
    }
}
