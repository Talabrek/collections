# Features Research: v1.1 Operational Features

**Domain:** Minecraft collectibles plugin operational features
**Researched:** 2026-01-22
**Plugin Context:** Paper 1.21.4 collectibles plugin with existing player progress tracking, SQLite/MySQL storage, and Adventure API integration

## Summary

Operational features for Minecraft plugins follow well-established patterns. Data export/import typically uses JSON or YAML formats with UUID-keyed player data for portability across servers. Progress notifications universally use Adventure API's actionbar, title/subtitle, and boss bar components. Admin commands follow Brigadier patterns with offline player support via UUID lookup. Metrics collection centers on bStats for community statistics with optional Prometheus/Grafana for server operators wanting detailed observability. The existing plugin architecture (PlayerDataManager, Storage interface, async CompletableFuture patterns) provides strong foundations for all four feature areas.

---

## Export/Import

Data portability is critical for multi-server networks. The existing `Storage` interface and `PlayerProgress` model provide the foundation.

### Table Stakes

- **JSON export format** - LOW complexity - Human-readable, portable, widely supported. Most plugins use JSON over YAML for data interchange because it's stricter (no YAML quirks) and parseable by external tools.

- **UUID-keyed player data** - LOW complexity - Already implemented. UUIDs are permanent identifiers that survive username changes. Essential for server transfers.

- **Per-player export command** - LOW complexity - `/collections export <player>` - Export single player's data. Operators need surgical exports, not just bulk.

- **Bulk export command** - MEDIUM complexity - `/collections export all` - Export all player data. Necessary for server migrations.

- **Import with merge strategy** - MEDIUM complexity - Import should handle existing data: skip duplicates, merge new items, report conflicts. Players shouldn't lose progress on import.

- **File-based import/export** - LOW complexity - Write to/read from plugin data folder. Standard location: `plugins/Collections/exports/`.

### Differentiators

- **Selective export by collection** - LOW complexity - Export only specific collections. Useful when splitting collections across servers (biome-specific collections stay with biome servers).

- **Date-range export** - MEDIUM complexity - Export only progress after a certain date. Useful for incremental backups and syncing recent activity.

- **Direct database transfer** - MEDIUM complexity - `/collections transfer <from-db> <to-db>` - Direct database-to-database migration without intermediate files. Faster for large datasets.

- **Export format versioning** - LOW complexity - Include format version in export files. Enables future format changes without breaking old exports.

- **Validation on import** - MEDIUM complexity - Verify collection/item IDs exist before importing. Prevent orphaned data when collection definitions change.

### Anti-Features

- **Real-time sync between servers** - Avoid building custom cross-server sync. Use dedicated sync plugins (MySQL with shared database, or proxy-level solutions like Redis). Cross-server sync is a massive undertaking with race conditions, network partitions, and conflict resolution. The existing MySQL storage with shared database handles this better.

- **Binary export formats** - Avoid proprietary binary formats. JSON/YAML are debuggable and portable. Binary saves minimal space but creates vendor lock-in.

- **Automatic scheduled exports** - Avoid automatic file exports on schedule. Database backups are the operator's responsibility; plugin-level exports clutter disk and create maintenance burden.

### Dependencies on Existing Features

- `Storage` interface - Add `exportPlayerData(UUID)` and `importPlayerData(PlayerProgress)` methods
- `PlayerProgress` model - Already serializable, add JSON serialization methods
- `PlayerDataManager` - Coordinate cache invalidation on import

---

## Progress Notifications

Notification design directly impacts player engagement. The plugin already has `ActionBarPromptTask` for collectible interaction prompts.

### Table Stakes

- **Collection progress on item pickup** - LOW complexity - "Forest Specimens: 3/8 collected" in actionbar. Players need immediate feedback when progress happens.

- **Collection completion celebration** - MEDIUM complexity - Title + subtitle + sound on completing a collection. Major achievement deserves prominent notification.

- **Configurable notification types** - LOW complexity - Config options: `notifications.progress: actionbar`, `notifications.completion: title`. Different servers have different preferences.

- **Disable option per notification type** - LOW complexity - `notifications.enabled.progress: true`. Some servers want minimal UI clutter.

- **Sound effects** - LOW complexity - Play sounds on progress and completion. Adventure API supports sound via `player.playSound()`.

### Differentiators

