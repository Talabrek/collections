package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for collection rewards within detail view.
 *
 * Contains reward information for display in collection detail pages.
 * Uses only primitive/String types for safe Gson serialization.
 *
 * @param experience XP points awarded on completion
 * @param commands   Console commands executed (%player% placeholder)
 * @param fireworks  Whether celebration fireworks are spawned
 */
public record RewardSummary(
    int experience,
    List<String> commands,
    boolean fireworks
) {}
