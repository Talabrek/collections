package com.blockworlds.collections.gui;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.manager.RewardManager;
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
 * Detail view GUI for a single collection.
 * Shows collected/missing items and allows reward claiming.
 */
public class CollectionDetailGUI implements GUIHolder {

    private final Collections plugin;
    private final GUIManager guiManager;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final Player player;
    private final Collection collection;
    private final Inventory inventory;

    // Layout constants
    private static final int INVENTORY_SIZE = 54;  // 6 rows

    // Item display slots (rows 1-3, 21 slots)
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    // Reward preview slots (row 4)
    private static final int[] REWARD_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    // Navigation slots (row 5)
    private static final int BACK_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int CLAIM_SLOT = 53;

    public CollectionDetailGUI(Collections plugin, Player player, Collection collection) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.player = player;
        this.collection = collection;

        Component title = configManager.parse("<gold>" + collection.name() + "</gold>");
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

        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());

        // Populate collection items
        List<CollectionItem> items = collection.items();
        for (int i = 0; i < ITEM_SLOTS.length && i < items.size(); i++) {
            CollectionItem item = items.get(i);
            boolean collected = progress != null && progress.hasItem(collection.id(), item.id());
            inventory.setItem(ITEM_SLOTS[i], createItemIcon(item, collected));
        }

        // Clear unused item slots
        for (int i = items.size(); i < ITEM_SLOTS.length; i++) {
            inventory.setItem(ITEM_SLOTS[i], null);
        }

        // Reward preview section
        populateRewardPreview();

        // Info item
        inventory.setItem(INFO_SLOT, createInfoItem(progress));

        // Navigation
        inventory.setItem(BACK_SLOT, guiManager.createBackButton());

        // Claim button
        boolean complete = progress != null && progress.hasCompleted(collection.id());
        boolean claimed = progress != null && progress.hasClaimedReward(collection.id());
        inventory.setItem(CLAIM_SLOT, guiManager.createClaimButton(complete, claimed));
    }

    /**
     * Create an icon for a collection item.
     */
    private ItemStack createItemIcon(CollectionItem item, boolean collected) {
        if (collected) {
            // Show the actual item
            List<String> lore = new ArrayList<>();
            lore.add("<gray>─────────────────</gray>");
            lore.addAll(item.lore());
            lore.add("");
            lore.add("<green>✓ Collected</green>");

            if (item.soulbound()) {
                lore.add("<red>Soulbound</red>");
            }

            return ItemBuilder.of(item.material())
                    .name("<gold>" + item.name() + "</gold>")
                    .lore(lore)
                    .build();
        } else {
            // Show a mystery item
            List<String> lore = new ArrayList<>();
            lore.add("<gray>─────────────────</gray>");
            lore.add("<gray>??? Not yet found</gray>");
            lore.add("");

            // Add zone hint
            if (!collection.allowedZones().isEmpty()) {
                String zones = String.join(", ", collection.allowedZones());
                lore.add("<gray>Found in: " + zones + "</gray>");
            }

            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name("<gray>???</gray>")
                    .lore(lore)
                    .build();
        }
    }

    /**
     * Populate the reward preview section.
     */
    private void populateRewardPreview() {
        Collection.CollectionRewards rewards = collection.rewards();

        List<ItemStack> rewardItems = new ArrayList<>();

        // Experience reward
        if (rewards.experience() > 0) {
            rewardItems.add(ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                    .name("<green>Experience Reward</green>")
                    .addLore("<yellow>" + rewards.experience() + " XP</yellow>")
                    .build());
        }

        // Item rewards
        for (Collection.RewardItem rewardItem : rewards.items()) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>─────────────────</gray>");
            if (rewardItem.amount() > 1) {
                lore.add("<white>Amount: " + rewardItem.amount() + "</white>");
            }
            lore.addAll(rewardItem.lore());

            String name = rewardItem.name().isEmpty() ?
                    formatMaterialName(rewardItem.material()) :
                    rewardItem.name();

            rewardItems.add(ItemBuilder.of(rewardItem.material())
                    .name("<gold>" + name + "</gold>")
                    .lore(lore)
                    .amount(Math.min(rewardItem.amount(), 64))
                    .build());
        }

        // Command rewards (show as book)
        if (!rewards.commands().isEmpty()) {
            rewardItems.add(ItemBuilder.of(Material.COMMAND_BLOCK)
                    .name("<light_purple>Special Rewards</light_purple>")
                    .addLore("<gray>Commands will be executed</gray>")
                    .addLore("<gray>when you claim rewards</gray>")
                    .build());
        }

        // Place rewards in slots
        for (int i = 0; i < REWARD_SLOTS.length && i < rewardItems.size(); i++) {
            inventory.setItem(REWARD_SLOTS[i], rewardItems.get(i));
        }

        // Fill remaining reward slots with info
        if (rewardItems.isEmpty()) {
            inventory.setItem(REWARD_SLOTS[3], ItemBuilder.of(Material.BARRIER)
                    .name("<gray>No Rewards Configured</gray>")
                    .build());
        }
    }

    /**
     * Create an info item showing collection status.
     */
    private ItemStack createInfoItem(PlayerProgress progress) {
        int collected = progress != null ? progress.getCollectedCount(collection.id()) : 0;
        int total = collection.getItemCount();
        boolean complete = progress != null && progress.hasCompleted(collection.id());

        List<String> lore = new ArrayList<>();
        lore.add("<gray>─────────────────</gray>");

        // Description
        if (!collection.description().isEmpty()) {
            lore.add("<gray><italic>" + collection.description() + "</italic></gray>");
            lore.add("");
        }

        // Progress
        lore.add("<white>Status: </white>" + guiManager.createProgressBar(collected, total, 10));

        if (complete) {
            lore.add("<green>✓ Collection Complete!</green>");
        } else {
            int missing = total - collected;
            lore.add("<gray>" + missing + " item" + (missing != 1 ? "s" : "") + " remaining</gray>");
        }

        lore.add("");
        lore.add("<white>Tier: </white><" + collection.tier().getColor().toString().toLowerCase() + ">" +
                collection.tier().name() + "</" + collection.tier().getColor().toString().toLowerCase() + ">");

        if (!collection.allowedZones().isEmpty()) {
            String zones = String.join(", ", collection.allowedZones());
            lore.add("<gray>Zones: " + zones + "</gray>");
        }

        Material icon = complete ? Material.NETHER_STAR : Material.COMPASS;
        ItemBuilder builder = ItemBuilder.of(icon)
                .name("<gold>" + collection.name() + "</gold>")
                .lore(lore);

        if (complete) {
            builder.glowing();
        }

        return builder.build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        guiManager.playClickSound(player);

        if (slot == BACK_SLOT) {
            // Return to main menu
            guiManager.unregisterGUI(player.getUniqueId());
            CollectionMenuGUI menuGui = new CollectionMenuGUI(plugin, player);
            menuGui.open();
        } else if (slot == CLAIM_SLOT) {
            attemptClaimReward();
        }
    }

    /**
     * Attempt to claim the reward for this collection.
     */
    private void attemptClaimReward() {
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());

        if (progress == null) {
            player.sendMessage(configManager.getMessage("no-permission"));
            guiManager.playErrorSound(player);
            return;
        }

        if (!progress.hasCompleted(collection.id())) {
            player.sendMessage(configManager.getMessage("collection-incomplete",
                    "collection", collection.name()));
            guiManager.playErrorSound(player);
            return;
        }

        if (progress.hasClaimedReward(collection.id())) {
            player.sendMessage(configManager.getMessage("reward-already-claimed",
                    "collection", collection.name()));
            guiManager.playErrorSound(player);
            return;
        }

        // Use RewardManager to check space and give rewards
        RewardManager rewardManager = plugin.getRewardManager();
        Collection.CollectionRewards rewards = collection.rewards();

        if (!rewardManager.hasInventorySpace(player, rewards)) {
            player.sendMessage(configManager.getMessage("inventory-full"));
            guiManager.playErrorSound(player);
            return;
        }

        // Give rewards via RewardManager
        rewardManager.giveRewards(player, collection);

        // Mark as claimed
        playerDataManager.claimReward(player.getUniqueId(), collection.id());

        // Send success message
        player.sendMessage(configManager.getMessage("reward-claimed",
                "collection", collection.name()));

        // Refresh the GUI
        populateInventory();
    }

    /**
     * Format a material name nicely.
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalize = true;
                result.append(c);
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Nothing special to do on close
    }

    @Override
    public GUIType getType() {
        return GUIType.COLLECTION_DETAIL;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
