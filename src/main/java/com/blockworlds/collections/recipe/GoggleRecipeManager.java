package com.blockworlds.collections.recipe;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.GoggleManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Manages crafting recipes for Collector's Goggles.
 * Handles recipe registration, discovery/unlock, and permission checks.
 */
public class GoggleRecipeManager implements Listener {

    private final Collections plugin;
    private final ConfigManager configManager;
    private final GoggleManager goggleManager;

    // Recipe keys
    private final NamespacedKey basicGoggleRecipeKey;
    private final NamespacedKey masterGoggleRecipeKey;

    // Track if recipes are registered
    private boolean recipesRegistered = false;

    public GoggleRecipeManager(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.goggleManager = plugin.getGoggleManager();

        // Create namespaced keys for recipes
        this.basicGoggleRecipeKey = new NamespacedKey(plugin, "collectors_goggles");
        this.masterGoggleRecipeKey = new NamespacedKey(plugin, "master_collectors_goggles");

        // Register this as event listener for permission checks
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Register all goggle recipes with Bukkit.
     * Should be called during plugin enable.
     */
    public void registerRecipes() {
        if (!configManager.areRecipesEnabled()) {
            plugin.getLogger().info("Goggle recipes are disabled in config.");
            return;
        }

        // Create and register basic goggles recipe
        ShapedRecipe basicRecipe = createBasicGoggleRecipe();
        if (basicRecipe != null) {
            Bukkit.addRecipe(basicRecipe);
        }

        // Create and register master goggles recipe
        ShapedRecipe masterRecipe = createMasterGoggleRecipe();
        if (masterRecipe != null) {
            Bukkit.addRecipe(masterRecipe);
        }

        recipesRegistered = true;
        plugin.getLogger().info("Registered goggle crafting recipes.");
    }

    /**
     * Unregister all goggle recipes from Bukkit.
     * Should be called during plugin disable or reload.
     */
    public void unregisterRecipes() {
        if (!recipesRegistered) {
            return;
        }

        Bukkit.removeRecipe(basicGoggleRecipeKey);
        Bukkit.removeRecipe(masterGoggleRecipeKey);

        recipesRegistered = false;
        plugin.getLogger().info("Unregistered goggle crafting recipes.");
    }

    /**
     * Create the Collector's Goggles recipe.
     * Shape:
     * [GLASS_PANE] [LEATHER] [GLASS_PANE]
     * [GOLD_INGOT] [       ] [GOLD_INGOT]
     */
    private ShapedRecipe createBasicGoggleRecipe() {
        ItemStack result = goggleManager.createBasicGoggles();
        ShapedRecipe recipe = new ShapedRecipe(basicGoggleRecipeKey, result);

        // Set the shape (2 rows, spaces represent empty slots)
        recipe.shape("GPG", "I I");

        // Set ingredients
        recipe.setIngredient('G', Material.GLASS_PANE);
        recipe.setIngredient('P', Material.LEATHER);
        recipe.setIngredient('I', Material.GOLD_INGOT);

        return recipe;
    }

    /**
     * Create the Master Collector's Goggles recipe.
     * Shape:
     * [ENDER_EYE] [COLLECTOR'S_GOGGLES] [ENDER_EYE]
     * [DIAMOND  ] [     NETHERITE     ] [DIAMOND  ]
     *
     * Uses ExactChoice to require actual Collector's Goggles.
     */
    private ShapedRecipe createMasterGoggleRecipe() {
        ItemStack result = goggleManager.createMasterGoggles();
        ShapedRecipe recipe = new ShapedRecipe(masterGoggleRecipeKey, result);

        // Set the shape (2 rows)
        recipe.shape("EGE", "DND");

        // Set ingredients
        recipe.setIngredient('E', Material.ENDER_EYE);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);

        // Use ExactChoice to require actual Collector's Goggles
        // This ensures players can't use any leather helmet
        ItemStack basicGoggles = goggleManager.createBasicGoggles();
        RecipeChoice goggleChoice = new RecipeChoice.ExactChoice(basicGoggles);
        recipe.setIngredient('G', goggleChoice);

        return recipe;
    }

    /**
     * Unlock goggle recipes for a player.
     * Should be called when a player collects their first collectible.
     *
     * @param player The player to unlock recipes for
     */
    public void unlockRecipesForPlayer(Player player) {
        if (!configManager.areRecipesEnabled()) {
            return;
        }

        if (!configManager.shouldUnlockOnFirstCollect()) {
            return;
        }

        // Unlock basic goggles recipe
        if (!player.hasDiscoveredRecipe(basicGoggleRecipeKey)) {
            player.discoverRecipe(basicGoggleRecipeKey);

            if (configManager.isDebugMode()) {
                plugin.getLogger().info("Unlocked Collector's Goggles recipe for " + player.getName());
            }
        }

        // Unlock master goggles recipe
        if (!player.hasDiscoveredRecipe(masterGoggleRecipeKey)) {
            player.discoverRecipe(masterGoggleRecipeKey);

            if (configManager.isDebugMode()) {
                plugin.getLogger().info("Unlocked Master Collector's Goggles recipe for " + player.getName());
            }
        }

        // Send message to player
        player.sendMessage(configManager.getMessage("goggles-unlocked", "tier", "Collector's"));
    }

    /**
     * Check if a player has discovered the goggle recipes.
     *
     * @param player The player to check
     * @return true if both recipes are discovered
     */
    public boolean hasDiscoveredRecipes(Player player) {
        return player.hasDiscoveredRecipe(basicGoggleRecipeKey) &&
                player.hasDiscoveredRecipe(masterGoggleRecipeKey);
    }

    /**
     * Check if a player has discovered the basic goggle recipe.
     *
     * @param player The player to check
     * @return true if discovered
     */
    public boolean hasDiscoveredBasicRecipe(Player player) {
        return player.hasDiscoveredRecipe(basicGoggleRecipeKey);
    }

    /**
     * Handle crafting permission checks.
     * Blocks crafting if player doesn't have the required permission.
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }

        // Only check shaped recipes
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return;
        }

        NamespacedKey key = shaped.getKey();
        HumanEntity viewer = event.getView().getPlayer();

        // Check basic goggles permission
        if (key.equals(basicGoggleRecipeKey)) {
            if (!viewer.hasPermission("collections.craft.goggles")) {
                event.getInventory().setResult(null);
            }
            return;
        }

        // Check master goggles permission
        if (key.equals(masterGoggleRecipeKey)) {
            if (!viewer.hasPermission("collections.craft.mastergoggles")) {
                event.getInventory().setResult(null);
            }
        }
    }

    /**
     * Get the basic goggle recipe key.
     */
    public NamespacedKey getBasicGoggleRecipeKey() {
        return basicGoggleRecipeKey;
    }

    /**
     * Get the master goggle recipe key.
     */
    public NamespacedKey getMasterGoggleRecipeKey() {
        return masterGoggleRecipeKey;
    }
}
