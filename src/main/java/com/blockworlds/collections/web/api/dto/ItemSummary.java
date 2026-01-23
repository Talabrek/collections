package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for collection items within detail view.
 *
 * Contains item information for display in collection detail pages.
 * Uses only primitive/String types for safe Gson serialization.
 *
 * @param id        Unique item identifier within collection
 * @param name      Display name
 * @param material  Minecraft material name (e.g., "DIAMOND")
 * @param weight    Spawn weight (higher = more common)
 * @param soulbound Whether the item is bound on pickup
 * @param lore      Item lore lines
 */
public record ItemSummary(
    String id,
    String name,
    String material,
    int weight,
    boolean soulbound,
    List<String> lore
) {}
