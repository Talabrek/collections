package com.blockworlds.collections.gui;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.recipe.GoggleRecipeManager;
import com.blockworlds.collections.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation GUI before adding an item to the collection journal.
 */
public class ConfirmAddGUI implements GUIHolder {

    private final Collections plugin;
    private final GUIManager guiManager;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final Player player;
    private final ItemStack itemToAdd;
    private final Collection collection;
    private final CollectionItem collectionItem;
    private final Inventory inventory;

    // Layout constants
    private static final int INVENTORY_SIZE = 27;  // 3 rows

    // Item display slot (center top)
    private static final int ITEM_SLOT = 4;

    // Info slot (center middle)
    private static final int INFO_SLOT = 13;

    // Action buttons (bottom row)
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;

    public ConfirmAddGUI(Collections plugin, Player player, ItemStack itemToAdd,
                         Collection collection, CollectionItem collectionItem) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.player = player;
        this.itemToAdd = itemToAdd.clone();
        this.collection = collection;
        this.collectionItem = collectionItem;

        Component title = configManager.parse("<yellow>Add to Collection?</yellow>");
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
    }

    /**
     * Open the GUI for the player.
     */
    public void open() {
        populateInventory();
        guiManager.registerGUI(player.getUniqueId(), this);
        player.openInventory(inventory);
        guiManager.playOpenSound(player);
    }

    /**
     * Populate the inventory with items.
     */
    private void populateInventory() {
        inventory.clear();

        // Fill with glass panes
        ItemStack filler = guiManager.createFiller();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Show the item being added
        inventory.setItem(ITEM_SLOT, itemToAdd);

        // Info item
        inventory.setItem(INFO_SLOT, createInfoItem());

        // Confirm and cancel buttons
        inventory.setItem(CONFIRM_SLOT, guiManager.createConfirmButton());
        inventory.setItem(CANCEL_SLOT, guiManager.createCancelButton());
    }

    /**
     * Create the info item explaining the action.
     */
    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>─────────────────</gray>");
        lore.add("<white>This will consume the item</white>");
        lore.add("<white>and add it to your journal.</white>");
        lore.add("");
        lore.add("<gray>Collection: <gold>" + collection.name() + "</gold></gray>");
        lore.add("<gray>Item: <gold>" + collectionItem.name() + "</gold></gray>");
        lore.add("");
        lore.add("<yellow>Click Confirm to add</yellow>");
        lore.add("<yellow>Click Cancel to keep item</yellow>");

        return ItemBuilder.of(Material.PAPER)
                .name("<yellow>Add to Collection?</yellow>")
                .lore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        guiManager.playClickSound(player);

        if (slot == CONFIRM_SLOT) {
            confirmAdd();
        } else if (slot == CANCEL_SLOT) {
            cancel();
        }
    }

    /**
     * Confirm adding the item to the journal.
     */
    private void confirmAdd() {
        // Close the GUI first
        player.closeInventory();

        // Check if player still has the item in their inventory
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isMatchingItem(mainHand)) {
            player.sendMessage(configManager.getMessage("item-not-found-in-hand"));
            guiManager.playErrorSound(player);
            return;
        }

        String collectionId = collection.id();
        String itemId = collectionItem.id();

        // Check if already has this item
        if (playerDataManager.hasItem(player.getUniqueId(), collectionId, itemId)) {
            player.sendMessage(configManager.getMessage("item-duplicate",
                    "item", collectionItem.name()));
            guiManager.playErrorSound(player);
            return;
        }

        // Add to journal
        boolean added = playerDataManager.addItem(player.getUniqueId(), collectionId, itemId);
        if (!added) {
            player.sendMessage(configManager.getMessage("item-duplicate",
                    "item", collectionItem.name()));
            guiManager.playErrorSound(player);
            return;
        }

        // Check if this was the first collection - unlock recipes
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        if (progress != null && progress.getTotalCollectiblesCollected() == 1) {
            GoggleRecipeManager recipeManager = plugin.getGoggleRecipeManager();
            if (recipeManager != null) {
                recipeManager.unlockRecipesForPlayer(player);
            }
        }

        // Consume the item
        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Play sound
        String addSound = configManager.getSound("add-to-journal");
        if (addSound != null) {
            player.playSound(player.getLocation(), addSound, 1.0f, 1.0f);
        }

        // Send success message
        player.sendMessage(configManager.getMessage("item-added-to-journal",
                "item", collectionItem.name(),
                "collection", collection.name()));

        // Check if collection is now complete
        checkCollectionComplete();
    }

    /**
     * Check if the item in hand matches the one we're adding.
     */
    private boolean isMatchingItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Check if PDC tags match
        String itemCollectionId = com.blockworlds.collections.util.ItemBuilder.getData(
                item, com.blockworlds.collections.util.PDCKeys.COLLECTION_ID());
        String itemItemId = com.blockworlds.collections.util.ItemBuilder.getData(
                item, com.blockworlds.collections.util.PDCKeys.ITEM_ID());

        return collection.id().equals(itemCollectionId) &&
                collectionItem.id().equals(itemItemId);
    }

    /**
     * Check if the collection is complete after adding this item.
     */
    private void checkCollectionComplete() {
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        if (progress == null) {
            return;
        }

        int collectedCount = progress.getCollectedCount(collection.id());
        int totalCount = collection.getItemCount();

        if (collectedCount >= totalCount) {
            // Collection is complete!
            playerDataManager.markComplete(player.getUniqueId(), collection.id());

            // Play completion sound
            String completeSound = configManager.getSound("complete-collection");
            if (completeSound != null) {
                player.playSound(player.getLocation(), completeSound, 1.0f, 1.0f);
            }

            // Send completion message
            player.sendMessage(configManager.getMessage("collection-complete",
                    "collection", collection.name()));

            if (configManager.isDebugMode()) {
                plugin.getLogger().info(player.getName() + " completed collection: " + collection.id());
            }
        }
    }

    /**
     * Cancel and keep the item.
     */
    private void cancel() {
        player.closeInventory();
        player.sendMessage(configManager.getMessage("add-cancelled"));
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Nothing special - item stays in player's inventory
    }

    @Override
    public GUIType getType() {
        return GUIType.CONFIRM_ADD;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
