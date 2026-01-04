package com.blockworlds.collections;

import com.blockworlds.collections.command.CollectionsCommand;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.gui.GUIManager;
import com.blockworlds.collections.listener.BlockDropListener;
import com.blockworlds.collections.listener.ChunkListener;
import com.blockworlds.collections.listener.CollectibleInteractListener;
import com.blockworlds.collections.listener.FishingDropListener;
import com.blockworlds.collections.listener.GUIListener;
import com.blockworlds.collections.listener.ArmorChangeListener;
import com.blockworlds.collections.listener.ItemModifyListener;
import com.blockworlds.collections.listener.ItemUseListener;
import com.blockworlds.collections.listener.LootDropListener;
import com.blockworlds.collections.listener.MobDropListener;
import com.blockworlds.collections.listener.PlayerListener;
import com.blockworlds.collections.manager.CollectionManager;
import com.blockworlds.collections.manager.DropSourceManager;
import com.blockworlds.collections.manager.EventManager;
import com.blockworlds.collections.manager.GoggleManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.manager.RewardManager;
import com.blockworlds.collections.manager.SpawnManager;
import com.blockworlds.collections.manager.ZoneManager;
import com.blockworlds.collections.recipe.GoggleRecipeManager;
import com.blockworlds.collections.storage.SQLiteStorage;
import com.blockworlds.collections.storage.Storage;
import com.blockworlds.collections.task.ActionBarPromptTask;
import com.blockworlds.collections.task.ParticleTask;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Collections - EQ2-style collectibles system for Paper
 *
 * Players discover glowing spawn points in the world, collect random items
 * from zone-specific pools, and complete collections for rewards.
 */
public class Collections extends JavaPlugin {

    private static Collections instance;

    private ConfigManager configManager;
    private Storage storage;
    private PlayerDataManager playerDataManager;
    private CollectionManager collectionManager;
    private ZoneManager zoneManager;
    private SpawnManager spawnManager;
    private DropSourceManager dropSourceManager;
    private ParticleTask particleTask;
    private ActionBarPromptTask actionBarPromptTask;
    private GUIManager guiManager;
    private RewardManager rewardManager;
    private GoggleManager goggleManager;
    private GoggleRecipeManager goggleRecipeManager;
    private EventManager eventManager;
    private CollectibleInteractListener collectibleInteractListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configs
        saveDefaultConfig();
        saveResource("zones.yml", false);

        // Initialize managers in dependency order
        this.configManager = new ConfigManager(this);
        this.storage = new SQLiteStorage(this);
        this.collectionManager = new CollectionManager(this);
        this.zoneManager = new ZoneManager(this);
        this.playerDataManager = new PlayerDataManager(this, storage);

        // Initialize storage (creates tables)
        storage.initialize();

        // Load collections and zones from YAML files
        collectionManager.loadCollections();
        zoneManager.loadZones();

        // Initialize drop source manager (depends on collections)
        this.dropSourceManager = new DropSourceManager(this);
        dropSourceManager.buildIndexes();

        // Initialize spawn manager (depends on zones and collections)
        this.spawnManager = new SpawnManager(this);
        spawnManager.initialize();

        // Initialize particle task
        this.particleTask = new ParticleTask(this);
        particleTask.start();

        // Initialize action bar prompt task
        this.actionBarPromptTask = new ActionBarPromptTask(this);
        actionBarPromptTask.start();

        // Initialize GUI manager
        this.guiManager = new GUIManager(this);

        // Initialize reward manager
        this.rewardManager = new RewardManager(this);

        // Initialize goggle manager
        this.goggleManager = new GoggleManager(this);

        // Initialize event manager
        this.eventManager = new EventManager(this);

        // Initialize goggle recipe manager and register recipes
        this.goggleRecipeManager = new GoggleRecipeManager(this);
        goggleRecipeManager.registerRecipes();

        // Register commands and listeners
        registerCommands();
        registerListeners();

        getLogger().info("Collections v" + getPluginMeta().getVersion() + " enabled!");
        getLogger().info("Loaded " + collectionManager.getCollectionCount() + " collections, " +
                zoneManager.getZoneCount() + " zones, " + spawnManager.getActiveCount() + " active collectibles");
    }

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            new CollectionsCommand(this).register(commands);
        });
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        this.collectibleInteractListener = new CollectibleInteractListener(this);
        getServer().getPluginManager().registerEvents(collectibleInteractListener, this);
        getServer().getPluginManager().registerEvents(new ItemModifyListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorChangeListener(this), this);

        // Alternative drop source listeners
        getServer().getPluginManager().registerEvents(new MobDropListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockDropListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingDropListener(this), this);
        getServer().getPluginManager().registerEvents(new LootDropListener(this), this);
    }

    @Override
    public void onDisable() {
        // Unregister recipes
        if (goggleRecipeManager != null) {
            goggleRecipeManager.unregisterRecipes();
        }

        // Stop particle task
        if (particleTask != null) {
            particleTask.stop();
        }

        // Stop action bar prompt task
        if (actionBarPromptTask != null) {
            actionBarPromptTask.stop();
        }

        // Shutdown spawn manager (stops spawn tasks)
        if (spawnManager != null) {
            spawnManager.shutdown();
        }

        // Save all player data before shutdown
        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll().get();
            } catch (Exception e) {
                getLogger().warning("Failed to save player data on shutdown: " + e.getMessage());
            }
        }

        // Shutdown storage connection
        if (storage != null) {
            storage.shutdown();
        }

        getLogger().info("Collections disabled!");
    }

    /**
     * Reload all configuration and collections
     */
    public void reload() {
        // Stop current tasks
        if (particleTask != null) {
            particleTask.stop();
        }
        if (actionBarPromptTask != null) {
            actionBarPromptTask.stop();
        }

        // Reload config and managers
        reloadConfig();
        configManager.reload();
        collectionManager.loadCollections();
        zoneManager.loadZones();

        // Rebuild drop source indexes
        if (dropSourceManager != null) {
            dropSourceManager.buildIndexes();
        }

        // Reset respawn timers so zones repopulate with new config
        if (spawnManager != null) {
            spawnManager.resetRespawnTimers();
        }

        // Restart particle task with new settings
        if (particleTask != null) {
            particleTask.start();
        }
        if (actionBarPromptTask != null) {
            actionBarPromptTask.start();
        }

        getLogger().info("Collections reloaded!");
    }

    // Getters

    public static Collections getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Storage getStorage() {
        return storage;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public CollectionManager getCollectionManager() {
        return collectionManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public DropSourceManager getDropSourceManager() {
        return dropSourceManager;
    }

    public ParticleTask getParticleTask() {
        return particleTask;
    }

    public ActionBarPromptTask getActionBarPromptTask() {
        return actionBarPromptTask;
    }

    public GUIManager getGUIManager() {
        return guiManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public GoggleManager getGoggleManager() {
        return goggleManager;
    }

    public GoggleRecipeManager getGoggleRecipeManager() {
        return goggleRecipeManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public CollectibleInteractListener getCollectibleInteractListener() {
        return collectibleInteractListener;
    }
}
