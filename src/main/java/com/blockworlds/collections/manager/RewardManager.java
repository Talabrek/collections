package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.Collection.CollectionRewards;
import com.blockworlds.collections.model.Collection.RewardItem;
import com.blockworlds.collections.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages reward distribution for completed collections.
 */
public class RewardManager {

    private final Collections plugin;
    private final ConfigManager configManager;

    public RewardManager(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Give all rewards for a completed collection to a player.
     *
     * @param player     The player to reward
     * @param collection The completed collection
     * @return true if rewards were given successfully
     */
    public boolean giveRewards(Player player, Collection collection) {
        CollectionRewards rewards = collection.rewards();

        // Give experience
        if (rewards.experience() > 0) {
            giveExperience(player, rewards.experience());
        }

        // Give items
        if (!rewards.items().isEmpty()) {
            giveItems(player, rewards);
        }

        // Execute commands
        if (!rewards.commands().isEmpty()) {
            executeCommands(player, rewards);
        }

        // Send custom message
        if (!rewards.message().isEmpty()) {
            sendMessage(player, rewards.message());
        }

        // Spawn fireworks
        if (rewards.fireworks()) {
            spawnFireworks(player);
        }

        // Play sound
        playRewardSound(player, rewards.sound());

        return true;
    }

    /**
     * Give experience points to a player.
     */
    private void giveExperience(Player player, int amount) {
        player.giveExp(amount);

        if (configManager.isDebugMode()) {
            plugin.getLogger().info("Gave " + amount + " XP to " + player.getName());
        }
    }

    /**
     * Give reward items to a player.
     */
    private void giveItems(Player player, CollectionRewards rewards) {
        for (RewardItem rewardItem : rewards.items()) {
            ItemStack item = createRewardItem(rewardItem);

            // Try to add to inventory
            var leftover = player.getInventory().addItem(item);

            // Drop any that didn't fit
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                player.sendMessage(configManager.getMessage("inventory-full"));
            }

            if (configManager.isDebugMode()) {
                plugin.getLogger().info("Gave " + rewardItem.amount() + "x " +
                        rewardItem.material().name() + " to " + player.getName());
            }
        }
    }

    /**
     * Create an ItemStack from a RewardItem configuration.
     */
    private ItemStack createRewardItem(RewardItem rewardItem) {
        ItemBuilder builder = ItemBuilder.of(rewardItem.material())
                .amount(rewardItem.amount());

        if (!rewardItem.name().isEmpty()) {
            builder.name("<gold>" + rewardItem.name() + "</gold>");
        }

        if (!rewardItem.lore().isEmpty()) {
            builder.lore(rewardItem.lore());
        }

        return builder.build();
    }

    /**
     * Execute console commands for rewards.
     */
    private void executeCommands(Player player, CollectionRewards rewards) {
        for (String command : rewards.commands()) {
            String parsed = command
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);

            if (configManager.isDebugMode()) {
                plugin.getLogger().info("Executed reward command: " + parsed);
            }
        }
    }

    /**
     * Send a custom reward message to the player.
     */
    private void sendMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(configManager.parse(message));
        }
    }

    /**
     * Spawn celebration fireworks at the player's location.
     */
    private void spawnFireworks(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        Firework firework = player.getWorld().spawn(loc, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();

            // Random colors for celebration
            Color[] colors = {Color.YELLOW, Color.ORANGE, Color.RED, Color.LIME, Color.AQUA};
            Color primary = colors[ThreadLocalRandom.current().nextInt(colors.length)];
            Color fade = colors[ThreadLocalRandom.current().nextInt(colors.length)];

            FireworkEffect effect = FireworkEffect.builder()
                    .withColor(primary)
                    .withFade(fade)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .flicker(true)
                    .build();

            meta.addEffect(effect);
            meta.setPower(1); // Short fuse

            fw.setFireworkMeta(meta);
        });

        // Detonate after a short delay
        Bukkit.getScheduler().runTaskLater(plugin, firework::detonate, 10L);
    }

    /**
     * Play the reward sound for the player.
     */
    private void playRewardSound(Player player, String customSound) {
        String sound = customSound;

        // Fall back to default if no custom sound
        if (sound == null || sound.isEmpty()) {
            sound = configManager.getSound("claim-reward");
        }

        if (sound != null && !sound.isEmpty()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Calculate the number of inventory slots required for reward items.
     *
     * @param rewards The rewards to check
     * @return Number of slots needed
     */
    public int getRequiredSlots(CollectionRewards rewards) {
        return rewards.items().size();
    }

    /**
     * Check if a player has enough inventory space for rewards.
     *
     * @param player  The player to check
     * @param rewards The rewards to give
     * @return true if player has enough space
     */
    public boolean hasInventorySpace(Player player, CollectionRewards rewards) {
        int required = getRequiredSlots(rewards);
        if (required == 0) {
            return true;
        }

        int emptySlots = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        return emptySlots >= required;
    }

    /**
     * Get a formatted list of rewards for display.
     *
     * @param rewards The rewards to format
     * @return Formatted string describing rewards
     */
    public String formatRewards(CollectionRewards rewards) {
        StringBuilder sb = new StringBuilder();

        if (rewards.experience() > 0) {
            sb.append(rewards.experience()).append(" XP");
        }

        if (!rewards.items().isEmpty()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(rewards.items().size()).append(" item(s)");
        }

        if (!rewards.commands().isEmpty()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("special rewards");
        }

        return sb.isEmpty() ? "None" : sb.toString();
    }
}
