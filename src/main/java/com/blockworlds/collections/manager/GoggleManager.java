package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.model.Collectible;
import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages goggle visibility for collectibles.
 * Players need specific goggles to see higher-tier collectibles.
 */
public class GoggleManager {

    private final Collections plugin;
    private final ConfigManager configManager;

    // Visibility tiers for each goggle type
    private static final Set<CollectibleTier> BASIC_GOGGLES_TIERS = Set.of(CollectibleTier.UNCOMMON);
    private static final Set<CollectibleTier> MASTER_GOGGLES_TIERS = Set.of(CollectibleTier.UNCOMMON, CollectibleTier.RARE);

    public GoggleManager(Collections plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Check if a player can see a specific collectible based on equipped goggles.
     *
     * @param player      The player to check
     * @param collectible The collectible to check visibility for
     * @return true if the player can see this collectible
     */
    public boolean canPlayerSeeCollectible(Player player, Collectible collectible) {
        return canPlayerSeeTier(player, collectible.tier());
    }

    /**
     * Check if a player can see collectibles of a specific tier.
     *
     * @param player The player to check
     * @param tier   The collectible tier
     * @return true if the player can see this tier
     */
    public boolean canPlayerSeeTier(Player player, CollectibleTier tier) {
        // Check if goggles are disabled in config
        if (!configManager.isGogglesEnabled()) {
            return true; // All tiers visible when goggles disabled
        }

        // COMMON tier is always visible
        if (tier == CollectibleTier.COMMON) {
            return true;
        }

        // Check bypass permission
        if (player.hasPermission("collections.bypass.goggles")) {
            return true;
        }

        // EVENT tier: visible during active event OR with any goggles
        if (tier == CollectibleTier.EVENT) {
            EventManager eventManager = plugin.getEventManager();
            if (eventManager != null && eventManager.isAnyEventActive()) {
                return true; // Event is active, visible to all
            }
            return hasAnyGoggles(player);
        }

        // Get player's goggle tier
        CollectibleTier goggleTier = getPlayerGoggleTier(player);
        if (goggleTier == null) {
            return false; // No goggles equipped
        }

        // Check if the goggle tier grants visibility for the collectible tier
        Set<CollectibleTier> visibleTiers = getVisibleTiers(goggleTier);
        return visibleTiers.contains(tier);
    }

    /**
     * Get the goggle tier from the player's equipped helmet.
     *
     * @param player The player to check
     * @return The goggle tier, or null if not wearing goggles
     */
    public CollectibleTier getPlayerGoggleTier(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) {
            return null;
        }

        if (!helmet.hasItemMeta()) {
            return null;
        }

        PersistentDataContainer pdc = helmet.getItemMeta().getPersistentDataContainer();
        String tierStr = pdc.get(PDCKeys.GOGGLE_TIER(), PersistentDataType.STRING);

        if (tierStr == null) {
            return null;
        }

        return CollectibleTier.fromString(tierStr);
    }

    /**
     * Check if a player is wearing any type of collector's goggles.
     *
     * @param player The player to check
     * @return true if wearing any goggles
     */
    public boolean hasAnyGoggles(Player player) {
        return getPlayerGoggleTier(player) != null;
    }

    /**
     * Get the set of collectible tiers visible with a specific goggle tier.
     *
     * @param goggleTier The goggle tier
     * @return Set of visible collectible tiers
     */
    public Set<CollectibleTier> getVisibleTiers(CollectibleTier goggleTier) {
        return switch (goggleTier) {
            case UNCOMMON -> BASIC_GOGGLES_TIERS;
            case RARE -> MASTER_GOGGLES_TIERS;
            default -> Set.of();
        };
    }

    /**
     * Refresh visibility of all collectibles for a player.
     * Should be called when goggles are equipped/unequipped.
     *
     * @param player The player to refresh visibility for
     */
    public void refreshVisibilityForPlayer(Player player) {
        SpawnManager spawnManager = plugin.getSpawnManager();
        int renderDistance = configManager.getParticleDistanceBlocks();

        for (Collectible collectible : spawnManager.getActiveCollectibles()) {
            if (!collectible.spawned()) continue;

            // Check distance - store world reference to avoid multiple calls
            org.bukkit.World collectibleWorld = collectible.location().getWorld();
            if (collectibleWorld == null || !collectibleWorld.equals(player.getWorld())) {
                continue;
            }

            double distance = player.getLocation().distance(collectible.location());
            if (distance > renderDistance) continue;

            // Update visibility
            boolean canSee = canPlayerSeeCollectible(player, collectible);
            setCollectibleVisibilityForPlayer(player, collectible, canSee);
        }
    }

