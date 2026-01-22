# Phase 10 Research: Progress Notifications

**Researched:** 2026-01-22
**Domain:** Player feedback UI (Adventure API, Minecraft sounds)
**Confidence:** HIGH

## Summary

This phase adds visual and audio feedback when players collect items and complete collections. The existing codebase already has established patterns for notifications (ConfigManager messages, sound playback, title/subtitle), so this phase builds on that foundation. The key insight is that notification logic should hook into the existing `ConfirmAddGUI.confirmAdd()` method rather than creating separate notification infrastructure.

**Primary recommendation:** Create a dedicated `NotificationManager` class that handles all notification types (actionbar, title, chat, sound) with configurable styles. Hook into the existing item-add flow in `ConfirmAddGUI` which already plays sounds and sends messages.

## Existing Code Analysis

### ItemUseListener Flow

The `ItemUseListener` handles right-clicking collection items in player inventory:

```
Player right-clicks item in hand
    -> ItemUseListener.onPlayerInteract()
    -> Validates item has COLLECTION_ID PDC tag
    -> Checks player doesn't already have item (spam prevention)
    -> Opens ConfirmAddGUI
```

This listener does NOT handle collecting from the world. That flow is:

```
Player right-clicks collectible entity in world
    -> CollectibleInteractListener.handleInteraction()
    -> Validates cooldown (anti-spam)
    -> Acquires lock (race condition prevention)
    -> processCollection() creates physical item, gives to player
    -> Plays "collect-item" sound
    -> Sends "item-collected" message
```

**Key insight:** World collectibles give items to inventory; journal additions happen via `ConfirmAddGUI`. Two distinct flows with separate notification points.

### ConfirmAddGUI Flow (Where Progress Changes)

The actual progress changes happen in `ConfirmAddGUI.confirmAdd()`:

```java
// Line 166: Item added to journal
boolean added = playerDataManager.addItem(player.getUniqueId(), collectionId, itemId);

// Line 191-193: Sound played
String addSound = configManager.getSound("add-to-journal");
if (addSound != null) {
    player.playSound(player.getLocation(), addSound, 1.0f, 1.0f);
}

// Line 196-198: Message sent
player.sendMessage(configManager.getMessage("item-added-to-journal", ...));

// Line 201: Collection completion check
checkCollectionComplete();
```

`checkCollectionComplete()` handles:
```java
// Lines 226-252: Already plays completion sound and sends completion message
String completeSound = configManager.getSound("complete-collection");
player.playSound(player.getLocation(), completeSound, 1.0f, 1.0f);
player.sendMessage(configManager.getMessage("collection-complete", ...));
```

**Existing notifications already work for:**
- Item added to journal: chat message + sound
- Collection complete: chat message + sound

**Missing notifications (this phase adds):**
- Progress actionbar ("2/5 Forest Collection")
- Collection complete title/subtitle
- Configurable notification styles

### Adventure API Usage

The codebase uses Adventure API consistently via ConfigManager:

```java
// ConfigManager.java - MiniMessage parsing
public Component getMessage(String key, Object... placeholders) {
    String raw = messages.getOrDefault(key, "...");
    TagResolver.Builder resolver = TagResolver.builder();
    // ... placeholder handling
    return miniMessage.deserialize(raw, resolver.build());
}

// Example usage in ConfirmAddGUI
player.sendMessage(configManager.getMessage("item-added-to-journal",
    "item", collectionItem.name(),
    "collection", collection.name()));
```

**ActionBar example in ActionBarPromptTask:**
```java
Component message = Component.text("Right-click to collect", tier.getColor())
    .append(Component.text(" [", NamedTextColor.GRAY))
    .append(Component.text(tier.getDisplayName(), tier.getColor()))
    .append(Component.text("]", NamedTextColor.GRAY));

player.sendActionBar(message);
```

**Title API (not currently used but available):**
```java
// Paper Adventure API
player.showTitle(Title.title(
    Component.text("Collection Complete!", NamedTextColor.GOLD),
    Component.text("Forest Specimens", NamedTextColor.GREEN),
    Title.Times.times(
        Duration.ofMillis(500),   // fade in
        Duration.ofSeconds(3),    // stay
        Duration.ofMillis(500)    // fade out
    )
));
```

### ConfigManager Patterns

Configuration is cached on load and accessed via getters:

```java
// Cached values (lines 22-57)
private int collectionCooldownMs;
private boolean debugMode;
// ... many more

// Reload method refreshes cache
public void reload() {
    plugin.reloadConfig();
    FileConfiguration config = plugin.getConfig();
    collectionCooldownMs = config.getInt("settings.collection-cooldown-ms", 500);
    // ...
}

// Direct config access for uncached values
public String getString(String path, String defaultValue) {
    return plugin.getConfig().getString(path, defaultValue);
}
```

