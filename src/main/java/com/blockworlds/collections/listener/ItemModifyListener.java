package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents modification of collection items.
 * Blocks anvil renaming and enchanting to preserve item integrity.
 */
public class ItemModifyListener implements Listener {

    private final Collections plugin;

    public ItemModifyListener(Collections plugin) {
        this.plugin = plugin;
    }

    /**
     * Block renaming collection items in anvils.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        // Check both input slots for collection items
        ItemStack firstItem = event.getInventory().getFirstItem();
        ItemStack secondItem = event.getInventory().getSecondItem();

        if (isCollectionItem(firstItem) || isCollectionItem(secondItem)) {
            // Cancel the result to prevent modification
            event.setResult(null);

            // Try to notify the player
            if (event.getView().getPlayer() instanceof Player player) {
                player.sendMessage(plugin.getConfigManager().getMessage("item-cannot-modify"));
            }
        }
    }

    /**
     * Block enchanting collection items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();

        if (isCollectionItem(item)) {
            event.setCancelled(true);
            event.getEnchanter().sendMessage(
                    plugin.getConfigManager().getMessage("item-cannot-modify"));
        }
    }

    /**
     * Check if an item is a collection item by checking for the collection_id PDC key.
     */
    private boolean isCollectionItem(ItemStack item) {
        return ItemBuilder.hasData(item, PDCKeys.COLLECTION_ID());
    }
}
