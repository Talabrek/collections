# Phase 3: GUI Safety - Research

**Researched:** 2026-01-21
**Domain:** Bukkit Inventory API, click event handling, GUI state management
**Confidence:** HIGH

## Summary

This research examines the current GUI implementation in the Collections plugin and identifies specific vulnerabilities that need to be addressed to prevent click exploits and state corruption. The codebase has a solid foundation with a proper GUIHolder interface pattern and centralized click handling, but has specific gaps in click type cancellation, state re-validation, and inventory handling.

The primary issues are:
1. **InventoryClickEvent handler doesn't check click location** - clicks in player inventory while GUI is open not properly handled
2. **No explicit ClickType filtering** - relies solely on event.setCancelled(true) which may not block all exploits
3. **No state versioning or staleness detection** - GUIs cache progress at open time, never re-validate
4. **Inventory full check occurs BEFORE giveRewards** - race window exists between check and actual reward distribution
5. **No double-claim protection** - rewardClaimed checked but GUI not refreshed atomically

**Primary recommendation:** Add comprehensive click type cancellation with explicit handling for all ClickTypes, implement progress re-fetch before mutations, and add atomic double-claim protection with proper inventory overflow handling.

## Current Implementation Analysis

### GUI Architecture

**Files:**
- `GUIHolder.java` (lines 1-33) - Interface defining click/close handlers
- `GUIManager.java` (lines 1-413) - Centralized GUI registry and utilities
- `GUIListener.java` (lines 1-108) - Event routing to GUI handlers
- `CollectionMenuGUI.java` (lines 1-462) - Main collection list GUI
- `CollectionDetailGUI.java` (lines 1-516) - Collection detail/claim GUI
- `ConfirmAddGUI.java` (lines 1-277) - Item add confirmation GUI

**Pattern:** Each GUI implements `GUIHolder`, is tracked in `GUIManager.openGuis` ConcurrentHashMap, and receives click events via `GUIListener`.

### GUIListener.java - Click Handling (lines 32-53)

```java
@EventHandler(priority = EventPriority.HIGH)
public void onInventoryClick(InventoryClickEvent event) {
    // ... player/holder validation ...

    // Only handle clicks in our GUI inventory
    if (!event.getInventory().equals(holder.getInventory())) {
        return;
    }

    // Cancel the event to prevent item movement
    event.setCancelled(true);

    // Route to the GUI handler
    holder.handleClick(event);
}
```

**ISSUE (GUI-01):** The check `event.getInventory().equals(holder.getInventory())` uses `getInventory()` which returns the TOP inventory. When player clicks their OWN inventory (bottom) while GUI is open:
- `event.getInventory()` returns GUI inventory (top)
- `event.getRawSlot() >= GUI_SIZE` indicates bottom inventory click
- Event is cancelled, but shift-click from bottom inventory can still interact with top

**ISSUE (GUI-01):** No explicit ClickType handling. While `setCancelled(true)` blocks most actions, certain exploits may slip through:
- `DOUBLE_CLICK` - can collect items from both inventories
- `HOTBAR_SWAP` (number keys) - may bypass in edge cases
- Drag events are handled separately but not coordinated with click events

### InventoryDragEvent Handling (lines 84-98)

```java
@EventHandler(priority = EventPriority.HIGH)
public void onInventoryDrag(InventoryDragEvent event) {
    // ... validation ...

    if (event.getInventory().equals(holder.getInventory())) {
        event.setCancelled(true);
    }
}
```

**GOOD:** Drag events are cancelled for GUI inventories.

**ISSUE (GUI-01):** Should also check if ANY of the drag slots are in the GUI inventory, not just top-level inventory match.

### CollectionDetailGUI.java - Reward Claiming (lines 434-479)

