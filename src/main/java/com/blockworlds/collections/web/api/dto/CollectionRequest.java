package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for collection create/update requests.
 *
 * Accepts JSON with all collection configuration fields.
 * Uses nullable types for optional fields - validation happens in CollectionValidator.
 *
 * @param id          Unique collection identifier (alphanumeric + underscore only)
 * @param name        Display name (required)
 * @param description Short description of the collection (optional)
 * @param tier        Collection tier: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, EVENT (optional)
 * @param icon        Material name for GUI icon (optional, defaults to PAPER)
 * @param items       List of items in this collection (required, at least 1)
 * @param rewards     Rewards configuration for completing the collection (optional)
 * @param zones       Zone IDs where this collection can spawn (optional, empty = all zones)
 * @param requires    IDs of collections that must be completed first (optional)
 */
public record CollectionRequest(
    String id,
    String name,
    String description,
    String tier,
    String icon,
    List<ItemRequest> items,
    RewardRequest rewards,
    List<String> zones,
    List<String> requires
) {}
