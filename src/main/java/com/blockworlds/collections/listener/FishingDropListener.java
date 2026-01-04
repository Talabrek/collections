package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.DropSourceManager;
import com.blockworlds.collections.manager.DropSourceManager.DropCandidate;
import com.blockworlds.collections.model.FishingDropSource;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Listener for fishing drop sources.
 * Handles PlayerFishEvent to drop collection items from fishing.
 */
public class FishingDropListener implements Listener {

    private final Collections plugin;
    private final DropSourceManager dropSourceManager;

    // Fish materials
    private static final Set<Material> FISH_ITEMS = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    );

    // Treasure materials
    private static final Set<Material> TREASURE_ITEMS = Set.of(
            Material.BOW, Material.ENCHANTED_BOOK, Material.FISHING_ROD, Material.NAME_TAG,
            Material.NAUTILUS_SHELL, Material.SADDLE
    );

    // Junk materials
    private static final Set<Material> JUNK_ITEMS = Set.of(
            Material.LEATHER_BOOTS, Material.LEATHER, Material.BONE, Material.POTION,
            Material.STRING, Material.BOWL, Material.STICK, Material.INK_SAC,
            Material.TRIPWIRE_HOOK, Material.ROTTEN_FLESH, Material.BAMBOO, Material.LILY_PAD
    );

    public FishingDropListener(Collections plugin) {
        this.plugin = plugin;
        this.dropSourceManager = plugin.getDropSourceManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // Only process when catching something
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();
        Entity caught = event.getCaught();

        // Must be an Item entity
        if (!(caught instanceof Item itemEntity)) {
            return;
        }

        // Determine catch type
        FishingDropSource.CatchType catchType = determineCatchType(itemEntity.getItemStack());

        // Get all fishing drop candidates
        List<DropCandidate<FishingDropSource>> candidates = dropSourceManager.getFishingDropCandidates();
        if (candidates.isEmpty()) {
            return;
        }

        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            // Check offhand
            rod = player.getInventory().getItemInOffHand();
        }

        for (DropCandidate<FishingDropSource> candidate : candidates) {
            FishingDropSource source = candidate.source();

            // Check catch type matches
            if (!source.matchesCatchType(catchType)) {
                continue;
            }

            // Check spawn conditions at hook location
            if (source.conditions() != null && event.getHook() != null &&
                    !source.conditions().check(event.getHook().getLocation())) {
                continue;
            }

            // Calculate chance with enchantments
            double chance = dropSourceManager.calculateDropChance(
                    source.chance(),
                    source.enchantBonus(),
                    rod
            );

            // Attempt drop
            dropSourceManager.tryDrop(
                    player,
                    candidate.collection(),
                    candidate.item(),
                    chance,
                    "fishing",
                    null
            );
        }
    }

    /**
     * Determine the catch type from the caught item.
     */
    private FishingDropSource.CatchType determineCatchType(ItemStack item) {
        Material type = item.getType();

        if (FISH_ITEMS.contains(type)) {
            return FishingDropSource.CatchType.FISH;
        }

        if (TREASURE_ITEMS.contains(type) || item.hasItemMeta()) {
            // Enchanted items count as treasure
            return FishingDropSource.CatchType.TREASURE;
        }

        if (JUNK_ITEMS.contains(type)) {
            return FishingDropSource.CatchType.JUNK;
        }

        // Default to fish for unknown items
        return FishingDropSource.CatchType.FISH;
    }
}