- **Near-completion alerts** - LOW complexity - "Only 1 more item for Forest Specimens!" when 1 item remains. Creates urgency and excitement.

- **First-time collection bonus notification** - LOW complexity - Extra celebration for first collection ever completed. Memorable onboarding moment.

- **Boss bar for active hunting** - MEDIUM complexity - Show current collection progress as boss bar while hunting. Optional toggle via `/collections track <collection>`. Persistent visual without spamming actionbar.

- **Streak notifications** - LOW complexity - "3 items in a row!" when collecting multiple items quickly. Gamification element.

- **Milestone notifications** - MEDIUM complexity - "You've collected 100 items total!" for overall milestones. Long-term engagement.

- **Rarity callout** - LOW complexity - Different notification style for rare vs common items. "[RARE] Ancient Coin collected!" with special formatting.

### Anti-Features

- **Spam on every pickup** - Never show notifications for items player already has. Only notify on NEW discoveries.

- **Blocking/modal notifications** - Avoid anything that interrupts gameplay (inventory GUIs, forced delays). Notifications should be ambient, not disruptive.

- **Cross-server broadcast** - Avoid broadcasting achievements to other servers. That's proxy-level functionality. Keep plugin scope to single server.

- **Persistent notification storage** - Avoid queuing notifications for offline players. If player misses it, it's gone. Offline notification systems add complexity without value.

### Dependencies on Existing Features

- `ActionBarPromptTask` - Extend pattern for progress notifications
- `CollectibleInteractListener` - Trigger point for progress notifications
- `RewardManager` - Trigger point for completion notifications
- `ConfigManager` - Add notification configuration options

---

## Admin Commands

The existing `CollectionsCommand` class provides comprehensive admin commands. Focus on gaps.

### Table Stakes

- **Force-complete collection** - LOW complexity - Already exists: `/collections complete <player> <collection>`. Mark collection complete and add all items.

- **Force-add single item** - LOW complexity - Already exists: `/collections give progress <player> <collection> <item>`. Add item to journal without physical item.

- **Reset player progress** - LOW complexity - Already exists: `/collections reset <player> [collection]`. Clear progress entirely.

- **View player progress** - LOW complexity - `/collections inspect <player>` - View another player's stats. Admins need visibility into player state for support tickets.

- **Offline player support** - MEDIUM complexity - Commands should work with offline players by UUID or cached username. Critical for support scenarios.

### Differentiators

- **Batch operations** - MEDIUM complexity - `/collections complete @a <collection>` - Complete for all online players. Server events or testing scenarios.

- **Undo/rollback** - MEDIUM complexity - `/collections undo <player>` - Revert last admin action. Safety net for mistakes. Store last action in memory.

- **Audit logging** - LOW complexity - Log all admin commands to file with timestamp, admin, target, action. Required for server accountability.

- **Confirmation for destructive actions** - LOW complexity - `/collections reset <player> --confirm` - Require explicit confirmation. Prevent accidents.

- **Preview mode** - MEDIUM complexity - `/collections complete <player> <collection> --dry-run` - Show what would happen without doing it.

### Anti-Features

- **Direct database manipulation commands** - Avoid `/collections sql <query>`. Direct SQL access bypasses validation and cache coherence. All data changes should go through the proper API.

- **Automatic player detection by name** - Avoid fuzzy name matching. Use exact names or UUIDs. Fuzzy matching leads to acting on wrong player.

- **Bulk destructive operations without safeguards** - Avoid `/collections reset-all-players`. Too dangerous. Require explicit confirmation file or multi-step process.

### Dependencies on Existing Features

- `CollectionsCommand` - Extend existing command structure
- `PlayerDataManager` - Add `loadOfflinePlayer(UUID)` for offline operations
- `Storage` interface - Already has async methods for offline data access

---

## Metrics/Observability

Two audiences: plugin developer (bStats for aggregate usage) and server operator (Prometheus/custom metrics for operational monitoring).

### Table Stakes

- **bStats integration** - LOW complexity - Standard for Minecraft plugins. Reports server count, player count, MC version, Java version. Create plugin page at bstats.org, add Metrics class.

- **bStats custom charts** - LOW complexity - Report plugin-specific metrics: total collections defined, average completion rate, most popular collections. Helps understand usage patterns.

- **Debug logging toggle** - LOW complexity - Already exists: `/collections debug`. Toggle verbose logging for troubleshooting.

