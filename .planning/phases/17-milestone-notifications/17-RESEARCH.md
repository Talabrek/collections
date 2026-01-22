# Phase 17: Milestone Notifications - Research

**Researched:** 2026-01-23
**Domain:** Paper Plugin notification systems, player progress tracking, celebratory effects
**Confidence:** HIGH

## Summary

Phase 17 implements celebratory notifications at 25%, 50%, and 75% collection progress milestones. Research of the existing codebase reveals a well-established notification infrastructure (NotificationManager) that already handles progress and completion notifications. The key challenge is tracking which milestones have been triggered (persistence) and ensuring notifications fire exactly once per milestone.

The codebase already demonstrates all necessary APIs: Title API for prominent notifications, particle effects via ParticleBuilder, firework effects in RewardManager, and sound playback. The milestone state must be persisted to prevent re-triggering across sessions.

**Primary recommendation:** Extend PlayerProgress.CollectionProgress to track triggered milestones (as a byte bitmask), add milestone notification methods to NotificationManager, and hook into the existing confirmAdd flow in ConfirmAddGUI.

## Standard Stack

The implementation uses APIs and patterns already present in the codebase:

### Core (Already In Project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Adventure API | Bundled with Paper | Text, titles, sounds | Already used throughout codebase |
| Paper ParticleBuilder | Bundled with Paper | Efficient particle effects | Already used in ParticleTask |
| MiniMessage | Bundled with Paper | Text formatting | Already used via ConfigManager |

### APIs to Use
| API | Method | Purpose | Already Used In |
|-----|--------|---------|-----------------|
| Title API | `player.showTitle(Title.title(...))` | Prominent milestone display | NotificationManager.sendCompletionTitle() |
| Sound API | `player.playSound(location, sound, volume, pitch)` | Celebratory audio | RewardManager, GUIManager |
| Particle API | `Particle.builder()...spawn()` | Visual celebration | ParticleTask.spawnParticleForPlayer() |
| Firework API | `world.spawn(loc, Firework.class, ...)` | Major milestone celebration | RewardManager.spawnFireworks() |

### No Additional Dependencies Required
All necessary APIs are provided by Paper and Adventure (bundled). No new libraries needed.

## Architecture Patterns

### Recommended Project Structure

Minimal changes to existing structure:

```
src/main/java/com/blockworlds/collections/
├── manager/
│   └── NotificationManager.java  # EXTEND with milestone methods
├── model/
│   └── PlayerProgress.java       # EXTEND CollectionProgress with milestone tracking
├── storage/
│   └── SQLiteStorage.java        # ADD milestone column to collection_progress table
│   └── MySQLStorage.java         # ADD milestone column (same schema)
└── gui/
    └── ConfirmAddGUI.java        # ADD milestone check call after adding item
```

### Pattern 1: Milestone Detection in ConfirmAddGUI

**What:** Check for milestone thresholds after adding an item, before completion check.
**When to use:** Every successful item addition.
**Hook point:** `ConfirmAddGUI.confirmAdd()` at line 184-190 (after addItem, before checkCollectionComplete).

```java
// After line 190 (sendProgressNotification)
// Add milestone check here:
int percentComplete = (currentCount * 100) / totalCount;
notificationManager.checkMilestoneNotifications(player, collection, currentCount, totalCount, percentComplete);
```

### Pattern 2: Milestone State as Bitmask

**What:** Store triggered milestones as a byte where bit positions represent milestones (25%=1, 50%=2, 75%=4).
**When to use:** Efficient storage, simple bitwise checks.

```java
// In PlayerProgress.CollectionProgress
private byte triggeredMilestones; // Bits: 0=25%, 1=50%, 2=75%

public boolean hasMilestone(int percent) {
    int bit = switch(percent) {
        case 25 -> 0;
        case 50 -> 1;
        case 75 -> 2;
        default -> -1;
    };
    return bit >= 0 && (triggeredMilestones & (1 << bit)) != 0;
}

public void setMilestone(int percent) {
    int bit = switch(percent) {
        case 25 -> 0;
        case 50 -> 1;
        case 75 -> 2;
        default -> -1;
    };
    if (bit >= 0) {
        triggeredMilestones |= (1 << bit);
    }
}
```

### Pattern 3: Notification Escalation by Milestone

**What:** Different celebration intensity based on milestone percentage.
**When to use:** 25% = subtle (actionbar + sound), 50% = moderate (title + particles), 75% = prominent (title + particles + optional firework).

