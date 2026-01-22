# Phase 16: Add Flow UX - Research

**Researched:** 2026-01-23
**Domain:** Bukkit Inventory GUI, GUI transitions, Collection display patterns
**Confidence:** HIGH

## Summary

This phase enhances the "add item to collection" flow by replacing the simple 3-row confirmation GUI with a rich experience that shows the full collection grid, progress summary, and Yes/No confirmation buttons. After confirming, the GUI transitions to show the collection view with the newly added item highlighted.

The existing codebase provides an excellent foundation:
- `ConfirmAddGUI` (27-slot) handles the current simple add confirmation
- `CollectionDetailGUI` (54-slot) shows full collection grids with collected/missing items
- `GUIManager` provides progress bars, item builders, and sound management
- `GUIListener` handles click routing and click cancellation robustly

**Primary recommendation:** Create a new `AddPreviewGUI` class that combines the collection grid display from `CollectionDetailGUI` with Yes/No buttons in the bottom row. After confirmation, transition to `CollectionDetailGUI` with a highlight flag for the newly added item.

## Current Implementation Analysis

### ConfirmAddGUI.java (Current Add Flow)

The current add flow uses a minimal 27-slot (3-row) GUI:

```
Layout (current):
Row 0: [filler] [filler] [filler] [filler] [ITEM]  [filler] [filler] [filler] [filler]
Row 1: [filler] [filler] [filler] [filler] [INFO]  [filler] [filler] [filler] [filler]
Row 2: [filler] [CONFIRM slot 11] [filler] [filler] [filler] [filler] [CANCEL slot 15] [filler] [filler]
```

**Key behaviors:**
- Shows the item being added (slot 4)
- Shows info panel with collection/item names (slot 13)
- Confirm button (slot 11) calls `confirmAdd()`
- Cancel button (slot 15) closes GUI with message

**Limitations:**
- No collection context - player can't see what they already have
- No progress indicator
- No visual preview of where item fits in collection

### CollectionDetailGUI.java (Collection Grid Display)

The detail GUI uses a 54-slot (6-row) layout:

```
Layout:
Rows 1-3: [border] [7 ITEM_SLOTS per row] [border]  (21 total item slots)
Row 4:    [border] [7 REWARD_SLOTS]       [border]
Row 5:    [BACK] [filler] [filler] [INFO] [filler] [filler] [filler] [filler] [CLAIM]
```

**Item slot positions:** `{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34}`

**Key methods to reuse:**
- `createItemIcon(item, collected, hasProgress)` - Shows collected items vs mystery placeholders
- `createInfoItem(progress)` - Shows collection status and progress bar
- `populateRewardPreview()` - Shows rewards (not needed for add flow)

### ItemUseListener.java (Entry Point)

Right-clicking a collection item triggers this flow:

```java
// Lines 39-95: Entry point for item-add flow
@EventHandler(priority = EventPriority.HIGH)
public void onPlayerInteract(PlayerInteractEvent event) {
    // Validate right-click, main hand, is collection item
    // Check not duplicate
    // Opens ConfirmAddGUI
    ConfirmAddGUI confirmGui = new ConfirmAddGUI(plugin, player, item, collection, collectionItem);
    confirmGui.open();
}
```

**This stays unchanged** - only `ConfirmAddGUI` replacement is needed.

### GUIManager.java (Shared Utilities)

Available utilities for the new GUI:

| Method | Purpose | Use in AddPreviewGUI |
|--------|---------|---------------------|
| `createFiller()` | Gray glass pane | Border fill |
| `createConfirmButton()` | Green "Confirm" button | Yes button |
| `createCancelButton()` | Red "Cancel" button | No button |
| `createProgressBar(current, max, length)` | MiniMessage progress string | Progress summary |
| `createProgressBarComponent(...)` | Component progress bar | Info item lore |
| `playOpenSound(player)` | GUI open sound | On open |
| `playClickSound(player)` | Button click sound | On button click |
| `playErrorSound(player)` | Error feedback | On error |
| `registerGUI(uuid, holder)` | Track open GUI | On open |
| `unregisterGUI(uuid)` | Cleanup tracking | On close/transition |