- **Performance timing logs** - LOW complexity - Log slow operations (>100ms) at warning level. Database queries, spawn attempts, GUI renders.

### Differentiators

- **Prometheus exporter** - MEDIUM complexity - Expose metrics endpoint for Prometheus scraping. Server operators with existing monitoring infrastructure expect this.

- **Custom metrics API** - MEDIUM complexity - Allow other plugins to query metrics: `CollectionsAPI.getMetrics().getTotalCollected()`. Useful for scoreboards, placeholders.

- **PlaceholderAPI integration** - LOW complexity - `%collections_total%`, `%collections_completed%`, `%collections_progress_<id>%`. Widely used for scoreboards and chat.

- **In-game metrics dashboard** - MEDIUM complexity - `/collections metrics` - Show server-wide statistics. Total collected items, completion rates, active players.

- **Per-player analytics** - LOW complexity - Track time-to-completion, collection order, session activity. Useful for balancing collections.

### Anti-Features

- **Automatic metric uploads** - Only bStats should phone home. Never upload player-identifiable data. Respect privacy expectations.

- **Heavy metric collection** - Avoid collecting metrics that require database queries on every action. Cache statistics, update periodically.

- **Required metrics** - Metrics should be optional. Some servers disable all telemetry. Respect `bStats.enabled: false` and similar config.

- **Real-time dashboard in plugin** - Avoid building a web dashboard into the plugin. Use existing tools (Grafana) with metrics export.

### Dependencies on Existing Features

- `Storage` interface - Already has `getTotalCollectiblesCollected()` and `getTotalCollectionsCompleted()` methods
- `ConfigManager` - Add metrics configuration section
- Main plugin class - Initialize Metrics in `onEnable()`

---

## Feature Priority Matrix

| Feature | Category | Complexity | Priority | Phase Recommendation |
|---------|----------|------------|----------|---------------------|
| JSON export/import | Export | MEDIUM | HIGH | Phase 1 |
| Progress actionbar | Notify | LOW | HIGH | Phase 1 |
| Completion celebration | Notify | MEDIUM | HIGH | Phase 1 |
| Admin inspect command | Admin | LOW | HIGH | Phase 1 |
| bStats integration | Metrics | LOW | HIGH | Phase 1 |
| Offline player commands | Admin | MEDIUM | MEDIUM | Phase 1 |
| Configurable notifications | Notify | LOW | MEDIUM | Phase 1 |
| Audit logging | Admin | LOW | MEDIUM | Phase 2 |
| Boss bar tracking | Notify | MEDIUM | LOW | Phase 2 |
| PlaceholderAPI | Metrics | LOW | MEDIUM | Phase 2 |
| Prometheus exporter | Metrics | MEDIUM | LOW | Phase 2 |

---

## Confidence

**HIGH** - Research draws from:
- Direct examination of existing plugin codebase (PlayerProgress, Storage, CollectionsCommand, ActionBarPromptTask)
- Established Minecraft plugin patterns (bStats, Adventure API, Brigadier)
- PaperMC official documentation for notification APIs
- bStats official documentation for custom charts
- Community consensus on data portability (JSON with UUID keys)

The existing plugin architecture strongly supports all proposed features. No fundamental changes needed - all features extend existing patterns.

---

## Sources

- [PaperMC Plugin Configurations](https://docs.papermc.io/paper/dev/plugin-configurations/) - Official guidance on YAML vs JSON
- [PaperMC Boss Bars](https://docs.papermc.io/adventure/bossbar/) - Adventure API boss bar documentation
- [bStats Include Metrics](https://bstats.org/getting-started/include-metrics) - bStats integration guide
- [bStats Custom Charts](https://bstats.org/docs/custom-charts) - Custom chart documentation
- [Advanced Achievements Plugin](https://modrinth.com/plugin/advanced-achievements-updated) - Reference implementation for notification patterns
- [UnifiedMetrics](https://github.com/Cubxity/UnifiedMetrics) - Prometheus metrics for Minecraft reference
- [PlayerTransfer Mod](https://www.curseforge.com/minecraft/mc-mods/playertransfer) - Data migration patterns
- [VaultIO SpigotMC](https://www.spigotmc.org/threads/vaultio-help-you-quick-and-simply-export-import-player-economy-data-or-switch-economy-plugin.370193/) - Economy data export patterns