```java
// In NotificationManager
public void sendMilestoneNotification(Player player, Collection collection, int milestone) {
    switch (milestone) {
        case 25 -> sendSubtleMilestone(player, collection, 25);    // Actionbar + sound
        case 50 -> sendModerateMilestone(player, collection, 50);  // Subtitle + particles
        case 75 -> sendProminentMilestone(player, collection, 75); // Full title + particles
    }
}
```

### Anti-Patterns to Avoid

- **Checking milestones on every progress query:** Only check after successful item add, not on GUI open.
- **Storing milestone state only in memory:** Must persist to database for cross-session consistency.
- **Triggering notification before persistence:** Always persist the milestone FIRST, then show notification (prevents double-trigger on disconnect).
- **Blocking on milestone persistence:** Use async save like existing item persistence pattern.

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Title display | Manual packet construction | `player.showTitle(Title.title(...))` | Adventure API handles timing, fading |
| Particle effects | Manual packet sending | `Particle.builder()...receivers(player).spawn()` | ParticleBuilder handles client targeting |
| Configurable messages | Hardcoded strings | ConfigManager.getMessage() with placeholders | Consistency with existing patterns |
| Sound playback | Manual sound packets | `player.playSound()` | Handles sound registry, volume |

**Key insight:** The codebase already has working implementations of all celebratory effects. RewardManager.spawnFireworks() and ParticleTask.spawnCollectionEffect() can be referenced or reused.

## Common Pitfalls

### Pitfall 1: Milestone Triggers Multiple Times

**What goes wrong:** Player at 24% adds item reaching 26%, milestone fires. They quit. Next session they add another item (now 30%), 25% milestone fires again.
**Why it happens:** Milestone state not persisted to database.
**How to avoid:** Persist milestone bitmask to collection_progress table, load on player join.
**Warning signs:** Testing shows milestone notification firing twice for same player.

### Pitfall 2: Race Condition on Milestone Check

**What goes wrong:** Progress percentage calculated, context switch, another item added, milestone check uses stale percentage.
**Why it happens:** Async operations interleaved with synchronous milestone check.
**How to avoid:** Calculate percentage and check milestones atomically in ConfirmAddGUI.confirmAdd() which runs on main thread.
**Warning signs:** Skipped milestones (player goes from 20% to 55%, never sees 25% notification).

### Pitfall 3: Completion Notification Overlaps 75% Milestone

**What goes wrong:** Player at 75% adds final item, both 75% milestone AND completion notification fire.
**Why it happens:** 75% threshold met simultaneously with 100%.
**How to avoid:** Skip milestone check if this item completes the collection (currentCount == totalCount).
**Warning signs:** Two overlapping titles when completing a collection.

### Pitfall 4: Notification During GUI Transition

**What goes wrong:** Title notification displays while GUI is closing/opening, gets cut off or looks jarring.
**Why it happens:** GUI close triggers title, then opens CollectionDetailGUI.
**How to avoid:** ConfirmAddGUI already closes inventory before effects (line 148). Ensure milestone notification plays after close, before any GUI reopen.
**Warning signs:** Title appears briefly then vanishes when player is navigating GUIs.

## Code Examples

Verified patterns from the existing codebase:

### Title Display (from NotificationManager)
```java
// Source: NotificationManager.java lines 49-57
player.showTitle(Title.title(
    Component.text("25% Complete!", NamedTextColor.GREEN),
    Component.text(collection.name(), NamedTextColor.GOLD),
    Title.Times.times(
        Duration.ofMillis(250),  // fade in
        Duration.ofSeconds(2),   // stay
        Duration.ofMillis(250)   // fade out
    )
));
```

### Sound Playback (from ConfigManager)
```java
// Source: ConfirmAddGUI.java lines 208-211
String milestoneSound = configManager.getSound("milestone-reached");
if (milestoneSound != null) {
    player.playSound(player.getLocation(), milestoneSound, 1.0f, 1.0f);
}
```

### Particle Burst (from ParticleTask)
```java
// Source: ParticleTask.java lines 174-186
public void spawnMilestoneEffect(Player player) {
    Location location = player.getLocation().add(0, 1, 0);
    Particle.HAPPY_VILLAGER.builder()
            .location(location)
            .count(20)
            .offset(0.5, 0.5, 0.5)
            .extra(0.1)
            .receivers(player)
            .spawn();
}
```

