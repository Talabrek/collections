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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up cooldown tracking to prevent memory leak
        CollectibleInteractListener interactListener = plugin.getCollectibleInteractListener();
        if (interactListener != null) {
            interactListener.cleanupPlayer(playerId);
        }

        // CRITICAL: Block until save completes to prevent data loss on rapid quit-rejoin.
        // 5-second timeout prevents server hang if database is unresponsive.
        try {
            playerDataManager.saveAndUnload(playerId)
                .get(5, TimeUnit.SECONDS);

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Saved and unloaded data for " + player.getName());
            }
        } catch (TimeoutException e) {
            plugin.getLogger().log(Level.SEVERE,
                "Save timed out for player " + playerId + " - data may be lost");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                "Save failed for player " + playerId, e);
        }
    }
}
