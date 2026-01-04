package com.blockworlds.collections.model;

import org.bukkit.Material;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a collection definition - a themed set of items to complete.
 *
 * @param id                   Unique identifier for this collection
 * @param name                 Display name
 * @param description          Short description of the collection
 * @param tier                 Visibility tier (determines goggle requirements)
 * @param items                List of items in this collection
 * @param rewards              Rewards configuration for completing the collection
 * @param requiredCollections  IDs of collections that must be completed first (meta-collection)
 * @param allowedZones         IDs of zones where this collection can spawn (empty = all)
 * @param headTexture          Base64 texture for the collectible head
 * @param icon                 Material to use as the icon in GUIs
 * @param spawnConditions      Spawn conditions for this collection (biomes, time, etc.)
 */
public record Collection(
        String id,
        String name,
        String description,
        CollectibleTier tier,
        List<CollectionItem> items,
        CollectionRewards rewards,
        List<String> requiredCollections,
        List<String> allowedZones,
        String headTexture,
        Material icon,
        SpawnConditions spawnConditions
) {
    /**
     * Create a Collection with validation.
     */
    public Collection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Collection id cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or blank");
        }
        if (description == null) {
            description = "";
        }
        if (tier == null) {
            tier = CollectibleTier.COMMON;
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Collection must have at least one item");
        }
        if (rewards == null) {
            rewards = CollectionRewards.EMPTY;
        }
        if (requiredCollections == null) {
            requiredCollections = List.of();
        }
        if (allowedZones == null) {
            allowedZones = List.of();
        }
        if (headTexture == null) {
            headTexture = "";
        }
        if (icon == null) {
            icon = Material.PAPER;
        }
        // spawnConditions can be null (no collection-level restrictions)
    }

    /**
     * Get an item by its ID.
     *
     * @param itemId The item ID to find
     * @return The item, or null if not found
     */
    public CollectionItem getItem(String itemId) {
        return items.stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a random item based on weights.
     */
    public CollectionItem getRandomItem() {
        int totalWeight = items.stream().mapToInt(CollectionItem::weight).sum();
        int random = ThreadLocalRandom.current().nextInt(totalWeight);

        int cumulative = 0;
        for (CollectionItem item : items) {
            cumulative += item.weight();
            if (random < cumulative) {
                return item;
            }
        }

        // Fallback (should never happen)
        return items.get(0);
    }

    /**
     * Get the total number of items in this collection.
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Rewards for completing a collection.
     *
     * @param experience XP points to give
     * @param items      Physical items to give
     * @param commands   Console commands to execute (%player% placeholder)
     * @param message    Custom message to send to player
     * @param fireworks  Whether to spawn celebration fireworks
     * @param sound      Custom sound to play (null = use default)
     */
    public record CollectionRewards(
            int experience,
            List<RewardItem> items,
            List<String> commands,
            String message,
            boolean fireworks,
            String sound
    ) {
        public static final CollectionRewards EMPTY = new CollectionRewards(0, List.of(), List.of(), "", false, null);

        public CollectionRewards {
            if (items == null) items = List.of();
            if (commands == null) commands = List.of();
            if (message == null) message = "";
        }

        /**
         * Check if this reward has any actual rewards to give.
         */
        public boolean hasRewards() {
            return experience > 0 || !items.isEmpty() || !commands.isEmpty();
        }
    }

    /**
     * A reward item to give to the player.
     */
    public record RewardItem(
            Material material,
            int amount,
            String name,
            List<String> lore
    ) {
        public RewardItem {
            if (material == null) material = Material.PAPER;
            if (amount < 1) amount = 1;
            if (name == null) name = "";
            if (lore == null) lore = List.of();
        }
    }
}
