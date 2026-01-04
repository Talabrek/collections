package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.CollectionManager;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles player interaction with collectibles in the world.
 * When a player right-clicks a collectible, they receive a random item
 * from the associated collection.
 */
public class CollectibleInteractListener implements Listener {

    private final Collections plugin;
    private final ConfigManager configManager;
    private final SpawnManager spawnManager;
    private final CollectionManager collectionManager;
    private final PlayerDataManager playerDataManager;

    // Cooldown tracking: player UUID -> last collect timestamp
    private final Map<UUID, Long> lastCollectTime = new ConcurrentHashMap<>();

    // Race condition handling: collectible UUID -> lock
    private final Map<UUID, AtomicBoolean> collectLocks = new ConcurrentHashMap<>();

    public CollectibleInteractListener(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.spawnManager = plugin.getSpawnManager();
        this.collectionManager = plugin.getCollectionManager();
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    /**
     * Handle interaction with Interaction entities (the hitbox).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleInteraction(event.getPlayer(), event.getRightClicked());
        // Cancel to prevent other interactions
        if (isCollectible(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle interaction with ArmorStand entities (fallback for direct clicks).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleInteraction(event.getPlayer(), event.getRightClicked());
        // Cancel to prevent other interactions
        if (isCollectible(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if an entity is a collectible.
     */
    private boolean isCollectible(Entity entity) {
        return entity.getPersistentDataContainer()
                .has(spawnManager.getCollectibleKey(), PersistentDataType.BOOLEAN);
    }

    /**
     * Handle the collection interaction logic.
     */
    private void handleInteraction(Player player, Entity entity) {
        // Get the collectible from the entity
        Collectible collectible = spawnManager.getCollectibleByEntity(entity.getUniqueId());
        if (collectible == null) {
            return;
        }

        // Check goggle visibility - player must be able to see this tier
        GoggleManager goggleManager = plugin.getGoggleManager();
        if (goggleManager != null && !goggleManager.canPlayerSeeCollectible(player, collectible)) {
            // Player cannot see this collectible (shouldn't happen normally,
            // but could if they removed goggles right after clicking)
            return;
        }

        // Check cooldown (anti-macro)
        if (!checkCooldown(player)) {
            player.sendMessage(configManager.getMessage("cooldown-active"));
            return;
        }

        // Try to acquire lock for this collectible (race condition handling)
        AtomicBoolean lock = collectLocks.computeIfAbsent(collectible.id(), k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            // Another player is collecting this
            player.sendMessage(configManager.getMessage("already-collected"));
            return;
        }

        try {
            processCollection(player, collectible);
        } finally {
            // Always release the lock
            collectLocks.remove(collectible.id());
        }
    }

    /**
     * Check if player is on cooldown.
     *
     * @return true if player can collect, false if on cooldown
     */
    private boolean checkCooldown(Player player) {
        // Bypass permission
        if (player.hasPermission("collections.bypass.cooldown")) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long lastCollect = lastCollectTime.get(player.getUniqueId());

        if (lastCollect != null) {
            long cooldownMs = configManager.getCollectionCooldownMs();
            if (now - lastCollect < cooldownMs) {
                return false;
            }
        }

        // Update cooldown
        lastCollectTime.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Process the actual collection - generate item and give to player.
     */
    private void processCollection(Player player, Collectible collectible) {
        // Get the collection definition
        Collection collection = collectionManager.getCollection(collectible.collectionId());
        if (collection == null) {
            plugin.getLogger().warning("Collection not found: " + collectible.collectionId());
            return;
        }

        // Get the pre-selected item from the collectible (selected at spawn time based on conditions)
        // Fall back to random selection for backwards compatibility with old collectibles
        CollectionItem item;
        if (collectible.itemId() != null) {
            item = collection.getItem(collectible.itemId());
            if (item == null) {
                // Item no longer exists in collection, fall back to random
                plugin.getLogger().warning("Pre-selected item '" + collectible.itemId() +
                        "' not found in collection: " + collectible.collectionId());
                item = collection.getRandomItem();
            }
        } else {
            // Legacy collectible without pre-selected item
            item = collection.getRandomItem();
        }

        if (item == null) {
            plugin.getLogger().warning("No items available in collection: " + collectible.collectionId());
            return;
        }

        // Create the physical item
        ItemStack physicalItem = createCollectionItem(collection, item);

        // Try to give to player's inventory
        if (player.getInventory().firstEmpty() == -1) {
            // Inventory full - drop at feet
            player.getWorld().dropItemNaturally(player.getLocation(), physicalItem);
            player.sendMessage(configManager.getMessage("inventory-full"));
        } else {
            player.getInventory().addItem(physicalItem);
        }

        // Play sound
        String collectSound = configManager.getSound("collect-item");
        if (collectSound != null) {
            player.playSound(player.getLocation(), collectSound, 1.0f, 1.0f);
        }

        // Send message
        player.sendMessage(configManager.getMessage("item-collected", "item", item.name()));

        // Despawn the collectible
        spawnManager.despawnCollectible(collectible.id(), true);

        if (configManager.isDebugMode()) {
            plugin.getLogger().info(player.getName() + " collected " + item.id() +
                    " from " + collection.id() + " at " + collectible.location());
        }
    }

    /**
     * Create a physical collection item with proper tags.
     */
    private ItemStack createCollectionItem(Collection collection, CollectionItem item) {
        // Build lore with hint to add to journal
        List<String> fullLore = new ArrayList<>(item.lore());
        fullLore.add(""); // Empty line
        fullLore.add("<gray>Part of: <gold>" + collection.name() + "</gold></gray>");
        fullLore.add("<gray>Right-click to add to journal</gray>");

        if (item.soulbound()) {
            fullLore.add("<red>Soulbound</red>");
        }

        // Create item using ItemBuilder
        ItemBuilder builder = ItemBuilder.of(item.material())
                .name("<gold>" + item.name() + "</gold>")
                .lore(fullLore)
                .data(PDCKeys.COLLECTION_ID(), collection.id())
                .data(PDCKeys.ITEM_ID(), item.id())
                .data(PDCKeys.SOULBOUND(), item.soulbound() ? (byte) 1 : (byte) 0);

        return builder.build();
    }

    /**
     * Clean up cooldown data for a player (call on quit).
     */
    public void cleanupPlayer(UUID playerId) {
        lastCollectTime.remove(playerId);
    }
}
