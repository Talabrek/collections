package com.blockworlds.collections.manager;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.model.BlockDropSource;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.CollectionItem;
import com.blockworlds.collections.model.DropSources;
import com.blockworlds.collections.model.FishingDropSource;
import com.blockworlds.collections.model.LootDropSource;
import com.blockworlds.collections.model.MobDropSource;
import com.blockworlds.collections.util.ItemBuilder;
import com.blockworlds.collections.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages alternative drop sources for collection items.
 * Provides efficient lookup of items by entity type, block type, etc.
 * and handles drop chance calculation with enchantment bonuses.
 */
public class DropSourceManager {

    private final Collections plugin;
    private final CollectionManager collectionManager;

    // Indexes for efficient lookup
    private final Map<EntityType, List<DropCandidate<MobDropSource>>> mobDropIndex = new HashMap<>();
    private final Map<Material, List<DropCandidate<BlockDropSource>>> blockDropIndex = new HashMap<>();
    private final List<DropCandidate<FishingDropSource>> fishingDrops = new ArrayList<>();
    private final Map<String, List<DropCandidate<LootDropSource>>> lootDropIndex = new HashMap<>();

    // Enchantment mapping
    private static final Map<String, Enchantment> ENCHANT_MAP = new HashMap<>();

    static {
        ENCHANT_MAP.put("looting", Enchantment.LOOTING);
        ENCHANT_MAP.put("fortune", Enchantment.FORTUNE);
        ENCHANT_MAP.put("luck-of-the-sea", Enchantment.LUCK_OF_THE_SEA);
        ENCHANT_MAP.put("luck_of_the_sea", Enchantment.LUCK_OF_THE_SEA);
    }

    // Config values (could be loaded from config.yml)
    private double lootingCap = 0.5;
    private double fortuneCap = 0.5;
    private double luckOfTheSeaCap = 0.3;
    private boolean allowCreativeDrops = false;

    // Messages
    private String mobDropMessage = "<green>A <yellow><item></yellow> dropped from the <mob>!";
    private String blockDropMessage = "<green>You found a <yellow><item></yellow>!";
    private String fishingDropMessage = "<green>You fished up a <yellow><item></yellow>!";
    private String lootDropMessage = "<green>You discovered a <yellow><item></yellow> in the chest!";

    public DropSourceManager(Collections plugin) {
        this.plugin = plugin;
        this.collectionManager = plugin.getCollectionManager();
    }

    /**
     * Build indexes for efficient drop source lookup.
     * Should be called after collections are loaded.
     */
    public void buildIndexes() {
        mobDropIndex.clear();
        blockDropIndex.clear();
        fishingDrops.clear();
        lootDropIndex.clear();

        for (Collection collection : collectionManager.getAllCollections().values()) {
            for (CollectionItem item : collection.items()) {
                DropSources sources = item.dropSources();
                if (sources == null || !sources.hasAnySource()) continue;

                // Index mob drops
                for (MobDropSource source : sources.mobs()) {
                    for (EntityType entity : source.entities()) {
                        mobDropIndex.computeIfAbsent(entity, k -> new ArrayList<>())
                                .add(new DropCandidate<>(collection, item, source));
                    }
                }

                // Index block drops
                for (BlockDropSource source : sources.blocks()) {
                    for (Material block : source.blockTypes()) {
                        blockDropIndex.computeIfAbsent(block, k -> new ArrayList<>())
                                .add(new DropCandidate<>(collection, item, source));
                    }
                }

                // Index fishing drops
                for (FishingDropSource source : sources.fishing()) {
                    fishingDrops.add(new DropCandidate<>(collection, item, source));
                }

                // Index loot drops
                for (LootDropSource source : sources.loot()) {
                    for (String table : source.lootTables()) {
                        lootDropIndex.computeIfAbsent(table.toUpperCase(), k -> new ArrayList<>())
                                .add(new DropCandidate<>(collection, item, source));
                    }
                }
            }
        }

        plugin.getLogger().info("Built drop source indexes: " +
                mobDropIndex.size() + " mob types, " +
                blockDropIndex.size() + " block types, " +
                fishingDrops.size() + " fishing sources, " +
                lootDropIndex.size() + " loot tables");
    }

    /**
     * Get all mob drop candidates for an entity type.
     */
    public List<DropCandidate<MobDropSource>> getMobDropCandidates(EntityType type) {
        return mobDropIndex.getOrDefault(type, List.of());
    }

    /**
     * Get all block drop candidates for a block type.
     */
    public List<DropCandidate<BlockDropSource>> getBlockDropCandidates(Material type) {
        return blockDropIndex.getOrDefault(type, List.of());
    }

    /**
     * Get all fishing drop candidates.
     */
    public List<DropCandidate<FishingDropSource>> getFishingDropCandidates() {
        return fishingDrops;
    }

    /**
     * Get all loot drop candidates for a loot table.
     */
    public List<DropCandidate<LootDropSource>> getLootDropCandidates(String lootTableKey) {
        // Try exact match first
        String normalizedKey = lootTableKey.toUpperCase()
                .replace("MINECRAFT:CHESTS/", "")
                .replace("CHESTS/", "")
                .replace("/", "_")
                .replace("-", "_");

        List<DropCandidate<LootDropSource>> candidates = lootDropIndex.get(normalizedKey);
        if (candidates != null) {
            return candidates;
        }

        // Try partial match
        List<DropCandidate<LootDropSource>> matches = new ArrayList<>();
        for (Map.Entry<String, List<DropCandidate<LootDropSource>>> entry : lootDropIndex.entrySet()) {
            if (normalizedKey.contains(entry.getKey())) {
                matches.addAll(entry.getValue());
            }
        }
        return matches;
    }

