package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.DropSourceManager;
import com.blockworlds.collections.manager.DropSourceManager.DropCandidate;
import com.blockworlds.collections.model.BlockDropSource;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Listener for block drop sources.
 * Handles BlockBreakEvent to drop collection items from block breaks.
 */
public class BlockDropListener implements Listener {

    private final Collections plugin;
    private final DropSourceManager dropSourceManager;

    public BlockDropListener(Collections plugin) {
        this.plugin = plugin;
        this.dropSourceManager = plugin.getDropSourceManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();

        // Skip if in creative mode and creative drops are disabled
        if (player.getGameMode() == GameMode.CREATIVE && !dropSourceManager.isAllowCreativeDrops()) {
            return;
        }

        // Get all block drop candidates for this block type
        List<DropCandidate<BlockDropSource>> candidates = dropSourceManager.getBlockDropCandidates(blockType);
        if (candidates.isEmpty()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        for (DropCandidate<BlockDropSource> candidate : candidates) {
            BlockDropSource source = candidate.source();

            // Check silk touch exclusion
            if (source.silkTouchDisabled() && dropSourceManager.hasSilkTouch(tool)) {
                continue;
            }

            // Check tool requirement
            if (source.toolRequired() && !dropSourceManager.isProperTool(tool, blockType)) {
                continue;
            }

            // Check spawn conditions at block location
            if (source.conditions() != null && !source.conditions().check(block.getLocation())) {
                continue;
            }

            // Calculate chance with enchantments
            double chance = dropSourceManager.calculateDropChance(
                    source.chance(),
                    source.enchantBonus(),
                    tool
            );

            // Attempt drop
            dropSourceManager.tryDrop(
                    player,
                    candidate.collection(),
                    candidate.item(),
                    chance,
                    "block",
                    null
            );
        }
    }
}
