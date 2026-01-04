package com.blockworlds.collections.task;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Task that displays action bar prompts when players are looking at collectibles.
 * Checks if players are within range and looking at a collectible,
 * then sends a prompt to right-click to collect.
 */
public class ActionBarPromptTask {

    private static final double MAX_DISTANCE = 5.0;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;
    private static final double LOOK_THRESHOLD = 0.95; // ~18 degree cone

    private final Collections plugin;
    private final SpawnManager spawnManager;
    private ScheduledTask task;

    public ActionBarPromptTask(Collections plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
    }

    /**
     * Start the action bar prompt task.
     */
    public void start() {
        // Run every 5 ticks (0.25 seconds) for responsive feedback
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            checkPlayers();
        }, 20L, 5L);
    }

    /**
     * Stop the action bar prompt task.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Check all online players for collectible focus.
     */
    private void checkPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Collectible target = findLookedAtCollectible(player);
            if (target != null && canPlayerSee(player, target)) {
                sendPrompt(player, target.tier());
            }
        }
    }

    /**
     * Find the collectible the player is looking at, if any.
     *
     * @param player The player to check
     * @return The collectible being looked at, or null if none
     */
    private Collectible findLookedAtCollectible(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        Collectible closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Collectible collectible : spawnManager.getActiveCollectibles()) {
            if (!collectible.spawned()) continue;

            Location loc = collectible.location();
            if (loc.getWorld() == null || !loc.getWorld().equals(player.getWorld())) continue;

            // Check distance
            double distanceSquared = eye.distanceSquared(loc);
            if (distanceSquared > MAX_DISTANCE_SQUARED) continue;

            // Check if looking at it
            Vector toTarget = loc.toVector().subtract(eye.toVector()).normalize();
            double dot = direction.dot(toTarget);

            if (dot > LOOK_THRESHOLD && distanceSquared < closestDistance) {
                closest = collectible;
                closestDistance = distanceSquared;
            }
        }

        return closest;
    }

    /**
     * Check if a player can see a collectible based on goggles.
     */
    private boolean canPlayerSee(Player player, Collectible collectible) {
        GoggleManager goggleManager = plugin.getGoggleManager();
        if (goggleManager == null) {
            // Fallback: only show COMMON tier if GoggleManager not initialized
            return collectible.tier() == CollectibleTier.COMMON;
        }
        return goggleManager.canPlayerSeeCollectible(player, collectible);
    }

    /**
     * Send the action bar prompt to a player.
     */
    private void sendPrompt(Player player, CollectibleTier tier) {
        Component message = Component.text("Right-click to collect", tier.getColor())
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(tier.getDisplayName(), tier.getColor()))
                .append(Component.text("]", NamedTextColor.GRAY));

        player.sendActionBar(message);
    }
}
