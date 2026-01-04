package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.SpawnZone;
import com.blockworlds.collections.spawn.AdaptiveSpawnFinder;
import com.blockworlds.collections.spawn.SpawnResult;
import com.blockworlds.collections.storage.Storage;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages the lifecycle of collectibles in the world.
 * Handles spawning, despawning, tracking, and respawning.
 */
public class SpawnManager {

    private final Collections plugin;
    private final ZoneManager zoneManager;
    private final CollectionManager collectionManager;
    private final Storage storage;
    private final AdaptiveSpawnFinder spawnFinder;

    // Active collectibles tracked by ID
    private final Map<UUID, Collectible> activeCollectibles = new ConcurrentHashMap<>();

    // Count of collectibles per zone
    private final Map<String, Integer> collectibleCountByZone = new ConcurrentHashMap<>();

    // Respawn timers per zone (zone ID -> next spawn time)
    private final Map<String, Long> respawnTimers = new ConcurrentHashMap<>();

    // NamespacedKeys for entity metadata
    private final NamespacedKey COLLECTIBLE_KEY;
    private final NamespacedKey COLLECTIBLE_ID_KEY;
    private final NamespacedKey COLLECTION_KEY;
    private final NamespacedKey TIER_KEY;

    // Spawn task
    private ScheduledTask spawnTask;
    private ScheduledTask validityTask;

    public SpawnManager(Collections plugin) {
        this.plugin = plugin;
        this.zoneManager = plugin.getZoneManager();
        this.collectionManager = plugin.getCollectionManager();
        this.storage = plugin.getStorage();
        this.spawnFinder = new AdaptiveSpawnFinder(plugin, plugin.getConfigManager());

        // Initialize keys
        COLLECTIBLE_KEY = new NamespacedKey(plugin, "collectible");
        COLLECTIBLE_ID_KEY = new NamespacedKey(plugin, "collectible_id");
        COLLECTION_KEY = new NamespacedKey(plugin, "collection_id");
        TIER_KEY = new NamespacedKey(plugin, "tier");
    }

    /**
     * Initialize the spawn manager - load existing collectibles and start tasks.
     */
    public void initialize() {
        // Load existing collectibles from database, then start tasks
        // This prevents race conditions where spawn task runs before load completes
        loadExistingCollectibles()
                .thenRun(() -> {
                    // Start spawn check task after load completes
                    startSpawnTask();

                    // Start validity check task
                    startValidityTask();

                    plugin.getLogger().info("SpawnManager initialized with " + activeCollectibles.size() + " active collectibles");
                });
    }