**Sound handling pattern:**
```java
private final Map<String, String> sounds;

// Load in reload()
sounds.clear();
if (config.isConfigurationSection("sounds")) {
    for (String key : config.getConfigurationSection("sounds").getKeys(false)) {
        String soundName = config.getString("sounds." + key);
        sounds.put(key, soundName);
    }
}

// Access via getter
public String getSound(String key) {
    return sounds.get(key);
}
```

## Implementation Approach

### Notification Hook Points

1. **Item Added to Journal** (progress notification):
   - Location: `ConfirmAddGUI.confirmAdd()` after `playerDataManager.addItem()`
   - Notification: Actionbar with progress "2/5 Forest Collection"
   - Should replace or supplement existing chat message based on config

2. **Collection Complete** (completion notification):
   - Location: `ConfirmAddGUI.checkCollectionComplete()`
   - Notification: Title/subtitle + sound (title is new, sound exists)
   - Enhance existing flow, don't duplicate

### Spam Prevention

The codebase already handles duplicate prevention:

```java
// ItemUseListener.java line 86
if (playerDataManager.hasItem(player.getUniqueId(), collectionId, itemId)) {
    player.sendMessage(configManager.getMessage("item-duplicate", "item", collectionItem.name()));
    // Don't consume - let them trade it
    return;
}
```

**No new spam prevention needed** - duplicates are blocked before GUI opens, and items can only be added once via `playerDataManager.addItem()` which returns false on duplicates.

### Configuration Structure

Proposed additions to `config.yml`:

```yaml
# Notifications section
notifications:
  # Progress notification when adding item to journal
  progress:
    # Style: actionbar, chat, title, or none
    style: actionbar
    # MiniMessage format with placeholders
    format: "<gold><current>/<total></gold> <gray>in</gray> <green><collection></green>"

  # Completion notification when finishing a collection
  completion:
    # Style: title, chat, or both
    style: title
    # Title line (MiniMessage)
    title: "<gold><bold>Collection Complete!</bold></gold>"
    # Subtitle line (MiniMessage)
    subtitle: "<green><collection></green>"
    # Duration in seconds
    fade-in: 0.5
    stay: 3.0
    fade-out: 0.5
```

Alternatively, add to existing `messages:` section:
```yaml
messages:
  # ... existing messages ...

  # Progress notification (supports <current>, <total>, <collection>)
  progress-notification: "<gold><current>/<total></gold> <gray>in</gray> <green><collection></green>"

  # Completion title (supports <collection>)
  completion-title: "<gold><bold>Collection Complete!</bold></gold>"
  completion-subtitle: "<green><collection></green>"

settings:
  # ... existing settings ...

  # Notification style: actionbar, chat, title, none
  progress-notification-style: actionbar
  # Completion style: title, chat, both
  completion-notification-style: title
```

## Technical Details

### Sound Constants

The codebase uses string-based sound names for version compatibility:

```yaml
sounds:
  collect-item: "entity.experience_orb.pickup"
  add-to-journal: "entity.player.levelup"
  complete-collection: "ui.toast.challenge_complete"
```

**Existing completion sound is excellent** (`ui.toast.challenge_complete`) - the achievement sound is perfect for collection completion.

Additional celebratory sounds available in 1.21:
- `entity.firework_rocket.blast` - firework burst
- `entity.firework_rocket.large_blast` - big celebration
- `ui.toast.challenge_complete` - achievement (already used)
- `entity.player.levelup` - level up chime

### MiniMessage Templates

Progress notification formats:

```
# Simple: "2/5 Forest Collection"
<gold><current>/<total></gold> <gray><collection></gray>

# With icon: "[*] 2/5 Forest Collection"
<yellow>[<gold>*</gold>]</yellow> <gold><current>/<total></gold> <gray><collection></gray>

# Progress bar style: "Forest [****----] 4/8"
<green><collection></green> <gray>[</gray><gold><progress_bar></gold><gray>]</gray> <gold><current>/<total></gold>
```

Completion title formats:

```
# Title
<gold><bold>Collection Complete!</bold></gold>

# Subtitle with collection name
<green><collection></green>

# Alternative with tier color
<tier_color><collection></tier_color>
```

### Progress Bar Component

The `GUIManager` already has a progress bar builder:

```java
public String createProgressBar(int current, int max, int barLength) {
    // Returns: "████░░░░░░ 4/10" in MiniMessage format
}

public Component createProgressBarComponent(int current, int max, int barLength) {
    // Returns Adventure Component
}
```

This can be reused for actionbar progress notifications if desired.

## Architecture Recommendation

### Option A: NotificationManager (Recommended)

Create a dedicated manager class:

```java
public class NotificationManager {
    private final ConfigManager configManager;

    public void sendProgressNotification(Player player, Collection collection,
                                         int current, int total) {
        NotificationStyle style = configManager.getProgressNotificationStyle();
        switch (style) {
            case ACTIONBAR -> sendActionBarProgress(player, collection, current, total);
            case CHAT -> sendChatProgress(player, collection, current, total);
            case TITLE -> sendTitleProgress(player, collection, current, total);
            case NONE -> {} // Silent
        }
    }

    public void sendCompletionNotification(Player player, Collection collection) {
        // Title + subtitle + sound
    }
}
```

