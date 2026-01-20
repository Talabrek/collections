package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.GoggleManager;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens for armor changes to refresh collectible visibility
 * when players equip or unequip Collector's Goggles.
 */
public class ArmorChangeListener implements Listener {

    private final Collections plugin;

    public ArmorChangeListener(Collections plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        // Only care about helmet changes
        if (event.getSlotType() != PlayerArmorChangeEvent.SlotType.HEAD) {
            return;
        }

        Player player = event.getPlayer();
        GoggleManager goggleManager = plugin.getGoggleManager();

        if (goggleManager == null) {
            return;
        }

        // Schedule the visibility refresh for next tick using EntityScheduler for Folia compatibility
        // EntityScheduler follows the entity across regions; no need for isOnline() check
        player.getScheduler().run(plugin, task -> {
            goggleManager.refreshVisibilityForPlayer(player);

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Refreshed collectible visibility for " + player.getName() +
                        " after helmet change");
            }
        }, null);
    }
}