```java
private void attemptClaimReward() {
    PlayerProgress progress = playerDataManager.getProgress(player.getUniqueId());

    // Null check
    if (progress == null) { ... return; }

    // Completion check
    if (!progress.hasCompleted(collection.id())) { ... return; }

    // Already claimed check
    if (progress.hasClaimedReward(collection.id())) { ... return; }

    // Inventory space check
    if (!rewardManager.hasInventorySpace(player, rewards)) { ... return; }

    // Give rewards
    rewardManager.giveRewards(player, collection);

    // Mark as claimed
    playerDataManager.claimReward(player.getUniqueId(), collection.id());

    // Refresh GUI
    populateInventory();
}
```

**ISSUE (GUI-02):** Uses `getProgress()` which returns cached data, not fresh data. If player's progress changed (another player collected via shared mechanism, admin reset, etc.), this check uses stale data.

**ISSUE (GUI-03):** Inventory space check happens BEFORE reward distribution, but `giveRewards()` already handles overflow by dropping items. The current flow:
1. Check space - if not enough, reject with error message
2. Give rewards - if overflow, drop at feet + send "inventory full" message

This means players see "inventory full" error even though rewards CAN be given (just dropped). The SPEC says rewards should drop if inventory full, not be blocked.

**ISSUE (GUI-04):** No versioning or staleness detection. GUI shows completion status from when it was opened. If player's progress is reset while GUI is open, claim button still shows "claimable" based on cached state.

**ISSUE (GUI-04):** Race condition between `hasClaimedReward()` check and `claimReward()` call. Fast double-clicks could potentially call `attemptClaimReward()` twice before first claim persists.

### ConfirmAddGUI.java - Item Adding (lines 142-203)

```java
private void confirmAdd() {
    player.closeInventory();

    // Check if player still has the item in hand
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    if (!isMatchingItem(mainHand)) { ... return; }

    // Check if already has this item
    if (playerDataManager.hasItem(player.getUniqueId(), collectionId, itemId)) { ... return; }

    // Add to journal
    boolean added = playerDataManager.addItem(player.getUniqueId(), collectionId, itemId);
    // ... consume item, check completion ...
}
```

**GOOD (GUI-02):** Re-validates item is still in hand after GUI closes.
**GOOD (GUI-02):** Checks duplicate BEFORE adding.
**GOOD:** Uses `addItem()` which returns false if already exists, preventing double-add.

**POTENTIAL ISSUE (GUI-05):** If `playerDataManager.addItem()` triggers async database save that fails, the in-memory state is updated but persistence may fail. However, Phase 1 already added proper exception handling with SEVERE logging, which is acceptable.

### CollectionMenuGUI.java and CollectionDetailGUI.java - Data Gating

Both GUIs have proper data gating on open:

```java
public void open() {
    // Gate on data load - ensure player data is available
    if (playerDataManager.getProgressBlocking(player.getUniqueId()) == null) {
        player.sendMessage(Component.text("Loading your collection data...", NamedTextColor.YELLOW));
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                open();
            }
        }, null, 20L);
        return;
    }
    // ... populate and open ...
}
```

**GOOD (builds on Phase 2):** Uses `getProgressBlocking()` added in Phase 2 to wait for async load.

### RewardManager.java - Space Check and Distribution

```java
public boolean hasInventorySpace(Player player, CollectionRewards rewards) {
    int required = getRequiredSlots(rewards);
    // ... count empty slots ...
    return emptySlots >= required;
}

private void giveItems(Player player, CollectionRewards rewards) {
    // ... create items ...
    var leftover = player.getInventory().addItem(item);
    if (!leftover.isEmpty()) {
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.sendMessage(configManager.getMessage("inventory-full"));
    }
}
```

**ISSUE (GUI-03):** `getRequiredSlots()` returns `rewards.items().size()` which is the NUMBER of reward items, not accounting for stack sizes. A reward of "64 diamonds" needs 1 slot, but a reward of "3 different items" needs 3 slots. Current code is correct, but the space check is overly restrictive - it could allow claim and let overflow handling work.

## Identified Vulnerabilities

### GUI-01: Cancel all click types in GUIs

**Current state:** Basic `event.setCancelled(true)` covers most cases, but:

