package com.blockworlds.collections.listener;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.manager.DropSourceManager;
import com.blockworlds.collections.manager.DropSourceManager.DropCandidate;
import com.blockworlds.collections.model.LootDropSource;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener for container loot drop sources.
 * Handles LootGenerateEvent to add collection items to naturally-generated chests.
 */
public class LootDropListener implements Listener {

    private final Collections plugin;
    private final DropSourceManager dropSourceManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private String lootDropMessage = "<green>You discovered a <yellow><item></yellow> in the chest!";

    public LootDropListener(Collections plugin) {
        this.plugin = plugin;
        this.dropSourceManager = plugin.getDropSourceManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        // Must be triggered by a player
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Get loot table key
        LootTable lootTable = event.getLootTable();
        NamespacedKey key = lootTable.getKey();
        String lootTableKey = key.getKey();

        // Get all loot drop candidates for this loot table
        List<DropCandidate<LootDropSource>> candidates = dropSourceManager.getLootDropCandidates(lootTableKey);
        if (candidates.isEmpty()) {
            return;
        }

        Location loc = event.getLootContext().getLocation();

        for (DropCandidate<LootDropSource> candidate : candidates) {
            LootDropSource source = candidate.source();

            // Check spawn conditions at chest location
            if (source.conditions() != null && loc != null && !source.conditions().check(loc)) {
                continue;
            }

            // Roll for drop (no enchant bonus for loot)
            if (ThreadLocalRandom.current().nextDouble() >= source.chance()) {
                continue;
            }

            // Create the collection item
            ItemStack drop = createCollectionItem(candidate);

            // Add to the generated loot
            event.getLoot().add(drop);

            // Notify the player
            sendLootMessage(player, candidate.item().name());
        }
    }

    /**
     * Create a physical collection item with proper tags.
     */
    private ItemStack createCollectionItem(DropCandidate<LootDropSource> candidate) {
        // Build lore with hint to add to journal
        List<String> fullLore = new ArrayList<>(candidate.item().lore());
        fullLore.add(""); // Empty line
        fullLore.add("<gray>Part of: <gold>" + candidate.collection().name() + "</gold></gray>");
        fullLore.add("<gray>Right-click to add to journal</gray>");

        if (candidate.item().soulbound()) {
            fullLore.add("<red>Soulbound</red>");
        }

        // Create item using ItemBuilder
        return ItemBuilder.of(candidate.item().material())
                .name("<gold>" + candidate.item().name() + "</gold>")
                .lore(fullLore)
                .data(PDCKeys.COLLECTION_ID(), candidate.collection().id())
                .data(PDCKeys.ITEM_ID(), candidate.item().id())
                .data(PDCKeys.SOULBOUND(), candidate.item().soulbound() ? (byte) 1 : (byte) 0)
                .build();
    }

    /**
     * Send a loot discovery message to a player.
     */
    private void sendLootMessage(Player player, String itemName) {
        Component message = miniMessage.deserialize(lootDropMessage,
                Placeholder.unparsed("item", itemName));
        player.sendMessage(message);
    }
}
