package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.DropSourceManager;
import com.blockworlds.collections.manager.DropSourceManager.DropCandidate;
import com.blockworlds.collections.model.MobDropSource;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Listener for mob drop sources.
 * Handles EntityDeathEvent to drop collection items from mob kills.
 */
public class MobDropListener implements Listener {

    private final Collections plugin;
    private final DropSourceManager dropSourceManager;

    public MobDropListener(Collections plugin) {
        this.plugin = plugin;
        this.dropSourceManager = plugin.getDropSourceManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        EntityType entityType = entity.getType();

        // Get all mob drop candidates for this entity type
        List<DropCandidate<MobDropSource>> candidates = dropSourceManager.getMobDropCandidates(entityType);
        if (candidates.isEmpty()) {
            return;
        }

        for (DropCandidate<MobDropSource> candidate : candidates) {
            MobDropSource source = candidate.source();

            // Check player kill requirement
            if (source.playerKillRequired() && killer == null) {
                continue;
            }

            // Check spawn conditions at entity location
            if (source.conditions() != null && !source.conditions().check(entity.getLocation())) {
                continue;
            }

            // Calculate chance with enchantments from killer's weapon
            ItemStack weapon = killer != null ? killer.getInventory().getItemInMainHand() : null;
            double chance = dropSourceManager.calculateDropChance(
                    source.chance(),
                    source.enchantBonus(),
                    weapon
            );

            // Attempt drop
            if (killer != null) {
                String mobName = formatEntityName(entityType);
                dropSourceManager.tryDrop(
                        killer,
                        candidate.collection(),
                        candidate.item(),
                        chance,
                        "mob",
                        mobName
                );
            }
        }
    }

    /**
     * Format an entity type name for display.
     */
    private String formatEntityName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        // Capitalize first letter of each word
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
