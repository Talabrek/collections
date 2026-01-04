package com.blockworlds.collections.util;

import com.blockworlds.collections.Collections;
import org.bukkit.NamespacedKey;

/**
 * Centralized PersistentDataContainer keys for the Collections plugin.
 * All PDC keys should be accessed through this class to ensure consistency.
 * Uses lazy initialization to avoid issues during class loading before plugin is enabled.
 */
public final class PDCKeys {

    // Cached keys - lazily initialized
    private static NamespacedKey collectionId;
    private static NamespacedKey itemId;
    private static NamespacedKey soulbound;
    private static NamespacedKey collectibleMarker;
    private static NamespacedKey collectibleTier;
    private static NamespacedKey goggleTier;

    private PDCKeys() {
        // Utility class, no instantiation
    }

    /**
     * Get the collection_id key for items.
     */
    public static NamespacedKey COLLECTION_ID() {
        if (collectionId == null) {
            collectionId = new NamespacedKey(Collections.getInstance(), "collection_id");
        }
        return collectionId;
    }

    /**
     * Get the item_id key for items.
     */
    public static NamespacedKey ITEM_ID() {
        if (itemId == null) {
            itemId = new NamespacedKey(Collections.getInstance(), "item_id");
        }
        return itemId;
    }

    /**
     * Get the soulbound key for items.
     */
    public static NamespacedKey SOULBOUND() {
        if (soulbound == null) {
            soulbound = new NamespacedKey(Collections.getInstance(), "soulbound");
        }
        return soulbound;
    }

    /**
     * Get the collectible marker key for entities.
     */
    public static NamespacedKey COLLECTIBLE_MARKER() {
        if (collectibleMarker == null) {
            collectibleMarker = new NamespacedKey(Collections.getInstance(), "collectible");
        }
        return collectibleMarker;
    }

    /**
     * Get the tier key for collectible entities.
     */
    public static NamespacedKey COLLECTIBLE_TIER() {
        if (collectibleTier == null) {
            collectibleTier = new NamespacedKey(Collections.getInstance(), "tier");
        }
        return collectibleTier;
    }

    /**
     * Get the goggle tier key for helmet items.
     */
    public static NamespacedKey GOGGLE_TIER() {
        if (goggleTier == null) {
            goggleTier = new NamespacedKey(Collections.getInstance(), "goggle_tier");
        }
        return goggleTier;
    }

    /**
     * Clear cached keys (used for reload).
     */
    public static void clearCache() {
        collectionId = null;
        itemId = null;
        soulbound = null;
        collectibleMarker = null;
        collectibleTier = null;
        goggleTier = null;
    }
}