    /**
     * Set visibility of a specific collectible for a player.
     *
     * @param player      The player
     * @param collectible The collectible
     * @param visible     Whether the player should see it
     */
    public void setCollectibleVisibilityForPlayer(Player player, Collectible collectible, boolean visible) {
        // Get the hitbox entity (only entity now - no armor stand)
        if (collectible.hitboxId() != null) {
            Entity hitbox = Bukkit.getEntity(collectible.hitboxId());
            if (hitbox != null) {
                if (visible) {
                    player.showEntity(plugin, hitbox);
                } else {
                    player.hideEntity(plugin, hitbox);
                }
            }
        }
    }

    /**
     * Setup initial visibility for a newly spawned collectible.
     * Should be called after spawning a collectible.
     *
     * @param collectible The newly spawned collectible
     */
    public void setupInitialVisibility(Collectible collectible) {
        org.bukkit.World world = collectible.location().getWorld();
        if (world == null) return;

        int renderDistance = configManager.getParticleDistanceBlocks();

        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(collectible.location());
            if (distance > renderDistance) continue;

            boolean canSee = canPlayerSeeCollectible(player, collectible);
            if (!canSee) {
                setCollectibleVisibilityForPlayer(player, collectible, false);
            }
        }
    }

    /**
     * Check if an item is a goggle item.
     *
     * @param item The item to check
     * @return true if the item is a goggle item
     */
    public boolean isGoggleItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        return item.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.GOGGLE_TIER());
    }

    /**
     * Create a pair of Collector's Goggles (reveals UNCOMMON).
     *
     * @return The goggle item
     */
    public ItemStack createBasicGoggles() {
        ItemStack goggles = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) goggles.getItemMeta();

        if (meta != null) {
            // Set color (aqua/blue)
            meta.setColor(Color.fromRGB(51, 153, 255)); // #3399FF

            // Set name
            meta.displayName(configManager.parse("<aqua>Collector's Goggles</aqua>"));

            // Set lore
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Specially crafted lenses that</gray>");
            lore.add("<gray>reveal hidden collectibles.</gray>");
            lore.add("");
            lore.add("<yellow>Reveals: <aqua>Uncommon</aqua> collectibles</yellow>");

            List<net.kyori.adventure.text.Component> loreParsed = new ArrayList<>();
            for (String line : lore) {
                loreParsed.add(configManager.parse(line));
            }
            meta.lore(loreParsed);

            // Add enchantments
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Set goggle tier PDC
            meta.getPersistentDataContainer().set(
                    PDCKeys.GOGGLE_TIER(),
                    PersistentDataType.STRING,
                    CollectibleTier.UNCOMMON.name()
            );

            // Make soulbound if configured
            if (configManager.isGogglesSoulbound()) {
                meta.getPersistentDataContainer().set(
                        PDCKeys.SOULBOUND(),
                        PersistentDataType.BOOLEAN,
                        true
                );
            }

            goggles.setItemMeta(meta);
        }

        return goggles;
    }

    /**
     * Create a pair of Master Collector's Goggles (reveals UNCOMMON + RARE).
     *
     * @return The goggle item
     */
    public ItemStack createMasterGoggles() {
        ItemStack goggles = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) goggles.getItemMeta();

        if (meta != null) {
            // Set color (gold)
            meta.setColor(Color.fromRGB(255, 215, 0)); // #FFD700

            // Set name
            meta.displayName(configManager.parse("<gold>Master Collector's Goggles</gold>"));

            // Set lore
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Ancient lenses imbued with</gray>");
            lore.add("<gray>the wisdom of master collectors.</gray>");
            lore.add("");
            lore.add("<yellow>Reveals: <aqua>Uncommon</aqua> + <gold>Rare</gold> collectibles</yellow>");

            List<net.kyori.adventure.text.Component> loreParsed = new ArrayList<>();
            for (String line : lore) {
                loreParsed.add(configManager.parse(line));
            }
            meta.lore(loreParsed);

            // Add enchantments
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Set goggle tier PDC
            meta.getPersistentDataContainer().set(
                    PDCKeys.GOGGLE_TIER(),
                    PersistentDataType.STRING,
                    CollectibleTier.RARE.name()
            );

            // Make soulbound if configured
            if (configManager.isGogglesSoulbound()) {
                meta.getPersistentDataContainer().set(
                        PDCKeys.SOULBOUND(),
                        PersistentDataType.BOOLEAN,
                        true
                );
            }

            goggles.setItemMeta(meta);
        }

        return goggles;
    }

    /**
     * Create goggles of a specific tier.
     *
     * @param tier The tier (UNCOMMON = basic, RARE = master)
     * @return The goggle item
     */
    public ItemStack createGoggles(CollectibleTier tier) {
        return switch (tier) {
            case UNCOMMON -> createBasicGoggles();
            case RARE -> createMasterGoggles();
            default -> createBasicGoggles(); // Default to basic
        };
    }
}