### Firework Celebration (from RewardManager)
```java
// Source: RewardManager.java lines 158-186
// Can be adapted for 75% milestone
private void spawnMilestoneFirework(Player player) {
    Location loc = player.getLocation().add(0, 1, 0);
    Firework firework = player.getWorld().spawn(loc, Firework.class, fw -> {
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(Color.LIME, Color.AQUA)
                .with(FireworkEffect.Type.BALL)
                .trail(true)
                .build();
        meta.addEffect(effect);
        meta.setPower(0); // Instant detonation
        fw.setFireworkMeta(meta);
    });
    firework.getScheduler().runDelayed(plugin, task -> firework.detonate(), null, 2L);
}
```

### Database Schema Extension
```sql
-- Add to collection_progress table
ALTER TABLE collection_progress ADD COLUMN milestones TINYINT DEFAULT 0;
-- milestones is a bitmask: bit 0 = 25%, bit 1 = 50%, bit 2 = 75%
```

### Progress Percentage Calculation
```java
// Source: ConfirmAddGUI.java lines 185-190
PlayerProgress progress = playerDataManager.getProgressBlocking(player.getUniqueId());
int currentCount = progress != null ? progress.getCollectedCount(collectionId) : 1;
int totalCount = collection.getItemCount();
// Percentage calculation:
int percentComplete = (currentCount * 100) / totalCount;
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ChatColor codes | MiniMessage/Adventure | Paper 1.16+ | All text uses MiniMessage |
| Packet-based particles | ParticleBuilder API | Paper 1.17+ | Cleaner, efficient particle targeting |
| Sync database calls | CompletableFuture async | Current codebase | Non-blocking persistence |

**Deprecated/outdated:**
- ChatColor/legacy formatting: Use MiniMessage exclusively
- BukkitRunnable: Use Paper schedulers (GlobalRegionScheduler, EntityScheduler)

## Configuration Design

Recommended config.yml additions:

```yaml
notifications:
  # ... existing progress and completion ...

  # Milestone notifications (25%, 50%, 75%)
  milestones:
    # Enable/disable milestone notifications
    enabled: true

    # 25% milestone - subtle celebration
    quarter:
      style: actionbar
      format: "<green>25% Complete!</green> <gray>- <collection></gray>"
      sound: "entity.experience_orb.pickup"
      particles: true

    # 50% milestone - moderate celebration
    half:
      style: subtitle
      title: "<gold>Halfway There!</gold>"
      subtitle: "<green><collection></green>"
      sound: "entity.player.levelup"
      particles: true

    # 75% milestone - prominent celebration
    threequarter:
      style: title
      title: "<gold>75% Complete!</gold>"
      subtitle: "<green><collection></green> - Almost done!"
      sound: "ui.toast.challenge_complete"
      particles: true
      firework: false  # Optional firework for 75%
```

## Open Questions

Things that couldn't be fully resolved:

1. **Should milestones track by collection tier?**
   - What we know: Tier affects particle type/color (CollectibleTier enum)
   - What's unclear: Should higher tier collections have more elaborate milestone celebrations?
   - Recommendation: Start with uniform milestones; tier-based enhancement can be Phase 17b

2. **Firework at 75% milestone?**
   - What we know: RewardManager already spawns fireworks for completion
   - What's unclear: Would 75% firework diminish completion celebration?
   - Recommendation: Make it configurable (default: false), let server admins decide

3. **Meta-collection milestones?**
   - What we know: Collections can require other collections (requiredCollections field)
   - What's unclear: Do meta-collections track individual item milestones or sub-collection milestones?
   - Recommendation: Treat meta-collections as normal collections for milestone purposes (count required collections as items)

## Sources

### Primary (HIGH confidence)
- `ConfirmAddGUI.java` - Actual add flow implementation, hook point identified
- `NotificationManager.java` - Existing notification patterns, Title API usage
- `PlayerProgress.java` - Progress tracking model, CollectionProgress inner class
- `SQLiteStorage.java` - Database schema, persistence patterns
- `RewardManager.java` - Firework and celebration effect patterns
- `ParticleTask.java` - ParticleBuilder patterns
- `ConfigManager.java` - Configuration structure, sound/message retrieval

### Secondary (MEDIUM confidence)
- `config.yml` - Existing notification configuration structure
- `RadarManager.java` - BossBar patterns (alternative notification style)

### Tertiary (LOW confidence)
- None - all research based on codebase analysis

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All APIs already used in codebase
- Architecture: HIGH - Follows existing patterns exactly
- Pitfalls: HIGH - Based on actual code flow analysis
- Configuration: MEDIUM - Proposed structure, needs validation during implementation

**Research date:** 2026-01-23
**Valid until:** Indefinite (codebase-specific research)
