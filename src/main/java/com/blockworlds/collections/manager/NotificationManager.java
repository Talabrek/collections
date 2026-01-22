package com.blockworlds.collections.manager;

import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.Collection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
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
}
