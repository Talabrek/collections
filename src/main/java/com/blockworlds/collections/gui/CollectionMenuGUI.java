package com.blockworlds.collections.gui;

import com.blockworlds.collections.Collections;
import com.blockworlds.collections.config.ConfigManager;
import com.blockworlds.collections.manager.CollectionManager;
import com.blockworlds.collections.manager.PlayerDataManager;
import com.blockworlds.collections.model.Collection;
import com.blockworlds.collections.model.PlayerProgress;
import com.blockworlds.collections.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Main collection journal GUI showing all collections with progress.
 */
public class CollectionMenuGUI implements GUIHolder {

    private final Collections plugin;
    private final GUIManager guiManager;
    private final ConfigManager configManager;
    private final CollectionManager collectionManager;
    private final PlayerDataManager playerDataManager;
    private final Player player;
    private final Inventory inventory;

    private int currentPage = 0;
    private SortType sortType = SortType.ALPHABETICAL;
    private FilterType filterType = FilterType.ALL;

    // Layout constants
    private static final int INVENTORY_SIZE = 54;  // 6 rows
    private static final int ITEMS_PER_PAGE = 28;  // 7x4 grid (rows 1-4, slots 9-44 excluding edges)
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    // Navigation slots (row 5)
    private static final int PREV_SLOT = 45;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int SORT_SLOT = 46;
    private static final int FILTER_SLOT = 47;
    private static final int STATS_SLOT = 51;
    private static final int CLOSE_SLOT = 52;

    /**
     * Sort types for collections.
     */
    public enum SortType {
        ALPHABETICAL("A-Z"),
        PROGRESS("Progress"),
        TIER("Tier"),
        COMPLETION("Complete First");

        private final String displayName;

        SortType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public SortType next() {
            SortType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /**
     * Filter types for collections.
     */
    public enum FilterType {
        ALL("All"),
        IN_PROGRESS("In Progress"),
        COMPLETE("Complete"),
        INCOMPLETE("Incomplete");

        private final String displayName;

        FilterType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public FilterType next() {
            FilterType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public CollectionMenuGUI(Collections plugin, Player player) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.configManager = plugin.getConfigManager();
        this.collectionManager = plugin.getCollectionManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.player = player;

        Component title = configManager.parse("<gold>📖 Collection Journal</gold>");
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
    }

    /**
     * Open the GUI for the player.
     */
    public void open() {
        populateInventory();
        guiManager.registerGUI(player.getUniqueId(), this);
        player.openInventory(inventory);
        guiManager.playOpenSound(player);
    }

    /**
     * Populate the inventory with items.
     */
    private void populateInventory() {
        inventory.clear();

        // Fill with glass panes
        ItemStack filler = guiManager.createFiller();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Get sorted and filtered collections
        List<Collection> collections = getSortedAndFilteredCollections();
        int maxPage = Math.max(0, (collections.size() - 1) / ITEMS_PER_PAGE);

        // Ensure current page is valid
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }

        // Populate collection icons
        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEM_SLOTS.length && startIndex + i < collections.size(); i++) {
            Collection collection = collections.get(startIndex + i);
            inventory.setItem(ITEM_SLOTS[i], createCollectionIcon(collection));
        }

        // Clear unused item slots (show nothing, not filler)
        for (int i = collections.size() - startIndex; i < ITEM_SLOTS.length; i++) {
            if (i >= 0) {
                inventory.setItem(ITEM_SLOTS[i], null);
            }
        }

        // Navigation buttons
        inventory.setItem(PREV_SLOT, guiManager.createPrevButton(currentPage));
        inventory.setItem(PAGE_SLOT, guiManager.createPageIndicator(currentPage, maxPage));
        inventory.setItem(NEXT_SLOT, guiManager.createNextButton(currentPage, maxPage));
        inventory.setItem(SORT_SLOT, guiManager.createSortButton(sortType.getDisplayName()));
        inventory.setItem(FILTER_SLOT, guiManager.createFilterButton(filterType.getDisplayName()));
        inventory.setItem(STATS_SLOT, guiManager.createStatsButton());
        inventory.setItem(CLOSE_SLOT, guiManager.createCloseButton());
    }

