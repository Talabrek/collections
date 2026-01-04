package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;

/**
 * Handles chunk load/unload events to manage collectible entity lifecycle.
 */
public class ChunkListener implements Listener {

    private final Collections plugin;
    private final SpawnManager spawnManager;

    public ChunkListener(Collections plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Use region scheduler for Folia compatibility
        Bukkit.getRegionScheduler().runDelayed(plugin, chunk.getBlock(8, 64, 8).getLocation(), task -> {
            List<Collectible> collectibles = spawnManager.getCollectiblesInChunk(
                    world, chunk.getX(), chunk.getZ());

            for (Collectible collectible : collectibles) {
                if (!collectible.spawned()) {
                    spawnManager.recreateEntities(collectible);

                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("Recreated collectible entities in chunk " +
                                chunk.getX() + "," + chunk.getZ());
                    }
                }
            }
        }, 5L); // Small delay to ensure chunk is fully loaded
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        List<Collectible> collectibles = spawnManager.getCollectiblesInChunk(
                world, chunk.getX(), chunk.getZ());

        for (Collectible collectible : collectibles) {
            if (collectible.spawned()) {
                spawnManager.markUnspawned(collectible);

                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().info("Marked collectible unspawned in chunk " +
                            chunk.getX() + "," + chunk.getZ());
                }
            }
        }
    }
}
