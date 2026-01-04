package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.recipe.GoggleRecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles player join/quit for data loading and saving.
 */
public class PlayerListener implements Listener {

    private final Collections plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerListener(Collections plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data asynchronously
        playerDataManager.loadPlayer(player)
                .thenAccept(progress -> {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("Loaded data for " + player.getName() +
                                " (" + progress.getTotalCollectiblesCollected() + " items collected)");
                    }

                    // Unlock recipes for returning players who have collected items
                    if (progress.getTotalCollectiblesCollected() > 0) {
                        GoggleRecipeManager recipeManager = plugin.getGoggleRecipeManager();
                        if (recipeManager != null && !recipeManager.hasDiscoveredRecipes(player)) {
                            // Run on main thread since discoverRecipe needs it
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    recipeManager.unlockRecipesForPlayer(player);
                                }
                            });
                        }
                    }
                });

        // Schedule visibility refresh after a short delay to allow chunks to load
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                GoggleManager goggleManager = plugin.getGoggleManager();
                if (goggleManager != null) {
                    goggleManager.refreshVisibilityForPlayer(player);
                }
            }
        }, 20L); // 1 second delay
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up cooldown tracking to prevent memory leak
        CollectibleInteractListener interactListener = plugin.getCollectibleInteractListener();
        if (interactListener != null) {
            interactListener.cleanupPlayer(playerId);
        }

        // Save and unload player data
        playerDataManager.saveAndUnload(playerId)
                .thenRun(() -> {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("Saved and unloaded data for " + player.getName());
                    }
                });
    }
}
