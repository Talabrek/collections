package com.blockworlds.collections.manager;

import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.PlayerProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Manages player notifications for collection progress and completion.
 * Supports multiple notification styles: actionbar, chat, title.
 */
public class NotificationManager {

    private final ConfigManager configManager;

    public NotificationManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Send progress notification when player adds item to journal.
     *
     * @param player     The player
     * @param collection The collection
     * @param current    Current items collected
     * @param total      Total items in collection
     */
    public void sendProgressNotification(Player player, Collection collection,
                                          int current, int total) {
        String style = configManager.getProgressNotificationStyle();

        if ("none".equalsIgnoreCase(style)) {
            return;
        }

        String format = configManager.getProgressNotificationFormat();
        Component message = configManager.parse(format,
            "current", String.valueOf(current),
            "total", String.valueOf(total),
            "collection", collection.name()
        );

        switch (style.toLowerCase()) {
            case "actionbar" -> player.sendActionBar(message);
            case "chat" -> player.sendMessage(message);
            case "title" -> player.showTitle(Title.title(
                Component.empty(),
                message,
                Title.Times.times(
                    Duration.ofMillis(250),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(250)
                )
            ));
            default -> player.sendActionBar(message); // Default to actionbar
        }
    }

    /**
     * Send completion notification when player completes a collection.
     *
     * @param player     The player
     * @param collection The completed collection
     */
    public void sendCompletionNotification(Player player, Collection collection) {
        String style = configManager.getCompletionNotificationStyle();

        if ("none".equalsIgnoreCase(style)) {
            return;
        }

        boolean showTitle = "title".equalsIgnoreCase(style) || "both".equalsIgnoreCase(style);
        boolean showChat = "chat".equalsIgnoreCase(style) || "both".equalsIgnoreCase(style);

        if (showTitle) {
            sendCompletionTitle(player, collection);
        }

        if (showChat) {
            // Use existing message from config
            player.sendMessage(configManager.getMessage("collection-complete",
                "collection", collection.name()));
        }
    }

    /**
     * Send the completion title/subtitle.
     */
    private void sendCompletionTitle(Player player, Collection collection) {
        String titleFormat = configManager.getCompletionTitle();
        String subtitleFormat = configManager.getCompletionSubtitle();

        Component title = configManager.parse(titleFormat,
            "collection", collection.name()
        );
        Component subtitle = configManager.parse(subtitleFormat,
            "collection", collection.name()
        );

        Title.Times times = Title.Times.times(
            Duration.ofMillis((long) (configManager.getCompletionFadeIn() * 1000)),
            Duration.ofMillis((long) (configManager.getCompletionStay() * 1000)),
            Duration.ofMillis((long) (configManager.getCompletionFadeOut() * 1000))
        );

        player.showTitle(Title.title(title, subtitle, times));
    }

    /**
     * Check and send milestone notifications after adding an item.
     * Milestones are 25%, 50%, and 75% collection progress.
     * Each milestone triggers exactly once per collection.
     *
     * @param player The player
     * @param collection The collection
     * @param progress The player's progress (to check/update milestone state)
     * @param currentCount Current items collected (after adding)
     * @param totalCount Total items in collection
     */
    public void checkMilestoneNotifications(Player player, Collection collection,
                                             PlayerProgress.CollectionProgress progress,
                                             int currentCount, int totalCount) {
        if (!configManager.isMilestonesEnabled()) {
            return;
        }

        // Don't check milestones if completing the collection (100%)
        // The completion notification handles that case
        if (currentCount >= totalCount) {
            return;
        }

        int percentComplete = (currentCount * 100) / totalCount;

        // Check milestones in order: 75%, 50%, 25%
        // Only fire the HIGHEST newly reached milestone to avoid spam
        int[] milestones = {75, 50, 25}; // Check highest first
        for (int milestone : milestones) {
            if (percentComplete >= milestone && !progress.hasMilestone(milestone)) {
                // Mark milestone as triggered BEFORE sending notification
                // This prevents re-triggering if save fails
                progress.setMilestone(milestone);
                sendMilestoneNotification(player, collection, milestone);
                break; // Only fire one milestone per item add
            }
        }
    }

    /**
     * Send a milestone notification to the player.
     *
     * @param player The player
     * @param collection The collection
     * @param milestone The milestone percentage (25, 50, or 75)
     */
    public void sendMilestoneNotification(Player player, Collection collection, int milestone) {
        String style = configManager.getMilestoneStyle(milestone);

        if ("none".equalsIgnoreCase(style)) {
            return;
        }

        String format = configManager.getMilestoneFormat(milestone);
        Component message = configManager.parse(format,
            "collection", collection.name(),
            "percent", String.valueOf(milestone)
        );

        // Send notification based on style
        switch (style.toLowerCase()) {
            case "actionbar" -> player.sendActionBar(message);
            case "chat" -> player.sendMessage(message);
            case "subtitle" -> {
                // Show only subtitle (empty title)
                player.showTitle(Title.title(
                    Component.empty(),
                    message,
                    Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(250)
                    )
                ));
            }
            case "title" -> {
                // Show full title with subtitle
                String titleFormat = configManager.getMilestoneTitle(milestone);
                Component title = configManager.parse(titleFormat,
                    "collection", collection.name(),
                    "percent", String.valueOf(milestone)
                );
                player.showTitle(Title.title(
                    title,
                    message,
                    Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500)
                    )
                ));
            }
            default -> player.sendActionBar(message); // Fallback to actionbar
        }

        // Play sound
        String sound = configManager.getMilestoneSound(milestone);
        if (sound != null && !sound.isEmpty()) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }

        // Spawn particles if enabled
        if (configManager.getMilestoneParticles(milestone)) {
            spawnMilestoneParticles(player, milestone);
        }
    }

    /**
     * Spawn celebratory particles for a milestone.
     * Intensity scales with milestone percentage.
     */
    private void spawnMilestoneParticles(Player player, int milestone) {
        Location location = player.getLocation().add(0, 1, 0);

        // Scale particle count by milestone
        int count = switch (milestone) {
            case 25 -> 10;
            case 50 -> 20;
            case 75 -> 30;
            default -> 15;
        };

        // Use HAPPY_VILLAGER for a celebratory effect
        Particle.HAPPY_VILLAGER.builder()
                .location(location)
                .count(count)
                .offset(0.5, 0.5, 0.5)
                .extra(0.1)
                .receivers(player)
                .spawn();
    }
}
