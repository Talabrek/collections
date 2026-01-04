package com.blockworlds.collections.model;

import java.util.Set;

/**
 * Configuration for dropping collection items from container loot (chests, etc.).
 *
 * @param chance     Base drop chance (0.0 to 1.0, e.g., 0.02 = 2%)
 * @param lootTables Set of loot table names that can contain this item
 *                   (e.g., "NETHER_BRIDGE", "DESERT_PYRAMID", "BURIED_TREASURE")
 * @param conditions Spawn conditions (biome, dimension, etc.)
 */
public record LootDropSource(
        double chance,
        Set<String> lootTables,
        SpawnConditions conditions
) {
    /**
     * Check if this source matches the given loot table key.
     *
     * @param lootTableKey The loot table key (e.g., "chests/nether_bridge")
     * @return true if this source applies to the given loot table
     */
    public boolean matchesLootTable(String lootTableKey) {
        if (lootTables == null || lootTables.isEmpty()) {
            return false;
        }

        // Normalize the key for comparison
        String normalizedKey = lootTableKey.toUpperCase()
                .replace("CHESTS/", "")
                .replace("/", "_")
                .replace("-", "_");

        for (String table : lootTables) {
            String normalizedTable = table.toUpperCase()
                    .replace("/", "_")
                    .replace("-", "_");

            if (normalizedKey.contains(normalizedTable) || normalizedTable.equals(normalizedKey)) {
                return true;
            }
        }

        return false;
    }
}
