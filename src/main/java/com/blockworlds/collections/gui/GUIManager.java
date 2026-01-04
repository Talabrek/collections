package com.blockworlds.collections.gui;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GUI utilities and tracks open GUIs.
 */
public class GUIManager {

    private final Collections plugin;
    private final ConfigManager configManager;

    // Track open GUIs: player UUID -> GUI holder
    private final Map<UUID, GUIHolder> openGuis = new ConcurrentHashMap<>();

    // Progress bar characters
    private static final char FILLED_CHAR = '\u2588';  // █
    private static final char EMPTY_CHAR = '\u2591';   // ░

    public GUIManager(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    // ========== GUI Registration ==========

    /**
     * Register an open GUI for a player.
     *
     * @param playerId The player's UUID
     * @param holder   The GUI holder
     */
    public void registerGUI(UUID playerId, GUIHolder holder) {
        openGuis.put(playerId, holder);
    }

    /**
     * Unregister a GUI when closed.
     *
     * @param playerId The player's UUID
     */
    public void unregisterGUI(UUID playerId) {
        openGuis.remove(playerId);
    }

    /**
     * Get the open GUI for a player.
     *
     * @param playerId The player's UUID
     * @return The GUI holder, or null if none open
     */
    public GUIHolder getOpenGUI(UUID playerId) {
        return openGuis.get(playerId);
    }

    /**
     * Check if a player has an open GUI.
     *
     * @param playerId The player's UUID
     * @return true if the player has an open GUI
     */
    public boolean hasOpenGUI(UUID playerId) {
        return openGuis.containsKey(playerId);
    }

    // ========== Item Builders ==========

    /**
     * Create a filler item (gray glass pane).
     *
     * @return The filler item
     */
    public ItemStack createFiller() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.text(" "))
                .build();
    }

    /**
     * Create a previous page button.
     *
     * @param currentPage The current page (0-indexed)
     * @return The previous button, or filler if on first page
     */
    public ItemStack createPrevButton(int currentPage) {
        if (currentPage <= 0) {
            return createFiller();
        }

        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>◀ Previous Page</yellow>")
                .addLore("<gray>Click to go to page " + currentPage + "</gray>")
                .build();
    }

    /**
     * Create a next page button.
     *
     * @param currentPage The current page (0-indexed)
     * @param maxPage     The maximum page (0-indexed)
     * @return The next button, or filler if on last page
     */
    public ItemStack createNextButton(int currentPage, int maxPage) {
        if (currentPage >= maxPage) {
            return createFiller();
        }

        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>Next Page ▶</yellow>")
                .addLore("<gray>Click to go to page " + (currentPage + 2) + "</gray>")
                .build();
    }

    /**
     * Create a page indicator.
     *
     * @param currentPage The current page (0-indexed)
     * @param maxPage     The maximum page (0-indexed)
     * @return The page indicator item
     */
    public ItemStack createPageIndicator(int currentPage, int maxPage) {
        return ItemBuilder.of(Material.PAPER)
                .name("<gold>Page " + (currentPage + 1) + "/" + (maxPage + 1) + "</gold>")
                .build();
    }

    /**
     * Create a back button.
     *
     * @return The back button
     */
    public ItemStack createBackButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("<red>◀ Back</red>")
                .addLore("<gray>Return to previous menu</gray>")
                .build();
    }

    /**
     * Create a close button.
     *
     * @return The close button
     */
    public ItemStack createCloseButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("<red>✕ Close</red>")
                .addLore("<gray>Close this menu</gray>")
                .build();
    }

    /**
     * Create a sort button.
     *
     * @param currentSort The current sort type name
     * @return The sort button
     */
    public ItemStack createSortButton(String currentSort) {
        return ItemBuilder.of(Material.HOPPER)
                .name("<aqua>Sort: " + currentSort + "</aqua>")
                .addLore("<gray>Click to change sort order</gray>")
                .build();
    }

    /**
     * Create a filter button.
     *
     * @param currentFilter The current filter name
     * @return The filter button
     */
    public ItemStack createFilterButton(String currentFilter) {
        return ItemBuilder.of(Material.COMPARATOR)
                .name("<aqua>Filter: " + currentFilter + "</aqua>")
                .addLore("<gray>Click to change filter</gray>")
                .build();
    }

    /**
     * Create a stats button.
     *
     * @return The stats button
     */
    public ItemStack createStatsButton() {
        return ItemBuilder.of(Material.BOOK)
                .name("<green>📊 Your Stats</green>")
                .addLore("<gray>View your collection statistics</gray>")
                .build();
    }

    /**
     * Create a confirm button.
     *
     * @return The confirm button
     */
    public ItemStack createConfirmButton() {
        return ItemBuilder.of(Material.LIME_CONCRETE)
                .name("<green>✓ Confirm</green>")
                .addLore("<gray>Add this item to your journal</gray>")
                .build();
    }

    /**
     * Create a cancel button.
     *
     * @return The cancel button
     */
    public ItemStack createCancelButton() {
        return ItemBuilder.of(Material.RED_CONCRETE)
                .name("<red>✕ Cancel</red>")
                .addLore("<gray>Keep the item in your inventory</gray>")
                .build();
    }

    /**
     * Create a claim reward button.
     *
     * @param canClaim Whether the reward can be claimed
     * @param claimed  Whether the reward has been claimed
     * @return The claim button
     */
    public ItemStack createClaimButton(boolean canClaim, boolean claimed) {
        if (claimed) {
            return ItemBuilder.of(Material.LIME_CONCRETE)
                .name("<gray>✓ Rewards Claimed</gray>")
                .addLore("<gray>You have already claimed this reward</gray>")
                .build();
        } else if (canClaim) {
            return ItemBuilder.of(Material.GREEN_CONCRETE)
                .name("<green>Claim Reward!</green>")
                .addLore("<yellow>Click to claim your rewards</yellow>")
                .glowing()
                .build();
        } else {
            return ItemBuilder.of(Material.RED_CONCRETE)
                .name("<red>Complete Collection First</red>")
                .addLore("<gray>Collect all items to unlock rewards</gray>")
                .build();
        }
    }

    // ========== Progress Bar ==========

    /**
     * Create a progress bar string.
     *
     * @param current   Current progress value
     * @param max       Maximum value
     * @param barLength Number of characters in the bar
     * @return The progress bar string (e.g., "████░░░░░░ 4/10")
     */
    public String createProgressBar(int current, int max, int barLength) {
        if (max <= 0) {
            max = 1;
        }
        if (current > max) {
            current = max;
        }

        int filledCount = (int) Math.round((double) current / max * barLength);
        int emptyCount = barLength - filledCount;

        StringBuilder bar = new StringBuilder();

        // Filled portion (green)
        bar.append("<green>");
        for (int i = 0; i < filledCount; i++) {
            bar.append(FILLED_CHAR);
        }
        bar.append("</green>");

        // Empty portion (gray)
        bar.append("<gray>");
        for (int i = 0; i < emptyCount; i++) {
            bar.append(EMPTY_CHAR);
        }
        bar.append("</gray>");

        // Count
        bar.append(" <white>").append(current).append("/").append(max).append("</white>");

        return bar.toString();
    }

    /**
     * Create a progress bar as a Component.
     *
     * @param current   Current progress value
     * @param max       Maximum value
     * @param barLength Number of characters in the bar
     * @return The progress bar component
     */
    public Component createProgressBarComponent(int current, int max, int barLength) {
        if (max <= 0) {
            max = 1;
        }
        if (current > max) {
            current = max;
        }

        int filledCount = (int) Math.round((double) current / max * barLength);
        int emptyCount = barLength - filledCount;

        StringBuilder filledPart = new StringBuilder();
        for (int i = 0; i < filledCount; i++) {
            filledPart.append(FILLED_CHAR);
        }

        StringBuilder emptyPart = new StringBuilder();
        for (int i = 0; i < emptyCount; i++) {
            emptyPart.append(EMPTY_CHAR);
        }

        return Component.text()
                .append(Component.text(filledPart.toString(), NamedTextColor.GREEN))
                .append(Component.text(emptyPart.toString(), NamedTextColor.GRAY))
                .append(Component.text(" " + current + "/" + max, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    // ========== Sound Effects ==========

    /**
     * Play the GUI open sound.
     *
     * @param player The player to play the sound for
     */
    public void playOpenSound(Player player) {
        String sound = configManager.getSound("gui-open");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Play the GUI click sound.
     *
     * @param player The player to play the sound for
     */
    public void playClickSound(Player player) {
        String sound = configManager.getSound("gui-click");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }

    /**
     * Play a success sound.
     *
     * @param player The player to play the sound for
     */
    public void playSuccessSound(Player player) {
        String sound = configManager.getSound("gui-success");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Play an error sound.
     *
     * @param player The player to play the sound for
     */
    public void playErrorSound(Player player) {
        String sound = configManager.getSound("gui-error");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Play the claim reward sound.
     *
     * @param player The player to play the sound for
     */
    public void playClaimSound(Player player) {
        String sound = configManager.getSound("gui-claim-reward");
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    // ========== Utility Methods ==========

    /**
     * Get the plugin instance.
     *
     * @return The plugin
     */
    public Collections getPlugin() {
        return plugin;
    }

    /**
     * Clean up when a player quits.
     *
     * @param playerId The player's UUID
     */
    public void cleanupPlayer(UUID playerId) {
        openGuis.remove(playerId);
    }
}
