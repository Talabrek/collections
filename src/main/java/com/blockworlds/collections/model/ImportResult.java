package com.blockworlds.collections.model;

import java.util.List;
import java.util.UUID;

/**
 * Result of an import operation.
 */
public record ImportResult(
    boolean success,
    int playersImported,
    int playersSkipped,
    List<UUID> affectedOnlinePlayers,
    String errorMessage
) {
    public ImportResult(int playersImported, int playersSkipped, List<UUID> affectedOnlinePlayers) {
        this(true, playersImported, playersSkipped, affectedOnlinePlayers, null);
    }

    public static ImportResult failure(String errorMessage) {
        return new ImportResult(false, 0, 0, List.of(), errorMessage);
    }
}
