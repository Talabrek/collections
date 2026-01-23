package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for item data in collection create/update requests.
 *
 * Accepts JSON item data for collection write operations.
 * Uses nullable types for optional fields - validation happens in CollectionValidator.
 *
 * @param id        Unique item identifier within collection
 * @param name      Display name (required)
 * @param material  Minecraft material name (optional, defaults to PAPER)
 * @param lore      Item lore lines (optional)
 * @param weight    Spawn weight, higher = more common (optional, defaults to 10)
 * @param soulbound Whether the item is bound on pickup (optional, defaults to false)
 */
public record ItemRequest(
    String id,
    String name,
    String material,
    List<String> lore,
    Integer weight,
    Boolean soulbound
) {}
