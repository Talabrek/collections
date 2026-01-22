package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.CollectibleTier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages boss bar radar display for players wearing collector's helmets.
 * Shows nearby collectibles count and direction indicator.
 */
public class RadarManager {

    private final Collections plugin;

    // Active boss bars tracked by player UUID
    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    public RadarManager(Collections plugin) {
        this.plugin = plugin;
    }

    /**
     * Show the radar boss bar for a player.
     * Creates a new boss bar if one doesn't exist.
     *
     * @param player The player to show radar for
     */
    public void showRadar(Player player) {
        UUID playerId = player.getUniqueId();

        // Don't create duplicate bars
        if (activeBars.containsKey(playerId)) {
            return;
        }

        // Create boss bar with initial state
        BossBar bar = BossBar.bossBar(
                Component.text("Radar Loading...", NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );

        activeBars.put(playerId, bar);
        player.showBossBar(bar);

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Showed radar boss bar for " + player.getName());
        }
    }

    /**
     * Hide the radar boss bar for a player.
     *
     * @param player The player to hide radar for
     */
    public void hideRadar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bar = activeBars.remove(playerId);

        if (bar != null) {
            player.hideBossBar(bar);

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Hid radar boss bar for " + player.getName());
            }
        }
    }

    /**
     * Update the radar boss bar with current nearby collectible information.
     *
     * @param player            The player to update radar for
     * @param count             Number of nearby collectibles
     * @param directionIndicator Direction indicator string (e.g., "[^]", "[<]", "[>]")
     * @param highestTier       Highest tier among visible collectibles (for color)
     */
    public void updateRadar(Player player, int count, String directionIndicator, CollectibleTier highestTier) {
        UUID playerId = player.getUniqueId();
        BossBar bar = activeBars.get(playerId);

        if (bar == null) {
            return;
        }

        // Build title component
        Component title;
        if (count == 0) {
            title = Component.text("No collectibles nearby", NamedTextColor.GRAY);
        } else {
            title = Component.text(count + " nearby ", NamedTextColor.WHITE)
                    .append(Component.text(directionIndicator, getTierTextColor(highestTier)));
        }

        // Update bar properties
        bar.name(title);
        bar.color(getTierBossBarColor(highestTier));
    }

    /**
     * Check if a player currently has a radar boss bar active.
     *
     * @param player The player to check
     * @return true if player has an active radar
     */
    public boolean hasRadar(Player player) {
        return activeBars.containsKey(player.getUniqueId());
    }

    /**
     * Clean up radar for a player (safe if no radar exists).
     * Used on disconnect to prevent memory leaks.
     *
     * @param player The player to clean up
     */
    public void cleanup(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bar = activeBars.remove(playerId);

        if (bar != null) {
            // Try to hide, but player may already be disconnected
            try {
                player.hideBossBar(bar);
            } catch (Exception ignored) {
                // Player already disconnected
            }
        }
    }

    /**
     * Get the boss bar color for a collectible tier.
     */
    private BossBar.Color getTierBossBarColor(CollectibleTier tier) {
        if (tier == null) {
            return BossBar.Color.WHITE;
        }

        return switch (tier) {
            case COMMON -> BossBar.Color.WHITE;
            case UNCOMMON -> BossBar.Color.GREEN;
            case RARE -> BossBar.Color.BLUE;
            case EPIC -> BossBar.Color.PURPLE;
            case LEGENDARY -> BossBar.Color.YELLOW;
            case EVENT -> BossBar.Color.PINK;
        };
    }

    /**
     * Get the text color for a collectible tier.
     */
    private NamedTextColor getTierTextColor(CollectibleTier tier) {
        if (tier == null) {
            return NamedTextColor.WHITE;
        }

        return tier.getColor();
    }
}