    /**
     * Calculate final drop chance with enchantment bonuses.
     *
     * @param baseChance   Base drop chance (0.0 to 1.0)
     * @param enchantBonus Map of enchantment name to bonus per level
     * @param tool         Tool used (may be null)
     * @return Final drop chance, capped at 1.0
     */
    public double calculateDropChance(double baseChance, Map<String, Double> enchantBonus, ItemStack tool) {
        double finalChance = baseChance;

        if (tool != null && enchantBonus != null && !enchantBonus.isEmpty()) {
            ItemMeta meta = tool.getItemMeta();
            if (meta != null) {
                for (Map.Entry<String, Double> entry : enchantBonus.entrySet()) {
                    Enchantment ench = ENCHANT_MAP.get(entry.getKey().toLowerCase());
                    if (ench != null) {
                        int level = meta.getEnchantLevel(ench);
                        if (level > 0) {
                            double bonus = level * entry.getValue();
                            // Apply cap
                            double cap = getEnchantCap(entry.getKey());
                            finalChance += Math.min(bonus, cap);
                        }
                    }
                }
            }
        }

        return Math.min(finalChance, 1.0);
    }

    /**
     * Get the cap for an enchantment bonus.
     */
    private double getEnchantCap(String enchantName) {
        return switch (enchantName.toLowerCase()) {
            case "looting" -> lootingCap;
            case "fortune" -> fortuneCap;
            case "luck-of-the-sea", "luck_of_the_sea" -> luckOfTheSeaCap;
            default -> 0.5; // Default cap
        };
    }

    /**
     * Attempt to drop a collection item for a player.
     *
     * @param player     The player receiving the drop
     * @param collection The collection the item belongs to
     * @param item       The item to drop
     * @param chance     The calculated drop chance
     * @param sourceType The type of drop source ("mob", "block", "fishing", "loot")
     * @param extraInfo  Extra info for message (e.g., mob name)
     * @return true if item was dropped
     */
    public boolean tryDrop(Player player, Collection collection, CollectionItem item,
                           double chance, String sourceType, String extraInfo) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }

        // Create the collection item
        ItemStack drop = createCollectionItem(collection, item);

        // Give to player
        giveItemToPlayer(player, drop);

        // Send message
        sendDropMessage(player, item.name(), sourceType, extraInfo);

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        return true;
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
        return ItemBuilder.of(item.material())
                .name("<gold>" + item.name() + "</gold>")
                .lore(fullLore)
                .data(PDCKeys.COLLECTION_ID(), collection.id())
                .data(PDCKeys.ITEM_ID(), item.id())
                .data(PDCKeys.SOULBOUND(), item.soulbound() ? (byte) 1 : (byte) 0)
                .build();
    }

    /**
     * Give an item to a player, dropping at feet if inventory is full.
     */
    private void giveItemToPlayer(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
            player.sendMessage(Component.text("Your inventory was full - item dropped at your feet!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
    }

    /**
     * Send the appropriate drop message to a player.
     */
    private void sendDropMessage(Player player, String itemName, String sourceType, String extraInfo) {
        String messageTemplate = switch (sourceType) {
            case "mob" -> mobDropMessage;
            case "block" -> blockDropMessage;
            case "fishing" -> fishingDropMessage;
            case "loot" -> lootDropMessage;
            default -> "<green>You found a <yellow><item></yellow>!";
        };

        Component message = MiniMessage.miniMessage().deserialize(messageTemplate,
                Placeholder.unparsed("item", itemName),
                Placeholder.unparsed("mob", extraInfo != null ? extraInfo : "mob"));

        player.sendMessage(message);
    }

    /**
     * Check if creative mode drops are allowed.
     */
    public boolean isAllowCreativeDrops() {
        return allowCreativeDrops;
    }

    /**
     * Check if a tool has silk touch.
     */
    public boolean hasSilkTouch(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta()) return false;
        return tool.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH);
    }

    /**
     * Check if a tool is the proper tool for a block.
     */
    public boolean isProperTool(ItemStack tool, Material blockType) {
        if (tool == null) return false;

        Material toolType = tool.getType();
        String blockName = blockType.name();

        // Check for pickaxe blocks
        if (blockName.contains("STONE") || blockName.contains("ORE") ||
                blockName.contains("BRICK") || blockName.contains("CONCRETE")) {
            return toolType.name().contains("PICKAXE");
        }

        // Check for axe blocks
        if (blockName.contains("LOG") || blockName.contains("WOOD") ||
                blockName.contains("PLANK")) {
            return toolType.name().contains("AXE");
        }

        // Check for shovel blocks
        if (blockName.contains("DIRT") || blockName.contains("SAND") ||
                blockName.contains("GRAVEL") || blockName.contains("CLAY")) {
            return toolType.name().contains("SHOVEL");
        }

        // Check for hoe blocks (leaves, hay, etc.)
        if (blockName.contains("LEAVES") || blockName.contains("HAY")) {
            return toolType.name().contains("HOE");
        }

        // Default: any tool is fine
        return true;
    }

    /**
     * Container for a drop candidate with collection and source info.
     */
    public record DropCandidate<T>(
            Collection collection,
            CollectionItem item,
            T source
    ) {}
}
