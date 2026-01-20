package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.SpawnManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles entity removal events to keep collectible tracking in sync.
 *
 * Paper's EntityRemoveEvent fires for ALL removal causes including:
 * - UNLOAD: Chunk unloading (temporary - mark unspawned, keep tracking)
 * - PLUGIN: Removed by plugin (permanent - remove from tracking)
 * - DEATH: Entity died (permanent)
 * - DESPAWN: Despawn rules (permanent)
 * - OUT_OF_WORLD: Fell out of world (permanent)
 * - And others (PICKUP, MERGE, etc. - shouldn't apply to Interaction entities)
 */
public class EntityRemoveListener implements Listener {

    private final Collections plugin;
    private final SpawnManager spawnManager;

    public EntityRemoveListener(Collections plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();

        // Quick check via PDC before expensive map lookup
        if (!entity.getPersistentDataContainer().has(
                spawnManager.getCollectibleKey(), PersistentDataType.BOOLEAN)) {
            return;
        }

        EntityRemoveEvent.Cause cause = event.getCause();
        boolean isUnload = (cause == EntityRemoveEvent.Cause.UNLOAD);

        // Delegate to SpawnManager
        spawnManager.handleEntityRemoved(entity.getUniqueId(), isUnload);

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Entity removed: " + entity.getUniqueId() +
                    " cause: " + cause + " (isUnload: " + isUnload + ")");
        }
    }
}
