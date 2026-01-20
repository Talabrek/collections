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
     * Cancels ALL click types when our GUI is open to prevent shift-click,
     * number key, double-click, and drag exploits.
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

        // Cancel ALL clicks when our GUI is open - prevents item movement
        // This must happen before any other checks to block shift-click,
        // number keys, double-click, and drag exploits in player inventory
        event.setCancelled(true);

        // Only route clicks actually in GUI slots to handler, not player inventory
        int topSize = holder.getInventory().getSize();
        int rawSlot = event.getRawSlot();

        // Click outside inventory bounds or in player inventory section
        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }

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
     * Checks ALL affected slots to catch drags that span player inventory and GUI.
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

        // Cancel if ANY drag slot is in GUI inventory
        // This catches drags that start in player inventory but extend into GUI
        int topSize = holder.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < topSize) {
                event.setCancelled(true);
                return;
            }
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