## New GUI Design

### AddPreviewGUI Layout (54 slots, 6 rows)

```
Row 0: [filler x9] - Top border
Row 1: [filler] [7 collection item slots: 10-16] [filler]
Row 2: [filler] [7 collection item slots: 19-25] [filler]
Row 3: [filler] [7 collection item slots: 28-34] [filler]
Row 4: [filler] [filler] [filler] [INFO slot 40] [filler] [filler] [filler] [filler] [filler]
Row 5: [filler] [filler] [YES slot 47] [filler] [ITEM slot 49] [filler] [NO slot 51] [filler] [filler]
```

**Slot assignments:**
- `ITEM_SLOTS[] = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34}` - 21 slots
- `INFO_SLOT = 40` - Progress summary item
- `ITEM_BEING_ADDED_SLOT = 49` - The collectible item being added (center bottom)
- `CONFIRM_SLOT = 47` - Yes button (left of item)
- `CANCEL_SLOT = 51` - No button (right of item)

### Item Display Logic

For each collection item slot:

```java
private ItemStack createSlotItem(CollectionItem item, boolean collected,
                                  boolean isItemBeingAdded, boolean hasProgress) {
    if (isItemBeingAdded) {
        // Highlight with glowing effect - this is what they're adding
        return ItemBuilder.of(item.material())
            .name("<green>" + item.name() + "</green>")
            .addLore("<gray>-----</gray>")
            .addLore("<yellow>Adding this item!</yellow>")
            .glowing()
            .build();
    } else if (collected) {
        // Already collected - show checkmark
        return ItemBuilder.of(item.material())
            .name("<gold>" + item.name() + "</gold>")
            .addLore("<green>Collected</green>")
            .build();
    } else if (hasProgress) {
        // Not collected, but player has some progress - show name
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
            .name("<yellow>" + item.name() + "</yellow>")
            .addLore("<red>Not yet found</red>")
            .build();
    } else {
        // No progress at all - mystery
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
            .name("<gray>???</gray>")
            .addLore("<dark_gray>Collect items to reveal</dark_gray>")
            .build();
    }
}
```

### Info Item (Progress Summary)

```java
private ItemStack createInfoItem() {
    PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
    int collected = progress != null ? progress.getCollectedCount(collection.id()) : 0;
    int total = collection.getItemCount();

    // Add 1 because we're about to add an item
    int afterAdd = collected + 1;

    List<String> lore = new ArrayList<>();
    lore.add("<gray>-----</gray>");
    lore.add("<white>Progress: " + guiManager.createProgressBar(collected, total, 10) + "</white>");
    lore.add("");
    lore.add("<yellow>After adding:</yellow>");
    lore.add("<white>" + guiManager.createProgressBar(afterAdd, total, 10) + "</white>");

    if (afterAdd >= total) {
        lore.add("");
        lore.add("<green>This will complete the collection!</green>");
    }

    return ItemBuilder.of(Material.PAPER)
        .name("<gold>" + collection.name() + "</gold>")
        .lore(lore)
        .build();
}
```

### GUI Transition After Confirm

After successful item addition, transition to `CollectionDetailGUI`:

```java
private void confirmAdd() {
    // ... existing validation (item in hand, not duplicate) ...

    boolean added = playerDataManager.addItem(player.getUniqueId(), collectionId, itemId);
    if (!added) {
        // Handle error
        return;
    }

    // Consume item, play sounds, send messages (existing logic)

    // Check if collection complete
    checkCollectionComplete();

    // Transition to collection detail view
    guiManager.unregisterGUI(player.getUniqueId());
    CollectionDetailGUI detailGui = new CollectionDetailGUI(plugin, player, collection);
    detailGui.setHighlightedItem(itemId);  // NEW: highlight the just-added item
    detailGui.open();
}
```

### Highlight Support in CollectionDetailGUI

Add highlighted item support:

