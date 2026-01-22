package com.blockworlds.collections.gui;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.PlayerProgress;
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
 * Preview GUI showing the full collection grid when adding an item.
 * Shows collected/uncollected items and progress preview before confirming add.
 */
public class AddPreviewGUI implements GUIHolder {

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
    private static final int INVENTORY_SIZE = 54;  // 6 rows

    // Item display slots (rows 1-3, 21 slots)
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    // Info slot (row 4 center)
    private static final int INFO_SLOT = 40;

    // Bottom row slots
    private static final int ITEM_DISPLAY_SLOT = 49;  // Item being added
    private static final int CONFIRM_SLOT = 47;       // Yes button
    private static final int CANCEL_SLOT = 51;        // No button

    public AddPreviewGUI(Collections plugin, Player player, ItemStack itemToAdd,
                         Collection collection, CollectionItem collectionItem) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.player = player;
        this.itemToAdd = itemToAdd.clone();
        this.collection = collection;
        this.collectionItem = collectionItem;

        Component title = configManager.parse("<gold>Add to " + collection.name() + "?</gold>");
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

        // Fill all slots with filler
        ItemStack filler = guiManager.createFiller();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        int collectedCount = progress != null ? progress.getCollectedCount(collection.id()) : 0;
        boolean hasProgress = collectedCount > 0;

        // Populate collection items in the grid
        List<CollectionItem> items = collection.items();
        for (int i = 0; i < ITEM_SLOTS.length && i < items.size(); i++) {
            CollectionItem item = items.get(i);
            boolean isItemBeingAdded = item.id().equals(collectionItem.id());
            boolean collected = progress != null && progress.hasItem(collection.id(), item.id());
            inventory.setItem(ITEM_SLOTS[i], createSlotItem(item, collected, isItemBeingAdded, hasProgress));
        }

        // Info item showing progress preview
        inventory.setItem(INFO_SLOT, createInfoItem(progress));

        // The actual item being added (shown in bottom row)
        inventory.setItem(ITEM_DISPLAY_SLOT, itemToAdd);

        // Confirm and cancel buttons
        inventory.setItem(CONFIRM_SLOT, guiManager.createConfirmButton());
        inventory.setItem(CANCEL_SLOT, guiManager.createCancelButton());
    }

    /**
     * Create a slot item for the collection grid.
     *
     * @param item           The collection item
     * @param collected      Whether this item has been collected
     * @param isItemBeingAdded Whether this is the item being added
     * @param hasProgress    Whether the player has any progress in this collection
     * @return The item to display in the slot
     */
    private ItemStack createSlotItem(CollectionItem item, boolean collected, boolean isItemBeingAdded, boolean hasProgress) {
        if (isItemBeingAdded) {
            // This is the item being added - highlight with green name and glowing
            return ItemBuilder.of(item.material())
                    .name("<green>" + item.name() + "</green>")
                    .addLore("<gray>-----</gray>")
                    .addLore("<yellow>Adding this item!</yellow>")
                    .glowing()
                    .build();
        } else if (collected) {
            // Already collected - show with gold name
            return ItemBuilder.of(item.material())
                    .name("<gold>" + item.name() + "</gold>")
                    .addLore("<green>Collected</green>")
                    .build();
        } else if (hasProgress) {
            // Has some progress - show name as hint
            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name("<yellow>" + item.name() + "</yellow>")
                    .addLore("<red>Not yet found</red>")
                    .build();
        } else {
            // No progress - mystery item
            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name("<gray>???</gray>")
                    .addLore("<dark_gray>Collect items to reveal</dark_gray>")
                    .build();
        }
    }

    /**
     * Create the info item showing progress preview.
     *
     * @param progress The player's current progress
     * @return The info item
     */
    private ItemStack createInfoItem(PlayerProgress progress) {
        int collected = progress != null ? progress.getCollectedCount(collection.id()) : 0;
        int total = collection.getItemCount();
        int afterAdd = collected + 1;

        List<String> lore = new ArrayList<>();
        lore.add("<gray>-----</gray>");
        lore.add("<white>Progress: " + guiManager.createProgressBar(collected, total, 10) + "</white>");
        lore.add("");
        lore.add("<yellow>After adding:</yellow>");
        lore.add("<white>" + guiManager.createProgressBar(afterAdd, total, 10) + "</white>");

        // Check if this will complete the collection
        if (afterAdd >= total) {
            lore.add("");
            lore.add("<green>This will complete the collection!</green>");
        }

        return ItemBuilder.of(Material.PAPER)
                .name("<gold>" + collection.name() + "</gold>")
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
     * Note: Plan 02 will implement the full transition logic.
     * For now, this is a stub that just closes the inventory.
     */
    private void confirmAdd() {
        // Stub for now - Plan 02 will implement the full logic with transition
        player.closeInventory();
        player.sendMessage(configManager.getMessage("item-added-placeholder",
                "item", collectionItem.name()));
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
        // Nothing special needed on close
    }

    @Override
    public GUIType getType() {
        return GUIType.ADD_PREVIEW;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    // ========== Getters for Plan 02 to use ==========

    /**
     * Get the item being added.
     * @return The item stack being added to the collection
     */
    public ItemStack getItemToAdd() {
        return itemToAdd;
    }

    /**
     * Get the collection this item belongs to.
     * @return The collection
     */
    public Collection getCollection() {
        return collection;
    }

    /**
     * Get the collection item being added.
     * @return The collection item
     */
    public CollectionItem getCollectionItem() {
        return collectionItem;
    }

    /**
     * Get the player adding the item.
     * @return The player
     */
    public Player getPlayer() {
        return player;
    }
}