    /**
     * Create an icon for a collection.
     */
    private ItemStack createCollectionIcon(Collection collection) {
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        int collected = progress != null ? progress.getCollectedCount(collection.id()) : 0;
        int total = collection.getItemCount();
        boolean complete = progress != null && progress.hasCompleted(collection.id());

        // Check meta-collection requirements
        List<String> required = collection.requiredCollections();
        boolean isMetaCollection = !required.isEmpty();
        boolean metaRequirementsMet = progress != null && progress.areMetaRequirementsMet(required);
        int metaProgress = progress != null ? progress.getMetaProgressCount(required) : 0;

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("<gray>─────────────────</gray>");

        // Description
        if (!collection.description().isEmpty()) {
            lore.add("<gray><italic>" + collection.description() + "</italic></gray>");
            lore.add("");
        }

        // Meta-collection requirements display
        if (isMetaCollection) {
            lore.add("<white>Required Collections:</white>");
            for (String reqId : required) {
                Collection reqCollection = collectionManager.getCollection(reqId);
                String reqName = reqCollection != null ? reqCollection.name() : reqId;
                boolean reqComplete = progress != null && progress.hasCompleted(reqId);
                if (reqComplete) {
                    lore.add("<green>  ✓ " + reqName + "</green>");
                } else {
                    lore.add("<red>  ✗ " + reqName + "</red>");
                }
            }
            lore.add("<gray>Progress: " + metaProgress + "/" + required.size() + " collections</gray>");
            lore.add("");
        }

        // Progress bar
        lore.add("<white>Progress: </white>" + guiManager.createProgressBar(collected, total, 10));

        // Missing items breakdown (simplified for now)
        if (!complete && collected < total) {
            int missing = total - collected;
            lore.add("<gray>• " + missing + " item" + (missing != 1 ? "s" : "") + " missing</gray>");
        }

        lore.add("");
        lore.add("<white>Tier: </white><" + collection.tier().getColor().toString().toLowerCase() + ">" +
                collection.tier().name() + "</" + collection.tier().getColor().toString().toLowerCase() + ">");

        // Zones hint
        if (!collection.allowedZones().isEmpty()) {
            String zones = String.join(", ", collection.allowedZones());
            lore.add("<gray>Zones: " + zones + "</gray>");
        }

        lore.add("");
        if (complete) {
            lore.add("<green>✓ Complete! Click to view.</green>");
        } else if (isMetaCollection && !metaRequirementsMet) {
            lore.add("<red>⚠ Complete required collections first</red>");
        } else {
            lore.add("<yellow>Click to view details</yellow>");
        }

        // Build item
        Material iconMaterial = collection.icon();
        // Show locked icon if meta requirements not met
        if (isMetaCollection && !metaRequirementsMet && !complete) {
            iconMaterial = Material.BARRIER;
        }

        ItemBuilder builder = ItemBuilder.of(iconMaterial)
                .name("<gold>" + collection.name() + "</gold>")
                .lore(lore);

        // Add glint if complete
        if (complete) {
            builder.glowing();
        }

        return builder.build();
    }