**Pros:**
- Single responsibility
- Easy to add new notification types
- Testable in isolation
- Consistent with existing Manager pattern

### Option B: Extend ConfigManager

Add notification methods to ConfigManager:

```java
public void sendProgressNotification(Player player, String collection, int current, int total) {
    String style = getString("notifications.progress.style", "actionbar");
    // ...
}
```

**Cons:**
- ConfigManager already has 380+ lines
- Mixes config access with player interaction

**Recommendation: Option A** - Create `NotificationManager`

## Risks and Considerations

### Risk 1: ActionBar Conflicts with ActionBarPromptTask

The existing `ActionBarPromptTask` sends actionbar messages when players look at collectibles. If a player adds an item while looking at another collectible, the progress notification could be immediately overwritten.

**Mitigation:**
- ActionBarPromptTask runs every 5 ticks (0.25s)
- Progress notification is a one-time event
- Acceptable UX: player sees progress briefly, then returns to "Right-click to collect"
- Alternative: Add cooldown to ActionBarPromptTask after progress notification

### Risk 2: Title Conflicts

If completion title is shown while another plugin shows a title, they conflict.

**Mitigation:**
- Use configurable style (allow chat-only for servers with title-heavy plugins)
- Title API handles this gracefully (new title replaces old)

### Risk 3: Performance

Sending notifications involves:
- MiniMessage parsing (CPU)
- Network packet (trivial)

**Mitigation:**
- Messages are already parsed by ConfigManager
- Notifications only trigger on actual progress changes (not spam)
- No concerns at expected scale

## Code Examples

### Actionbar Progress Notification

```java
// In NotificationManager
public void sendProgressNotification(Player player, Collection collection,
                                     int current, int total) {
    String format = configManager.getRawMessage("progress-notification");
    Component message = configManager.parse(format,
        "current", String.valueOf(current),
        "total", String.valueOf(total),
        "collection", collection.name()
    );
    player.sendActionBar(message);
}
```

### Title Completion Notification

```java
// In NotificationManager
public void sendCompletionTitle(Player player, Collection collection) {
    Component title = configManager.getMessage("completion-title");
    Component subtitle = configManager.getMessage("completion-subtitle",
        "collection", collection.name()
    );

    Title.Times times = Title.Times.times(
        Duration.ofMillis(500),
        Duration.ofSeconds(3),
        Duration.ofMillis(500)
    );

    player.showTitle(Title.title(title, subtitle, times));
}
```

### Integration in ConfirmAddGUI

```java
// In confirmAdd() after line 166
boolean added = playerDataManager.addItem(player.getUniqueId(), collectionId, itemId);
if (added) {
    // Get current progress
    int current = progress.getCollectedCount(collection.id());
    int total = collection.getItemCount();

    // Send progress notification
    plugin.getNotificationManager().sendProgressNotification(
        player, collection, current, total);
}

// In checkCollectionComplete() after marking complete
if (collectedCount >= totalCount) {
    playerDataManager.markComplete(player.getUniqueId(), collection.id());

    // Enhanced completion notification
    plugin.getNotificationManager().sendCompletionNotification(player, collection);
}
```

## Open Questions

1. **Should progress notification replace or supplement the existing chat message?**
   - Current: Chat message "Added Golden Acorn to your journal!"
   - Option A: Keep chat, add actionbar (both)
   - Option B: Replace chat with actionbar (cleaner)
   - Recommendation: Make configurable, default to actionbar-only

2. **Should title timing be configurable?**
   - Could add config options for fade-in, stay, fade-out
   - Or use sensible defaults (0.5s, 3s, 0.5s)
   - Recommendation: Hardcode sensible defaults for v1, add config later if requested

3. **Per-collection notification customization?**
   - Some collections might want special sounds or messages
   - Collection YAML already has `rewards.sound` and `rewards.message`
   - Could extend to `notifications` block per-collection
   - Recommendation: Out of scope for v1, use global settings

## Sources

### Primary (HIGH confidence)
- Codebase analysis: `ItemUseListener.java`, `ConfirmAddGUI.java`, `CollectibleInteractListener.java`
- Codebase analysis: `ConfigManager.java`, `ActionBarPromptTask.java`
- Codebase analysis: `GUIManager.java`, `PlayerDataManager.java`
- Paper API: Adventure API title/actionbar methods

### Secondary (MEDIUM confidence)
- Existing config.yml patterns and messages section

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Adventure API already in use, patterns established
- Architecture: HIGH - Clear integration points identified in existing code
- Pitfalls: HIGH - Analyzed potential conflicts with ActionBarPromptTask

**Research date:** 2026-01-22
**Valid until:** 60 days (stable domain, unlikely to change)
