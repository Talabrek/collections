package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for full collection details.
 *
 * Contains complete collection information for detail view pages.
 * Uses only primitive/String types for safe Gson serialization.
 *
 * @param id          Unique collection identifier
 * @param name        Display name
 * @param description Collection description
 * @param tier        Collection tier (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
 * @param icon        GUI icon material name
 * @param items       List of items in this collection
 * @param rewards     Rewards for completing this collection
 * @param zones       Zone IDs where this collection can spawn
 * @param requires    IDs of collections that must be completed first
 */
public record CollectionDetail(
    String id,
    String name,
    String description,
    String tier,
    String icon,
    List<ItemSummary> items,
    RewardSummary rewards,
    List<String> zones,
    List<String> requires
) {}
