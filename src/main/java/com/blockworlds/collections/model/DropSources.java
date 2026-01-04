package com.blockworlds.collections.model;

import java.util.List;

/**
 * Container for all alternative drop sources for a collection item.
 * Items can have multiple sources of each type (e.g., drop from multiple mob types).
 *
 * @param mobs    List of mob kill drop sources
 * @param blocks  List of block break drop sources
 * @param fishing List of fishing drop sources
 * @param loot    List of container loot drop sources
 */
public record DropSources(
        List<MobDropSource> mobs,
        List<BlockDropSource> blocks,
        List<FishingDropSource> fishing,
        List<LootDropSource> loot
) {
    /**
     * Empty drop sources (no alternative drops configured).
     */
    public static final DropSources EMPTY = new DropSources(
            List.of(),
            List.of(),
            List.of(),
            List.of()
    );

    /**
     * Check if any drop sources are configured.
     */
    public boolean hasAnySource() {
        return !mobs.isEmpty() || !blocks.isEmpty() || !fishing.isEmpty() || !loot.isEmpty();
    }

    /**
     * Check if mob drop sources are configured.
     */
    public boolean hasMobSources() {
        return mobs != null && !mobs.isEmpty();
    }

    /**
     * Check if block drop sources are configured.
     */
    public boolean hasBlockSources() {
        return blocks != null && !blocks.isEmpty();
    }

    /**
     * Check if fishing drop sources are configured.
     */
    public boolean hasFishingSources() {
        return fishing != null && !fishing.isEmpty();
    }

    /**
     * Check if loot drop sources are configured.
     */
    public boolean hasLootSources() {
        return loot != null && !loot.isEmpty();
    }
}