```java
// New field
private String highlightedItemId = null;

// New setter
public void setHighlightedItem(String itemId) {
    this.highlightedItemId = itemId;
}

// Modified createItemIcon() to check highlight
private ItemStack createItemIcon(CollectionItem item, boolean collected, boolean hasProgress) {
    boolean isHighlighted = item.id().equals(highlightedItemId);

    if (collected) {
        ItemBuilder builder = ItemBuilder.of(item.material())
            .name("<gold>" + item.name() + "</gold>")
            .addLore("<green>Collected</green>");

        if (isHighlighted) {
            builder.addLore("");
            builder.addLore("<yellow>Just added!</yellow>");
            builder.glowing();
        }

        return builder.build();
    }
    // ... rest of method unchanged
}
```

## Architecture Patterns

### Pattern 1: GUI Transition (Close and Reopen)

**When to use:** Switching between different GUI types

**Implementation:**
```java
// In source GUI's action handler
guiManager.unregisterGUI(player.getUniqueId());
player.closeInventory();  // Clean close

// Open target GUI (on next tick to ensure clean state)
Bukkit.getScheduler().runTask(plugin, () -> {
    if (player.isOnline()) {
        NewGUI newGui = new NewGUI(plugin, player, ...);
        newGui.open();
    }
});
```

**Why next-tick:** The `closeInventory()` call triggers `InventoryCloseEvent` which may conflict with immediate reopening. Scheduling ensures clean state.

**Alternative (Bukkit pattern):** Paper's `player.openInventory()` can be called directly and will close the previous inventory automatically. The codebase already does this in `CollectionMenuGUI.openCollectionDetail()`:

```java
private void openCollectionDetail(Collection collection) {
    guiManager.unregisterGUI(player.getUniqueId());
    CollectionDetailGUI detailGui = new CollectionDetailGUI(plugin, player, collection);
    detailGui.open();  // Opens directly, closes current
}
```

**Recommendation:** Follow existing pattern - unregister, create new GUI, call open(). No need for next-tick scheduling.

### Pattern 2: Item Highlight with Glowing

**When to use:** Drawing attention to a specific slot

**Implementation:**
```java
ItemBuilder.of(material)
    .name("<gold>" + name + "</gold>")
    .glowing()  // Adds Unbreaking I + HIDE_ENCHANTS flag
    .build();
```

**Visual effect:** Item has enchantment glint without showing enchantment text.

### Pattern 3: Progress Preview (Before/After)

**When to use:** Showing what will happen if action is confirmed