| ClickType | Current Handling | Risk |
|-----------|------------------|------|
| LEFT/RIGHT | Cancelled | LOW |
| SHIFT_LEFT/SHIFT_RIGHT | Cancelled | MEDIUM - may interact with player inventory |
| NUMBER_KEY (1-9) | Cancelled | MEDIUM - hotbar swap edge cases |
| DOUBLE_CLICK | Cancelled | MEDIUM - collects from both inventories |
| DROP/CONTROL_DROP | Cancelled | LOW |
| MIDDLE | Cancelled | LOW |

**Specific vulnerability:** Player inventory clicks while GUI is open pass the `event.getInventory().equals(holder.getInventory())` check (because `getInventory()` returns top inventory), so they ARE cancelled. However, the raw slot check in GUI handlers (`slot >= INVENTORY_SIZE`) returns early without action, which is correct.

**Recommended fix:** Explicitly check `event.getRawSlot() < 0` (outside inventory) and `event.getRawSlot() >= event.getInventory().getSize()` (player inventory) and handle appropriately.

### GUI-02: Re-fetch player progress before any mutation

**Current state:**
- `attemptClaimReward()` uses `getProgress()` which returns cached data
- `ConfirmAddGUI.confirmAdd()` properly re-validates item presence and duplicate status

**Recommended fix:** In `attemptClaimReward()`:
1. Call `getProgressBlocking()` instead of `getProgress()` for fresh data
2. Re-validate completion status against current state, not GUI-cached state

### GUI-03: Handle inventory full edge case when claiming rewards

**Current state:**
- `hasInventorySpace()` check blocks claim if not enough space
- `giveRewards()` has overflow handling (drop at feet)
- These conflict - SPEC says drop at feet, not block claim

**Recommended fix:** Remove the `hasInventorySpace()` pre-check. Let `giveRewards()` handle overflow by dropping items at player's feet. This is the documented behavior.

### GUI-04: Prevent reward claiming if progress changed since GUI opened

**Current state:** No staleness detection. GUI can be open for minutes while backend state changes.

**Race condition scenario:**
1. Player A opens GUI, sees collection complete
2. Admin runs `/collections reset <player> <collection>`
3. Player A clicks "Claim Reward" - passes completion check against stale cache
4. Rewards given for incomplete collection

**Recommended fix:**
1. Store collection completion state hash or version when GUI opens
2. On claim attempt, re-fetch progress and compare
3. If mismatch, refresh GUI and show "Progress changed, please try again"

**Double-click race scenario:**
1. Player clicks "Claim" rapidly twice
2. First click: passes `hasClaimedReward()` check, starts processing
3. Second click: passes `hasClaimedReward()` check (not yet updated), starts processing
4. Both calls reach `claimReward()` and `giveRewards()`

**Recommended fix:**
1. Add synchronized block or AtomicBoolean flag on claim processing per-player
2. Check and set claiming flag before any validation
3. Release flag in finally block

### GUI-05: Verify GUI state consistency after async operations

**Current state:** No async operations in current GUI handlers. Phase 2 moved data loading to blocking pattern.

**Recommended fix:** This requirement is largely satisfied by Phase 2's work. Additional validation:
1. Verify `populateInventory()` is always called after state-changing operations
2. Consider closing GUI and re-opening if major state change detected

## Recommended Fixes

### Fix 1: Comprehensive Click Cancellation (GUI-01)

**File:** `GUIListener.java`

**Changes:**
1. Cancel event FIRST, before any inventory check
2. Check if click is in player inventory section (rawSlot >= top inventory size)
3. For player inventory clicks, simply return after cancel (no action needed)
4. For drag events, check if ANY slot is in GUI inventory

```java
@EventHandler(priority = EventPriority.HIGH)
public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
        return;
    }

    GUIHolder holder = guiManager.getOpenGUI(player.getUniqueId());
    if (holder == null) {
        return;
    }

    // Cancel ALL clicks when our GUI is open
    event.setCancelled(true);

    // Only process clicks actually in our GUI, not player's inventory
    int topSize = holder.getInventory().getSize();
    int rawSlot = event.getRawSlot();

    // Click outside or in player inventory - already cancelled, nothing to do
    if (rawSlot < 0 || rawSlot >= topSize) {
        return;
    }

    // Route to the GUI handler
    holder.handleClick(event);
}
```

