package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.gui.GUIHolder;
import com.blockworlds.collections.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for GUI events.
 * Routes inventory events to the appropriate GUI handler.
 */
public class GUIListener implements Listener {

    private final Collections plugin;
    private final GUIManager guiManager;

    public GUIListener(Collections plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    /**
     * Handle inventory click events.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUIHolder holder = guiManager.getOpenGUI(player.getUniqueId());
        if (holder == null) {
            return;
        }

        // Only handle clicks in our GUI inventory
        if (!event.getInventory().equals(holder.getInventory())) {
            return;
        }

        // Cancel the event to prevent item movement
        event.setCancelled(true);

        // Route to the GUI handler
        holder.handleClick(event);
    }

    /**
     * Handle inventory close events.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        GUIHolder holder = guiManager.getOpenGUI(player.getUniqueId());
        if (holder == null) {
            return;
        }

        // Only handle our GUI
        if (!event.getInventory().equals(holder.getInventory())) {
            return;
        }

        // Notify the holder
        holder.handleClose(event);

        // Unregister the GUI
        guiManager.unregisterGUI(player.getUniqueId());
    }

    /**
     * Handle inventory drag events (prevent dragging in our GUIs).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUIHolder holder = guiManager.getOpenGUI(player.getUniqueId());
        if (holder == null) {
            return;
        }

        // Cancel dragging in our GUIs
        if (event.getInventory().equals(holder.getInventory())) {
            event.setCancelled(true);
        }
    }

    /**
     * Clean up when a player quits.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        guiManager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}