    /**
     * Get collections sorted and filtered according to current settings.
     */
    private List<Collection> getSortedAndFilteredCollections() {
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        java.util.Collection<Collection> allCollections = collectionManager.getAllCollections().values();

        // Filter
        List<Collection> filtered = new ArrayList<>();
        for (Collection collection : allCollections) {
            boolean include = switch (filterType) {
                case ALL -> true;
                case IN_PROGRESS -> {
                    if (progress == null) yield false;
                    int collected = progress.getCollectedCount(collection.id());
                    yield collected > 0 && !progress.hasCompleted(collection.id());
                }
                case COMPLETE -> progress != null && progress.hasCompleted(collection.id());
                case INCOMPLETE -> progress == null || !progress.hasCompleted(collection.id());
            };
            if (include) {
                filtered.add(collection);
            }
        }

        // Sort
        Comparator<Collection> comparator = switch (sortType) {
            case ALPHABETICAL -> Comparator.comparing(Collection::name);
            case PROGRESS -> {
                if (progress == null) {
                    yield Comparator.comparing(Collection::name);
                }
                PlayerProgress p = progress;
                yield Comparator.comparingDouble((Collection c) -> {
                    int collected = p.getCollectedCount(c.id());
                    int total = c.getItemCount();
                    return total > 0 ? (double) collected / total : 0;
                }).reversed().thenComparing(Collection::name);
            }
            case TIER -> Comparator.comparing(Collection::tier).thenComparing(Collection::name);
            case COMPLETION -> {
                if (progress == null) {
                    yield Comparator.comparing(Collection::name);
                }
                PlayerProgress p = progress;
                yield Comparator.comparing((Collection c) -> p.hasCompleted(c.id()))
                        .reversed()
                        .thenComparing(Collection::name);
            }
        };

        filtered.sort(comparator);
        return filtered;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        guiManager.playClickSound(player);

        // Check navigation buttons
        switch (slot) {
            case PREV_SLOT -> {
                if (currentPage > 0) {
                    currentPage--;
                    populateInventory();
                }
            }
            case NEXT_SLOT -> {
                List<Collection> collections = getSortedAndFilteredCollections();
                int maxPage = Math.max(0, (collections.size() - 1) / ITEMS_PER_PAGE);
                if (currentPage < maxPage) {
                    currentPage++;
                    populateInventory();
                }
            }
            case SORT_SLOT -> {
                sortType = sortType.next();
                currentPage = 0;  // Reset to first page
                populateInventory();
            }
            case FILTER_SLOT -> {
                filterType = filterType.next();
                currentPage = 0;  // Reset to first page
                populateInventory();
            }
            case STATS_SLOT -> {
                // Show stats in chat for now (could be separate GUI later)
                showStats();
            }
            case CLOSE_SLOT -> {
                player.closeInventory();
            }
            default -> {
                // Check if clicking on a collection icon
                for (int i = 0; i < ITEM_SLOTS.length; i++) {
                    if (ITEM_SLOTS[i] == slot) {
                        List<Collection> collections = getSortedAndFilteredCollections();
                        int index = currentPage * ITEMS_PER_PAGE + i;
                        if (index < collections.size()) {
                            openCollectionDetail(collections.get(index));
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * Open the detail view for a collection.
     */
    private void openCollectionDetail(Collection collection) {
        // Close this GUI and open detail
        guiManager.unregisterGUI(player.getUniqueId());
        CollectionDetailGUI detailGui = new CollectionDetailGUI(plugin, player, collection);
        detailGui.open();
    }

    /**
     * Show player stats in chat.
     */
    private void showStats() {
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());

        player.sendMessage(Component.text()
                .append(Component.text("=== ", NamedTextColor.GOLD))
                .append(Component.text("Your Collection Stats", NamedTextColor.YELLOW))
                .append(Component.text(" ===", NamedTextColor.GOLD))
                .build());

        if (progress == null) {
            player.sendMessage(Component.text("No collection data found.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text()
                .append(Component.text("Items Collected: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectiblesCollected()), NamedTextColor.GREEN))
                .build());

        player.sendMessage(Component.text()
                .append(Component.text("Collections Completed: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(progress.getTotalCollectionsCompleted()), NamedTextColor.GREEN))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(collectionManager.getCollectionCount()), NamedTextColor.WHITE))
                .build());
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Nothing special to do on close
    }

    @Override
    public GUIType getType() {
        return GUIType.COLLECTION_MENU;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