### Fix 2: Progress Re-fetch Before Mutation (GUI-02)

**File:** `CollectionDetailGUI.java`

**Changes to `attemptClaimReward()`:**

```java
private void attemptClaimReward() {
    // Re-fetch fresh progress
    PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());

    if (progress == null) {
        player.sendMessage(configManager.getMessage("no-permission"));
        guiManager.playErrorSound(player);
        return;
    }

    // Rest of validation against FRESH progress...
}
```

### Fix 3: Remove Inventory Full Pre-Check (GUI-03)

**File:** `CollectionDetailGUI.java`

**Remove:**
```java
if (!rewardManager.hasInventorySpace(player, rewards)) {
    player.sendMessage(configManager.getMessage("inventory-full"));
    guiManager.playErrorSound(player);
    return;
}
```

**Keep:** The overflow handling in `RewardManager.giveItems()` which drops items at feet.

### Fix 4: Double-Claim Prevention (GUI-04)

**File:** `CollectionDetailGUI.java`

**Add claiming lock:**

```java
private final AtomicBoolean claiming = new AtomicBoolean(false);

private void attemptClaimReward() {
    // Prevent double-click race
    if (!claiming.compareAndSet(false, true)) {
        return;
    }

    try {
        // ... existing claim logic ...
    } finally {
        claiming.set(false);
    }
}
```

**Alternative:** Use synchronized block per-collection claim via ConcurrentHashMap in GUIManager.

### Fix 5: State Change Detection (GUI-04)

**File:** `CollectionDetailGUI.java`

**Store state on open:**
```java
private final boolean wasCompleteOnOpen;
private final boolean wasClaimedOnOpen;

public CollectionDetailGUI(...) {
    // ... existing ...
    PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());
    this.wasCompleteOnOpen = progress != null && progress.hasCompleted(collection.id());
    this.wasClaimedOnOpen = progress != null && progress.hasClaimedReward(collection.id());
}
```

**Check on claim:**
```java
private void attemptClaimReward() {
    PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());

    // Detect state change since GUI opened
    boolean nowComplete = progress != null && progress.hasCompleted(collection.id());
    boolean nowClaimed = progress != null && progress.hasClaimedReward(collection.id());

    if (nowComplete != wasCompleteOnOpen || nowClaimed != wasClaimedOnOpen) {
        player.sendMessage(configManager.getMessage("progress-changed"));
        populateInventory();  // Refresh GUI
        guiManager.playErrorSound(player);
        return;
    }

    // Continue with claim...
}
```

## Dependencies/Risks

### Dependencies

1. **Phase 2 must be complete** - Uses `getProgressBlocking()` for fresh data access
2. **ConcurrentHashMap patterns from Phase 2** - Thread-safe progress access

### Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Over-aggressive click cancellation | LOW | Users confused by normal inventory actions | Test all ClickTypes thoroughly |
| Progress re-fetch latency | LOW | Slight delay on claim button click | Already using blocking pattern, ~instant for cached data |
| False positive state change detection | LOW | Annoys users who opened GUI long ago | Only matters if completion/claimed actually changed |
| AtomicBoolean claim lock stuck | VERY LOW | Player can't claim until GUI re-opened | Always release in finally block |

### Edge Cases

1. **Player quits while claiming** - EntityScheduler pattern handles this (retired callback)
2. **Server restart during claim** - Database save is immediate, rewards already given
3. **Multiple GUIs for same collection** - Each GUI instance has own claiming lock

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Click race prevention | Custom locking map | AtomicBoolean per-GUI | Simple, lightweight, auto-cleared on GUI close |
| Fresh data access | Custom cache invalidation | getProgressBlocking() | Already built in Phase 2 |
| Inventory overflow | Custom slot calculation | addItem() + dropItemNaturally() | Bukkit handles stacking correctly |

