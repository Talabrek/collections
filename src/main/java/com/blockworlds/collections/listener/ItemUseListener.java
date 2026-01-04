package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.gui.ConfirmAddGUI;
import com.blockworlds.collections.manager.CollectionManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-clicking collection items to add them to the journal.
 * Opens a confirmation GUI before consuming the item.
 */
public class ItemUseListener implements Listener {

    private final Collections plugin;
    private final ConfigManager configManager;
    private final CollectionManager collectionManager;
    private final PlayerDataManager playerDataManager;

    public ItemUseListener(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.collectionManager = plugin.getCollectionManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only handle main hand to prevent double firing
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if it's a collection item
        if (!isCollectionItem(item)) {
            return;
        }

        // Cancel the event to prevent placing blocks, etc.
        event.setCancelled(true);

        // Get collection and item IDs from PDC
        String collectionId = ItemBuilder.getData(item, PDCKeys.COLLECTION_ID());
        String itemId = ItemBuilder.getData(item, PDCKeys.ITEM_ID());

        if (collectionId == null || itemId == null) {
            plugin.getLogger().warning("Collection item missing PDC data for player " + player.getName());
            return;
        }

        // Verify collection and item still exist
        Collection collection = collectionManager.getCollection(collectionId);
        if (collection == null) {
            player.sendMessage(configManager.getMessage("collection-not-found", "collection", collectionId));
            return;
        }

        CollectionItem collectionItem = collection.getItem(itemId);
        if (collectionItem == null) {
            player.sendMessage(configManager.getMessage("item-not-found", "item", itemId));
            return;
        }

        // Check if player already has this item in their journal
        if (playerDataManager.hasItem(player.getUniqueId(), collectionId, itemId)) {
            player.sendMessage(configManager.getMessage("item-duplicate", "item", collectionItem.name()));
            // Don't consume - let them trade it
            return;
        }

        // Open confirmation GUI instead of directly adding
        ConfirmAddGUI confirmGui = new ConfirmAddGUI(plugin, player, item, collection, collectionItem);
        confirmGui.open();
    }

    /**
     * Check if an item is a collection item.
     */
    private boolean isCollectionItem(ItemStack item) {
        return ItemBuilder.hasData(item, PDCKeys.COLLECTION_ID());
    }
}