**Implementation:**
```java
int current = progress.getCollectedCount(collectionId);
int total = collection.getItemCount();
int afterAction = current + 1;

lore.add("Current: " + progressBar(current, total));
lore.add("After: " + progressBar(afterAction, total));
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Item display icons | Custom material mapping | Copy from `CollectionDetailGUI.createItemIcon()` | Already handles collected/mystery/hint states |
| Progress bar | Custom string builder | `GUIManager.createProgressBar()` | Already exists, well-tested |
| Confirm/Cancel buttons | Custom item creation | `GUIManager.createConfirmButton()` / `createCancelButton()` | Consistent UX |
| GUI tracking | Custom player map | `GUIManager.registerGUI()` / `unregisterGUI()` | Handles cleanup on quit |
| Sound effects | Direct Sound enum | `GUIManager.playClickSound()` etc. | Configurable via config.yml |

## Common Pitfalls

### Pitfall 1: Not Unregistering GUI Before Transition

**What goes wrong:** Old GUI remains in `GUIManager.openGuis`, click events route to wrong handler.

**Why it happens:** Forgetting to call `unregisterGUI()` before opening new GUI.

**How to avoid:** Always call `guiManager.unregisterGUI(player.getUniqueId())` before creating/opening new GUI.

**Warning signs:** Click sounds play twice, or clicks do nothing.

### Pitfall 2: Item Validation After GUI Close

**What goes wrong:** Player moves item while GUI is open, confirm fails because item not in hand.

**Why it happens:** GUI events don't cancel player inventory clicks in bottom section.

**How to avoid:** Current `ConfirmAddGUI.confirmAdd()` already validates item is still in main hand. Preserve this check.

```java
ItemStack mainHand = player.getInventory().getItemInMainHand();
if (!isMatchingItem(mainHand)) {
    player.sendMessage(configManager.getMessage("item-not-found-in-hand"));
    guiManager.playErrorSound(player);
    return;
}
```

### Pitfall 3: Collection Size Exceeds Slot Count

**What goes wrong:** Collections with >21 items don't fit in the grid.

**Why it happens:** Fixed slot layout assumes max 21 items.

**How to avoid:** Current collections have 8-12 items. Add a check:

```java
if (collection.getItemCount() > ITEM_SLOTS.length) {
    plugin.getLogger().warning("Collection " + collection.id() +
        " has " + collection.getItemCount() + " items but grid only supports " +
        ITEM_SLOTS.length);
    // Show first 21 items only, or fall back to simpler GUI
}
```

**Current state:** Collections in codebase have 6-12 items, well under 21 limit.

### Pitfall 4: Highlight Lost on GUI Refresh

**What goes wrong:** If GUI refreshes (e.g., progress change detection), highlight disappears.

**Why it happens:** `populateInventory()` doesn't preserve highlight state.

**How to avoid:** Store `highlightedItemId` as instance field, not parameter. Use in `populateInventory()`.

### Pitfall 5: Not Handling Duplicate Add Attempt

**What goes wrong:** Player somehow opens add GUI for item they already have (edge case).

**Why it happens:** `ItemUseListener` should block this, but race condition possible.

**How to avoid:** Re-check in `confirmAdd()`:

```java
if (playerDataManager.hasItem(player.getUniqueId(), collectionId, itemId)) {
    player.sendMessage(configManager.getMessage("item-duplicate", "item", collectionItem.name()));
    guiManager.playErrorSound(player);
    player.closeInventory();
    return;
}
```

Current `ConfirmAddGUI` already has this check.

## Code Examples

### Full AddPreviewGUI Skeleton

```java
public class AddPreviewGUI implements GUIHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };
    private static final int INFO_SLOT = 40;
    private static final int ITEM_DISPLAY_SLOT = 49;
    private static final int CONFIRM_SLOT = 47;
    private static final int CANCEL_SLOT = 51;

    private final Collections plugin;
    private final GUIManager guiManager;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final Player player;
    private final ItemStack itemToAdd;
    private final Collection collection;
    private final CollectionItem collectionItem;
    private final Inventory inventory;

    public AddPreviewGUI(Collections plugin, Player player, ItemStack itemToAdd,
                         Collection collection, CollectionItem collectionItem) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.configManager = plugin.getConfigManager();
        this.playerDataManager = plugin.getPlayerDataManager();
        this.player = player;
        this.itemToAdd = itemToAdd.clone();
        this.collection = collection;
        this.collectionItem = collectionItem;

        Component title = configManager.parse("<gold>Add to " + collection.name() + "?</gold>");
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
    }

    public void open() {
        populateInventory();
        guiManager.registerGUI(player.getUniqueId(), this);
        player.openInventory(inventory);
        guiManager.playOpenSound(player);
    }

    private void populateInventory() {
        // Fill with glass panes
        ItemStack filler = guiManager.createFiller();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Populate collection grid
        PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());
        boolean hasProgress = progress != null && progress.getCollectedCount(collection.id()) > 0;

        List<CollectionItem> items = collection.items();
        for (int i = 0; i < ITEM_SLOTS.length && i < items.size(); i++) {
            CollectionItem item = items.get(i);
            boolean isItemBeingAdded = item.id().equals(collectionItem.id());
            boolean collected = progress != null && progress.hasItem(collection.id(), item.id());
            inventory.setItem(ITEM_SLOTS[i], createSlotItem(item, collected, isItemBeingAdded, hasProgress));
        }

        // Info item with progress
        inventory.setItem(INFO_SLOT, createInfoItem());

        // Item being added (display copy)
        inventory.setItem(ITEM_DISPLAY_SLOT, itemToAdd);

        // Confirm and cancel buttons
        inventory.setItem(CONFIRM_SLOT, guiManager.createConfirmButton());
        inventory.setItem(CANCEL_SLOT, guiManager.createCancelButton());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        guiManager.playClickSound(player);

        if (slot == CONFIRM_SLOT) {
            confirmAdd();
        } else if (slot == CANCEL_SLOT) {
            cancel();
        }
    }

    private void confirmAdd() {
        // Close GUI first
        player.closeInventory();

        // Validate item still in hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isMatchingItem(mainHand)) {
            player.sendMessage(configManager.getMessage("item-not-found-in-hand"));
            guiManager.playErrorSound(player);
            return;
        }

        // Check not already collected (race condition guard)
        if (playerDataManager.hasItem(player.getUniqueId(), collection.id(), collectionItem.id())) {
            player.sendMessage(configManager.getMessage("item-duplicate", "item", collectionItem.name()));
            guiManager.playErrorSound(player);
            return;
        }

        // Add to journal
        boolean added = playerDataManager.addItem(player.getUniqueId(), collection.id(), collectionItem.id());
        if (!added) {
            player.sendMessage(configManager.getMessage("item-duplicate", "item", collectionItem.name()));
            guiManager.playErrorSound(player);
            return;
        }

        // ... consume item, metrics, notifications (copy from ConfirmAddGUI) ...

        // Transition to collection detail with highlight
        CollectionDetailGUI detailGui = new CollectionDetailGUI(plugin, player, collection);
        detailGui.setHighlightedItem(collectionItem.id());
        detailGui.open();
    }

    // ... remaining methods from ConfirmAddGUI ...
}
```

### GUIType Enum Update

Add new GUI type:

```java
public enum GUIType {
    COLLECTION_MENU,
    COLLECTION_DETAIL,
    CONFIRM_ADD,
    ADD_PREVIEW  // NEW
}
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Simple 3-row confirmation | Full collection grid preview | Better UX, context |
| No progress visibility | Progress bar with before/after | Informed decision |
| Close on confirm | Transition to detail view | Immediate feedback |
| No highlight | Glowing highlight on added item | Visual confirmation |

