package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for collection list items.
 *
 * Contains summary information suitable for displaying collections in a list view.
 * Uses only primitive/String types for safe Gson serialization.
 *
 * @param id        Unique collection identifier
 * @param name      Display name
 * @param tier      Collection tier (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
 * @param itemCount Number of items in this collection
 * @param zones     Zone IDs where this collection can spawn
 */
public record CollectionSummary(
    String id,
    String name,
    String tier,
    int itemCount,
    List<String> zones
) {}