    /**
     * Shutdown the spawn manager - stop tasks.
     */
    public void shutdown() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        if (validityTask != null) {
            validityTask.cancel();
        }
    }

    /**
     * Load existing collectibles from the database.
     *
     * @return CompletableFuture that completes when loading is done
     */
    private CompletableFuture<Void> loadExistingCollectibles() {
        return storage.loadAllCollectibles()
                .thenAccept(collectibles -> {
                    for (Collectible collectible : collectibles) {
                        activeCollectibles.put(collectible.id(), collectible);
                        collectibleCountByZone.merge(collectible.zoneId(), 1, Integer::sum);
                    }
                    plugin.getLogger().info("Loaded " + collectibles.size() + " collectibles from database");
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to load collectibles", throwable);
                    return null;
                });
    }

    /**
     * Start the periodic spawn check task.
     */
    private void startSpawnTask() {
        int intervalTicks = plugin.getConfigManager().getSpawnCheckIntervalSeconds() * 20;

        // Use GlobalRegionScheduler for Folia compatibility
        spawnTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            checkAndSpawnCollectibles();
        }, 100L, intervalTicks);
    }

    /**
     * Start the periodic validity check task.
     */
    private void startValidityTask() {
        int intervalTicks = plugin.getConfigManager().getValidityCheckIntervalMinutes() * 60 * 20;

        validityTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            validateActiveCollectibles();
        }, 200L, intervalTicks);
    }

    /**
     * Check all zones and spawn collectibles where needed.
     */
    private void checkAndSpawnCollectibles() {
        for (SpawnZone zone : zoneManager.getAllZones().values()) {
            if (!zone.enabled()) continue;

            int currentCount = collectibleCountByZone.getOrDefault(zone.id(), 0);
            if (currentCount >= zone.maxCollectibles()) continue;

            // Check respawn timer
            Long nextSpawn = respawnTimers.get(zone.id());
            if (nextSpawn != null && System.currentTimeMillis() < nextSpawn) continue;

            // Try to spawn a collectible
            attemptSpawnInZone(zone);
        }
    }

    /**
     * Attempt to spawn a collectible in a zone.
     */
    private void attemptSpawnInZone(SpawnZone zone) {
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) return;

        // Get a valid spawn location using adaptive finder
        SpawnResult result = findSpawnLocation(zone);
        if (!result.success()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("Failed to find spawn location in zone " + zone.id() +
                        ": " + result.stats().getSummary());
            }
            return;
        }

        Location spawnLoc = result.location();

        // Select a random collection that passes spawn conditions at this location
        Collection collection = selectRandomCollection(zone, spawnLoc);
        if (collection == null) return;

        // Select a random item that passes spawn conditions at this location
        CollectionItem item = selectRandomItem(collection, spawnLoc);
        if (item == null) return;

        // Spawn the collectible with the pre-selected item
        spawnCollectible(spawnLoc, zone, collection, item);
    }

    /**
     * Find a valid spawn location in a zone using the adaptive spawn finder.
     *
     * @param zone The zone to find a spawn location in
     * @return SpawnResult containing location or failure statistics
     */
    public SpawnResult findSpawnLocation(SpawnZone zone) {
        return spawnFinder.findLocation(zone);
    }

    /**
     * Find a valid spawn location in a zone (legacy method for internal use).
     */
    private Location findSpawnLocationLegacy(SpawnZone zone, World world) {
        SpawnResult result = findSpawnLocation(zone);
        return result.success() ? result.location() : null;
    }

    /**
     * Select a random collection that can spawn in this zone at the given location.
     * Filters by collection-level spawn conditions.
     *
     * @param zone     The zone to spawn in
     * @param location The spawn location to validate conditions against
     * @return A valid collection, or null if none found
     */
    private Collection selectRandomCollection(SpawnZone zone, Location location) {
        List<Collection> candidateCollections = new ArrayList<>();

        if (zone.collections().isEmpty()) {
            // All collections allowed
            candidateCollections.addAll(collectionManager.getAllCollections().values());
        } else {
            for (String collectionId : zone.collections()) {
                Collection collection = collectionManager.getCollection(collectionId);
                if (collection != null) {
                    candidateCollections.add(collection);
                }
            }
        }

        // Filter by collection-level spawn conditions
        List<Collection> validCollections = new ArrayList<>();
        for (Collection collection : candidateCollections) {
            // Check collection spawn conditions
            if (collection.spawnConditions() != null &&
                    !zoneManager.checkConditions(collection.spawnConditions(), location)) {
                continue;
            }

            // Also verify at least one item can spawn at this location
            if (hasValidItemForLocation(collection, location)) {
                validCollections.add(collection);
            }
        }

        if (validCollections.isEmpty()) return null;

        return validCollections.get(ThreadLocalRandom.current().nextInt(validCollections.size()));
    }

    /**
     * Check if a collection has at least one item that can spawn at the given location.
     *
     * @param collection The collection to check
     * @param location   The spawn location
     * @return true if at least one item passes spawn conditions
     */
    private boolean hasValidItemForLocation(Collection collection, Location location) {
        for (CollectionItem item : collection.items()) {
            if (item.spawnConditions() == null ||
                    zoneManager.checkConditions(item.spawnConditions(), location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Select a random item from a collection that can spawn at the given location.
     * Uses weighted random selection among valid items.
     *
     * @param collection The collection to select from
     * @param location   The spawn location to validate conditions against
     * @return A valid item, or null if none found
     */
    private CollectionItem selectRandomItem(Collection collection, Location location) {
        // Filter items by their spawn conditions
        List<CollectionItem> validItems = new ArrayList<>();
        for (CollectionItem item : collection.items()) {
            if (item.spawnConditions() == null ||
                    zoneManager.checkConditions(item.spawnConditions(), location)) {
                validItems.add(item);
            }
        }

        if (validItems.isEmpty()) return null;

        // Weighted random selection
        int totalWeight = validItems.stream().mapToInt(CollectionItem::weight).sum();
        int random = ThreadLocalRandom.current().nextInt(totalWeight);

        int cumulative = 0;
        for (CollectionItem item : validItems) {
            cumulative += item.weight();
            if (random < cumulative) {
                return item;
            }
        }

        // Fallback (should never happen)
        return validItems.get(0);
    }

    /**
     * Spawn a collectible at the given location with a pre-selected item.
     * Collectibles are invisible interaction entities with particle effects - no armor stand or head.
     *
     * @param location   The spawn location
     * @param zone       The zone spawning in
     * @param collection The collection to spawn from
     * @param item       The pre-selected item (selected based on spawn conditions)
     * @return The spawned collectible, or null if spawn failed
     */
    public Collectible spawnCollectible(Location location, SpawnZone zone, Collection collection, CollectionItem item) {
        World world = location.getWorld();
        if (world == null) return null;

        CollectibleTier tier = collection.tier();
        UUID collectibleId = UUID.randomUUID();

        // Spawn the interaction entity (invisible hitbox for clicking)
        // Particles are handled by ParticleTask, action bar prompt by ActionBarPromptTask
        Interaction hitbox = world.spawn(location, Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(1.5f);
            interaction.setPersistent(false); // We manage persistence ourselves

            // Store metadata on the hitbox
            PersistentDataContainer pdc = interaction.getPersistentDataContainer();
            pdc.set(COLLECTIBLE_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(COLLECTIBLE_ID_KEY, PersistentDataType.STRING, collectibleId.toString());
            pdc.set(COLLECTION_KEY, PersistentDataType.STRING, collection.id());
            pdc.set(TIER_KEY, PersistentDataType.STRING, tier.name());
        });

        // Create the collectible record with the pre-selected item
        Collectible collectible = new Collectible(
                collectibleId,
                hitbox.getUniqueId(),
                zone.id(),
                collection.id(),
                item.id(),
                location,
                tier,
                System.currentTimeMillis(),
                true // spawned = true
        );

        // Track it
        activeCollectibles.put(collectibleId, collectible);
        collectibleCountByZone.merge(zone.id(), 1, Integer::sum);

        // Save to database
        storage.saveCollectible(collectible);

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Spawned collectible " + collectibleId +
                    " from collection " + collection.id() + " (item: " + item.id() + ") at " + location);
        }

        return collectible;
    }

    /**
     * Spawn a collectible at the given location (selects random item based on location conditions).
     *
     * @param location   The spawn location
     * @param zone       The zone spawning in
     * @param collection The collection to spawn from
     * @return The spawned collectible, or null if spawn failed
     */
    public Collectible spawnCollectible(Location location, SpawnZone zone, Collection collection) {
        // Select a random valid item for this location
        CollectionItem item = selectRandomItem(collection, location);
        if (item == null) {
            // Fallback to any random item if no items pass conditions
            item = collection.getRandomItem();
        }
        return spawnCollectible(location, zone, collection, item);
    }

    /**
     * Despawn a collectible and remove it from tracking.
     */
    public void despawnCollectible(UUID collectibleId, boolean removeFromDatabase) {
        Collectible collectible = activeCollectibles.remove(collectibleId);
        if (collectible == null) return;

        // Decrement zone count
        collectibleCountByZone.computeIfPresent(collectible.zoneId(), (k, v) -> Math.max(0, v - 1));

        // Remove entities
        removeCollectibleEntities(collectible);

        // Set respawn timer
        SpawnZone zone = zoneManager.getZone(collectible.zoneId());
        if (zone != null) {
            long respawnTime = System.currentTimeMillis() + (zone.respawnDelay() * 1000L);
            respawnTimers.put(zone.id(), respawnTime);
        }

        if (removeFromDatabase) {
            storage.removeCollectible(collectibleId);
        }

        if (plugin.getConfigManager().isDebugMode()) {
            plugin.getLogger().info("Despawned collectible " + collectibleId);
        }
    }

    /**
     * Remove the entities associated with a collectible.
     */
    private void removeCollectibleEntities(Collectible collectible) {
        // Remove hitbox (the only entity now - no armor stand)
        if (collectible.hitboxId() != null) {
            Entity hitbox = Bukkit.getEntity(collectible.hitboxId());
            if (hitbox != null) {
                hitbox.remove();
            }
        }
    }

    /**
     * Recreate entities for a collectible (after chunk load).
     */
    public void recreateEntities(Collectible collectible) {
        if (collectible.spawned()) return;

        World world = collectible.location().getWorld();
        if (world == null) return;

        Location location = collectible.location();
        Collection collection = collectionManager.getCollection(collectible.collectionId());
        if (collection == null) {
            // Collection no longer exists - remove this collectible
            despawnCollectible(collectible.id(), true);
            return;
        }

        CollectibleTier tier = collectible.tier();

        // Spawn new hitbox (only entity - particles handled by ParticleTask)
        Interaction hitbox = world.spawn(location, Interaction.class, interaction -> {
            interaction.setInteractionWidth(1.0f);
            interaction.setInteractionHeight(1.5f);
            interaction.setPersistent(false);

            PersistentDataContainer pdc = interaction.getPersistentDataContainer();
            pdc.set(COLLECTIBLE_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(COLLECTIBLE_ID_KEY, PersistentDataType.STRING, collectible.id().toString());
            pdc.set(COLLECTION_KEY, PersistentDataType.STRING, collection.id());
            pdc.set(TIER_KEY, PersistentDataType.STRING, tier.name());
        });

        // Update the collectible with new hitbox ID
        Collectible updated = collectible.withHitbox(hitbox.getUniqueId());
        activeCollectibles.put(updated.id(), updated);
    }

    /**
     * Mark a collectible as unspawned (for chunk unload).
     */
    public void markUnspawned(Collectible collectible) {
        removeCollectibleEntities(collectible);
        Collectible updated = collectible.withSpawned(false);
        activeCollectibles.put(updated.id(), updated);
    }

    /**
     * Validate all active collectibles and remove invalid/expired ones.
     */
    private void validateActiveCollectibles() {
        int despawnMinutes = plugin.getConfigManager().getDespawnAfterMinutes();
        long despawnMs = despawnMinutes * 60 * 1000L;
        long now = System.currentTimeMillis();
        boolean debug = plugin.getConfigManager().isDebugMode();

        List<UUID> toDespawn = new ArrayList<>();

        for (Collectible collectible : activeCollectibles.values()) {
            if (!collectible.spawned()) continue;

            // Check despawn timeout (if enabled)
            if (despawnMinutes > 0) {
                long age = now - collectible.spawnedAt();
                if (age >= despawnMs) {
                    toDespawn.add(collectible.id());
                    if (debug) {
                        plugin.getLogger().info("Despawning expired collectible " +
                                collectible.id() + " in zone " + collectible.zoneId() +
                                " (age: " + (age / 60000) + " minutes)");
                    }
                    continue; // Skip other validity checks
                }
            }

            Location loc = collectible.location();
            World world = loc.getWorld();

            if (world == null) {
                toDespawn.add(collectible.id());
                continue;
            }

            // Check if location is still valid
            SpawnZone zone = zoneManager.getZone(collectible.zoneId());
            if (zone == null || !zoneManager.isValidSpawnLocation(zone, loc)) {
                toDespawn.add(collectible.id());
                if (debug) {
                    plugin.getLogger().info("Removed invalid collectible at " + loc);
                }
            }
        }

        // Despawn all marked collectibles
        for (UUID id : toDespawn) {
            despawnCollectible(id, true);
        }

        if (!toDespawn.isEmpty() && debug) {
            plugin.getLogger().info("Validity check: despawned " + toDespawn.size() + " collectibles");
        }
    }

    /**
     * Get a collectible by its ID.
     */
    public Collectible getCollectible(UUID id) {
        return activeCollectibles.get(id);
    }

    /**
     * Get a collectible by entity UUID (hitbox).
     */
    public Collectible getCollectibleByEntity(UUID entityId) {
        for (Collectible collectible : activeCollectibles.values()) {
            if (entityId.equals(collectible.hitboxId())) {
                return collectible;
            }
        }
        return null;
    }

    /**
     * Get all active collectibles.
     */
    public java.util.Collection<Collectible> getActiveCollectibles() {
        return java.util.Collections.unmodifiableCollection(activeCollectibles.values());
    }

    /**
     * Get collectibles in a specific chunk.
     */
    public List<Collectible> getCollectiblesInChunk(World world, int chunkX, int chunkZ) {
        List<Collectible> result = new ArrayList<>();
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;

        for (Collectible collectible : activeCollectibles.values()) {
            Location loc = collectible.location();
            if (loc.getWorld() != null && loc.getWorld().equals(world)) {
                int x = loc.getBlockX();
                int z = loc.getBlockZ();
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    result.add(collectible);
                }
            }
        }
        return result;
    }

    /**
     * Clear all collectibles in a zone.
     */
    public int clearZone(String zoneId) {
        int count = 0;
        for (Collectible collectible : new ArrayList<>(activeCollectibles.values())) {
            if (collectible.zoneId().equals(zoneId)) {
                despawnCollectible(collectible.id(), true);
                count++;
            }
        }
        return count;
    }

    /**
     * Clear all collectibles.
     */
    public int clearAll() {
        int count = activeCollectibles.size();
        for (Collectible collectible : new ArrayList<>(activeCollectibles.values())) {
            despawnCollectible(collectible.id(), true);
        }
        return count;
    }

    /**
     * Force spawn a collectible in a specific zone (admin command).
     *
     * @param zone The zone to spawn in
     * @return The spawned collectible, or null if spawn failed
     */
    public Collectible forceSpawn(SpawnZone zone) {
        SpawnResult result = forceSpawnWithResult(zone);
        return result.success() ? getLastSpawnedCollectible(result.location()) : null;
    }

    /**
     * Force spawn a collectible in a specific zone with detailed result.
     *
     * @param zone The zone to spawn in
     * @return SpawnResult with location or failure statistics
     */
    public SpawnResult forceSpawnWithResult(SpawnZone zone) {
        World world = Bukkit.getWorld(zone.worldName());
        if (world == null) {
            var stats = new com.blockworlds.collections.spawn.SpawnFailureStats();
            stats.recordFailure("world-not-loaded");
            return SpawnResult.failure(stats);
        }

        // Get a valid spawn location using adaptive finder
        SpawnResult result = findSpawnLocation(zone);
        if (!result.success()) {
            return result;
        }

        Location spawnLoc = result.location();

        // Select a random collection for this zone that passes spawn conditions
        Collection collection = selectRandomCollection(zone, spawnLoc);
        if (collection == null) {
            var stats = new com.blockworlds.collections.spawn.SpawnFailureStats();
            stats.recordFailure("no-valid-collection");
            return SpawnResult.failure(stats);
        }

        // Spawn the collectible (item selected based on location conditions)
        Collectible spawned = spawnCollectible(spawnLoc, zone, collection);
        if (spawned == null) {
            var stats = new com.blockworlds.collections.spawn.SpawnFailureStats();
            stats.recordFailure("spawn-failed");
            return SpawnResult.failure(stats);
        }

        return result;
    }

    /**
     * Get the most recently spawned collectible at a location.
     */
    private Collectible getLastSpawnedCollectible(Location location) {
        for (Collectible c : activeCollectibles.values()) {
            if (c.location().getBlockX() == location.getBlockX() &&
                    c.location().getBlockY() == location.getBlockY() &&
                    c.location().getBlockZ() == location.getBlockZ()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Reset all respawn timers and trigger immediate respawns.
     * Called during reload to repopulate zones with new config.
     */
    public void resetRespawnTimers() {
        respawnTimers.clear();
        plugin.getLogger().info("Respawn timers reset - zones will repopulate on next spawn check");
    }

    /**
     * Get the count of active collectibles.
     */
    public int getActiveCount() {
        return activeCollectibles.size();
    }

    /**
     * Get the count of active collectibles in a zone.
     */
    public int getCountInZone(String zoneId) {
        return collectibleCountByZone.getOrDefault(zoneId, 0);
    }

    // Getters for NamespacedKeys (for use by listeners)

    public NamespacedKey getCollectibleKey() {
        return COLLECTIBLE_KEY;
    }

    public NamespacedKey getCollectibleIdKey() {
        return COLLECTIBLE_ID_KEY;
    }

    public NamespacedKey getCollectionKey() {
        return COLLECTION_KEY;
    }

    public NamespacedKey getTierKey() {
        return TIER_KEY;
    }
}
