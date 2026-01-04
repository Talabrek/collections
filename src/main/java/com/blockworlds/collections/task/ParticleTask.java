package com.blockworlds.collections.task;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Task that spawns tier-appropriate particles around collectibles.
 * Particles are only sent to players who can see the collectible.
 */
public class ParticleTask {

    private final Collections plugin;
    private final SpawnManager spawnManager;
    private ScheduledTask task;

    // Configuration
    private int particleDistance;
    private int particleCount;

    public ParticleTask(Collections plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
    }

    /**
     * Start the particle task.
     */
    public void start() {
        // Load config
        particleDistance = plugin.getConfigManager().getParticleDistanceBlocks();
        int intervalTicks = plugin.getConfigManager().getParticleIntervalTicks();
        particleCount = 3; // Particles per spawn

        // Use async scheduler for particle calculations, but spawn on main thread per-region
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> {
            spawnParticles();
        }, 20L, intervalTicks);
    }

    /**
     * Stop the particle task.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Spawn particles for all active collectibles.
     */
    private void spawnParticles() {
        for (Collectible collectible : spawnManager.getActiveCollectibles()) {
            if (!collectible.spawned()) continue;

            Location loc = collectible.location();
            if (loc.getWorld() == null) continue;

            CollectibleTier tier = collectible.tier();
            Particle particle = tier.getParticle();

            // Get particle offset for a floating effect
            double time = System.currentTimeMillis() / 1000.0;
            double yOffset = 0.5 + Math.sin(time * 2) * 0.1; // Gentle bobbing

            Location particleLoc = loc.clone().add(0, yOffset, 0);

            // Send particles only to nearby players who can see this collectible
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) > particleDistance * particleDistance) {
                    continue;
                }

                // Check goggle visibility
                if (canPlayerSee(player, collectible)) {
                    spawnParticleForPlayer(player, particleLoc, particle, tier);
                }
            }
        }
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
     * Spawn particles for a specific player.
     */
    private void spawnParticleForPlayer(Player player, Location location, Particle particle, CollectibleTier tier) {
        // Different particle patterns based on tier
        switch (tier) {
            case COMMON -> {
                // Simple sparkle effect
                player.spawnParticle(particle, location, particleCount, 0.2, 0.2, 0.2, 0);
            }
            case UNCOMMON -> {
                // Enchant spiral effect
                for (int i = 0; i < particleCount; i++) {
                    double angle = (System.currentTimeMillis() / 50.0 + i * 120) * Math.PI / 180;
                    double x = Math.cos(angle) * 0.3;
                    double z = Math.sin(angle) * 0.3;
                    player.spawnParticle(particle, location.clone().add(x, 0, z), 1, 0, 0.1, 0, 0);
                }
            }
            case RARE -> {
                // Elegant rising particles
                player.spawnParticle(particle, location, particleCount + 2, 0.15, 0.3, 0.15, 0.01);
            }
            case EVENT -> {
                // Celebratory burst
                player.spawnParticle(particle, location, particleCount + 3, 0.25, 0.25, 0.25, 0.02);
            }
        }
    }

    /**
     * Spawn a collection effect when a collectible is collected.
     */
    public void spawnCollectionEffect(Location location, CollectibleTier tier) {
        if (location.getWorld() == null) return;

        // Burst of particles when collected
        Particle particle = tier.getParticle();

        // Send to all nearby players
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= particleDistance * particleDistance) {
                player.spawnParticle(particle, location.clone().add(0, 0.5, 0),
                        15, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }
}
