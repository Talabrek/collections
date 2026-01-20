package com.blockworlds.collections.task;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Task that spawns tier-appropriate particles around collectibles.
 * Particles are only sent to players who can see the collectible.
 */
public class ParticleTask {

    // 2 chunks = 32 blocks, matching typical particle visibility
    private static final int PARTICLE_CHUNK_RADIUS = 2;

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
     * Spawn particles for all active collectibles visible to each player.
     * Uses chunk-based lookup for O(players x nearby_chunks) instead of O(players x all_collectibles).
     */
    private void spawnParticles() {
        // Get particle offset for floating effect (same for all collectibles this tick)
        double time = System.currentTimeMillis() / 1000.0;
        double yOffset = 0.5 + Math.sin(time * 2) * 0.1;

        // O(players) outer loop
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLoc = player.getLocation();
            World world = playerLoc.getWorld();
            if (world == null) continue;

            int playerChunkX = playerLoc.getBlockX() >> 4;
            int playerChunkZ = playerLoc.getBlockZ() >> 4;

            // O(1) chunk lookup per radius (typically 5x5 = 25 lookups)
            List<Collectible> nearby = spawnManager.getCollectiblesNearChunk(
                    playerChunkX, playerChunkZ, PARTICLE_CHUNK_RADIUS);

            // O(nearby collectibles) - much smaller than O(all collectibles)
            for (Collectible collectible : nearby) {
                // Skip if different world
                if (!world.equals(collectible.location().getWorld())) continue;

                // Distance check (finer than chunk granularity)
                if (playerLoc.distanceSquared(collectible.location()) > particleDistance * particleDistance) {
                    continue;
                }

                // Goggle visibility check
                if (!canPlayerSee(player, collectible)) continue;

                // Spawn particles using ParticleBuilder
                Location particleLoc = collectible.location().clone().add(0, yOffset, 0);
                spawnParticleForPlayer(player, particleLoc, collectible.tier().getParticle(), collectible.tier());
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
     * Spawn particles for a specific player using Paper's ParticleBuilder.
     */
    private void spawnParticleForPlayer(Player player, Location location, Particle particle, CollectibleTier tier) {
        // Different particle patterns based on tier
        switch (tier) {
            case COMMON -> {
                // Simple sparkle effect
                particle.builder()
                        .location(location)
                        .count(particleCount)
                        .offset(0.2, 0.2, 0.2)
                        .receivers(player)
                        .spawn();
            }
            case UNCOMMON -> {
                // Enchant spiral effect
                for (int i = 0; i < particleCount; i++) {
                    double angle = (System.currentTimeMillis() / 50.0 + i * 120) * Math.PI / 180;
                    double x = Math.cos(angle) * 0.3;
                    double z = Math.sin(angle) * 0.3;
                    particle.builder()
                            .location(location.clone().add(x, 0, z))
                            .count(1)
                            .offset(0, 0.1, 0)
                            .receivers(player)
                            .spawn();
                }
            }
            case RARE -> {
                // Elegant rising particles
                particle.builder()
                        .location(location)
                        .count(particleCount + 2)
                        .offset(0.15, 0.3, 0.15)
                        .extra(0.01)
                        .receivers(player)
                        .spawn();
            }
            case EVENT -> {
                // Celebratory burst
                particle.builder()
                        .location(location)
                        .count(particleCount + 3)
                        .offset(0.25, 0.25, 0.25)
                        .extra(0.02)
                        .receivers(player)
                        .spawn();
            }
        }
    }

    /**
     * Spawn a collection effect when a collectible is collected.
     * Uses ParticleBuilder with radius-based receivers for efficiency.
     */
    public void spawnCollectionEffect(Location location, CollectibleTier tier) {
        if (location.getWorld() == null) return;

        Particle particle = tier.getParticle();

        // Send to all nearby players using ParticleBuilder with radius
        particle.builder()
                .location(location.clone().add(0, 0.5, 0))
                .count(15)
                .offset(0.3, 0.3, 0.3)
                .extra(0.05)
                .receivers(particleDistance, false) // false = cubic distance check
                .spawn();
    }
}