## Common Pitfalls

### Pitfall 1: Checking getInventory() instead of getRawSlot()

**What goes wrong:** `event.getInventory()` always returns the top inventory. Checking `event.getInventory().equals(holder.getInventory())` doesn't distinguish player inventory clicks.

**Why it happens:** Confusing API naming.

**How to avoid:** Use `event.getRawSlot()` to determine which inventory section was clicked.

### Pitfall 2: Trusting GUI-cached state for mutations

**What goes wrong:** GUI shows "complete" based on open-time data. Player claims rewards. Backend state already changed. Rewards given incorrectly.

**Why it happens:** GUIs cache state for display performance.

**How to avoid:** Always re-fetch fresh state before any mutation (add item, claim reward).

### Pitfall 3: Blocking claim on inventory full instead of dropping

**What goes wrong:** Player can't claim rewards even though system CAN give them (by dropping at feet).

**Why it happens:** Over-protective validation.

**How to avoid:** Let the reward system handle overflow. Inform player items dropped, don't block the claim.

### Pitfall 4: Not handling double-click races

**What goes wrong:** Fast clicks trigger multiple claim attempts before first completes.

**Why it happens:** Event handlers are not atomic.

**How to avoid:** Use AtomicBoolean or synchronized to prevent concurrent claim processing.

## Code Examples

### Verified: InventoryClickEvent slot checking

```java
// Source: Bukkit API documentation
// getRawSlot() returns the slot index across ALL inventories
// For a 54-slot chest GUI (6 rows):
// - Slots 0-53: Top inventory (GUI)
// - Slots 54-89: Player inventory (36 slots: 9 hotbar + 27 main)
// - Slot -999: Outside any inventory

int topSize = event.getInventory().getSize();
int rawSlot = event.getRawSlot();

if (rawSlot < 0) {
    // Click outside inventory
} else if (rawSlot < topSize) {
    // Click in top inventory (GUI)
} else {
    // Click in player's inventory
}
```

### Verified: AtomicBoolean for race prevention

```java
// Standard pattern for preventing concurrent execution
private final AtomicBoolean processing = new AtomicBoolean(false);

public void handleClick() {
    if (!processing.compareAndSet(false, true)) {
        return;  // Another click already processing
    }

    try {
        // Process click...
    } finally {
        processing.set(false);
    }
}
```

### Verified: Inventory overflow handling

```java
// Source: Bukkit Inventory.addItem() Javadoc
// Returns a HashMap of items that didn't fit
HashMap<Integer, ItemStack> leftover = inventory.addItem(items);

if (!leftover.isEmpty()) {
    for (ItemStack item : leftover.values()) {
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Check inventory equality | Check rawSlot position | Always best practice | Correct click handling |
| Block on inventory full | Let system drop overflow | SPEC requirement | Better UX |
| Trust cached state | Re-fetch on mutation | Phase 2 foundation | Prevents exploits |

## Open Questions

1. **Should we add visual feedback for claiming in progress?**
   - Could show temporary "Processing..." item in claim slot
   - Recommendation: Not needed - claim is fast enough

2. **Should we close GUI on state change instead of refresh?**
   - Current: Refresh GUI and show message
   - Alternative: Close GUI, force re-open
   - Recommendation: Refresh is less disruptive

## Sources

### Primary (HIGH confidence)
- Direct code analysis of current implementation
- Bukkit API documentation for InventoryClickEvent
- Phase 2 research for PlayerDataManager patterns

### Secondary (MEDIUM confidence)
- `.planning/research/BUGS.md` - Inventory click duplication patterns

## Metadata

**Confidence breakdown:**
- Click handling: HIGH - Based on Bukkit API documentation
- State management: HIGH - Based on code analysis
- Race conditions: HIGH - Standard concurrent programming patterns

**Research date:** 2026-01-21
**Valid until:** 60 days (stable APIs, no fast-moving dependencies)
