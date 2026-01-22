package com.blockworlds.collections.metrics;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.model.PlayerProgress;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for Collections plugin.
 * Provides placeholders for player and server-wide statistics.
 *
 * <h2>Player Placeholders</h2>
 * <ul>
 *   <li>{@code %collections_completed%} - Number of collections completed by the player</li>
 *   <li>{@code %collections_items%} - Total items collected by the player</li>
 *   <li>{@code %collections_progress_<id>%} - Progress for a specific collection (e.g., "3/5")</li>
 * </ul>
 *
 * <h2>Server Placeholders</h2>
 * <ul>
 *   <li>{@code %collections_server_total%} - Total items collected server-wide (since restart)</li>
 *   <li>{@code %collections_server_completed%} - Total collections completed server-wide (since restart)</li>
 *   <li>{@code %collections_server_active%} - Currently spawned collectibles count</li>
 * </ul>
 */
public class CollectionsExpansion extends PlaceholderExpansion {

    private final Collections plugin;
    private final PlayerDataManager playerDataManager;
    private final MetricsManager metricsManager;

    /**
     * Create a new CollectionsExpansion.
     *
     * @param plugin The Collections plugin instance
     */
    public CollectionsExpansion(Collections plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.metricsManager = plugin.getMetricsManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "collections";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BlockWorlds";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Keep registered during /papi reload
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String lowerParams = params.toLowerCase();

        // Server-wide placeholders (no player context needed)
        switch (lowerParams) {
            case "server_total" -> {
                // Total items collected server-wide (from counters)
                if (metricsManager != null) {
                    return String.valueOf(metricsManager.getItemsCollected());
                }
                return "0";
            }
            case "server_completed" -> {
                // Total collections completed server-wide (from counters)
                if (metricsManager != null) {
                    return String.valueOf(metricsManager.getCollectionsCompleted());
                }
                return "0";
            }
            case "server_active" -> {
                // Currently spawned collectibles
                return String.valueOf(plugin.getSpawnManager().getActiveCount());
            }
        }

        // Player-specific placeholders require player context
        if (player == null) {
            return "";
        }

        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());

        switch (lowerParams) {
            case "completed" -> {
                // Player's completed collections count
                return progress != null
                    ? String.valueOf(progress.getTotalCollectionsCompleted())
                    : "0";
            }
            case "items" -> {
                // Player's total items collected
                return progress != null
                    ? String.valueOf(progress.getTotalCollectiblesCollected())
                    : "0";
            }
            default -> {
                // Check for collection-specific progress: %collections_progress_<id>%
                if (lowerParams.startsWith("progress_")) {
                    String collectionId = params.substring(9); // Remove "progress_" prefix
                    if (progress != null) {
                        int collected = progress.getCollectedCount(collectionId);
                        var collection = plugin.getCollectionManager().getCollection(collectionId);
                        int total = collection != null ? collection.getItemCount() : 0;
                        return collected + "/" + total;
                    }
                    return "0/0";
                }
            }
        }

        return null; // Unknown placeholder
    }
}
