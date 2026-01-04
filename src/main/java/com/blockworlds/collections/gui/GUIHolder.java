package com.blockworlds.collections.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Interface for all Collection GUI holders.
 * Extends InventoryHolder to work with Bukkit's inventory system.
 */
public interface GUIHolder extends InventoryHolder {

    /**
     * Handle a click event in this GUI.
     *
     * @param event The click event
     */
    void handleClick(InventoryClickEvent event);

    /**
     * Handle the inventory close event.
     *
     * @param event The close event
     */
    void handleClose(InventoryCloseEvent event);

    /**
     * Get the type of this GUI.
     *
     * @return The GUI type
     */
    GUIType getType();
}
