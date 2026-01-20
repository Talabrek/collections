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
 *
 * <p>COORDINATION WITH EntityRemoveListener:</p>
 * <p>Both ChunkListener and EntityRemoveListener may handle the same events:
 * <ul>
 *   <li>Chunk unload: ChunkUnloadEvent fires here, EntityRemoveEvent (UNLOAD cause) fires in EntityRemoveListener</li>
 *   <li>Chunk load: ChunkLoadEvent fires here to recreate entities</li>
 * </ul>
 * All operations are designed to be idempotent - safe to run multiple times for the same collectible.
 * </p>
 *
 * <p>FUTURE IMPROVEMENT: Consider using EntitiesLoadEvent instead of ChunkLoadEvent + delay,
 * as it fires when entities are guaranteed to be loaded with the chunk.</p>
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
        // 5-tick delay ensures chunk is fully loaded (entities may not be available immediately)
        Bukkit.getRegionScheduler().runDelayed(plugin, chunk.getBlock(8, 64, 8).getLocation(), task -> {
            List<Collectible> collectibles = spawnManager.getCollectiblesInChunk(
                    world, chunk.getX(), chunk.getZ());

            for (Collectible collectible : collectibles) {
                // Re-fetch current state - EntityRemoveListener may have already processed this collectible
                // during the delay period (e.g., if entity was removed by /kill while chunk was loading)
                Collectible current = spawnManager.getCollectible(collectible.id());
                if (current == null) {
                    // Collectible was fully removed during delay - skip
                    continue;
                }

                // Only recreate if still unspawned (idempotent check)
                if (!current.spawned()) {
                    spawnManager.recreateEntities(current);

                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("Recreated collectible " + current.id() +
                                " entities in chunk " + chunk.getX() + "," + chunk.getZ());
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
