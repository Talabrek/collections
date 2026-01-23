package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for reward data in collection create/update requests.
 *
 * Accepts JSON reward configuration for collection write operations.
 * Uses nullable types for optional fields - validation happens in CollectionValidator.
 *
 * @param experience XP points awarded on completion (optional, defaults to 0)
 * @param commands   Console commands to execute with %player% placeholder (optional)
 * @param message    Custom message sent to player on completion (optional)
 * @param fireworks  Whether to spawn celebration fireworks (optional, defaults to false)
 */
public record RewardRequest(
    Integer experience,
    List<String> commands,
    String message,
    Boolean fireworks
) {}