## Requirements Mapping

| Requirement | Implementation |
|-------------|----------------|
| UX-01: Full collection grid | 21 item slots showing all collection items |
| UX-02: Progress summary | Info item showing X/Y collected with progress bar |
| UX-03: Yes/No buttons | CONFIRM_SLOT (47) and CANCEL_SLOT (51) |
| UX-04: Transition to collection view | Open CollectionDetailGUI with highlight after confirm |

## Open Questions

1. **Should the item being added be in the grid or separate?**
   - Current design: Both - highlighted in grid AND shown at bottom
   - Alternative: Only in grid, remove bottom display
   - Recommendation: Keep both - grid shows context, bottom shows actual item

2. **What if player cancels - return to inventory or show message?**
   - Current ConfirmAddGUI: Closes GUI, sends "add-cancelled" message
   - Recommendation: Keep same behavior for consistency

3. **Should clicking on other collection items do anything?**
   - Option A: No action (just display)
   - Option B: Show item tooltip/details
   - Recommendation: No action - keep focus on add decision

4. **Highlight duration in detail view?**
   - Option A: Permanent until GUI closes
   - Option B: Fade after few seconds
   - Recommendation: Permanent (simple, no timer needed)

## Sources

### Primary (HIGH confidence)
- Direct code analysis: `ConfirmAddGUI.java`, `CollectionDetailGUI.java`, `GUIManager.java`
- Direct code analysis: `ItemUseListener.java`, `GUIListener.java`, `GUIHolder.java`
- Phase 3 research: GUI safety patterns and click handling

### Secondary (MEDIUM confidence)
- Phase 10 research: Notification and UX patterns
- Existing config.yml patterns

## Metadata

**Confidence breakdown:**
- GUI layout: HIGH - Based on existing CollectionDetailGUI patterns
- Transition pattern: HIGH - Based on existing code in CollectionMenuGUI
- Item display: HIGH - Directly reusing CollectionDetailGUI.createItemIcon() patterns
- Highlight feature: MEDIUM - New feature, but uses existing glowing() pattern

**Research date:** 2026-01-23
**Valid until:** 60 days (stable domain, no external dependencies)
