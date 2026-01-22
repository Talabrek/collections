# Research Summary: v1.1 Operational Features

**Synthesized:** 2026-01-22
**Research Files:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Overall Confidence:** HIGH

---

## Executive Summary

The v1.1 operational features (data export/import, progress notifications, admin force-complete, and metrics collection) integrate cleanly into the existing Collections plugin architecture with minimal new dependencies. The plugin's well-structured manager-based service layer with async CompletableFuture patterns provides natural extension points for all four feature areas. Only one new dependency is required: bStats for metrics collection (3.1.0, must be shaded and relocated). Gson for JSON export is already bundled with Paper servers, requiring only a `compileOnly` reference for IDE support.

The highest-risk feature is data export/import, which introduces potential memory exhaustion on large datasets, cache coherency issues on import, and multi-server synchronization challenges. These risks are well-understood and have established mitigation patterns: streaming exports with cursor-based fetching, strict validation on import, and cache invalidation coordination with PlayerDataManager. The existing Storage interface pattern (proven with SQLite/MySQL) extends naturally for export/import operations.

Progress notifications and admin commands are low-risk additions that leverage existing infrastructure. The ActionBarPromptTask pattern extends for progress notifications, though care must be taken to avoid spam during batch operations (admin `/complete` commands) and conflicts with the existing action bar usage. The admin force-complete command already exists at 90% completion - only `markComplete()` call, reward flag, and offline player support are needed. bStats integration follows a standard pattern used by 45,000+ Minecraft plugins.

---

## Key Findings

### Stack Additions

**Required:**
- **bStats 3.1.0** (`org.bstats:bstats-bukkit:3.1.0`) - Industry standard for plugin metrics. Must shade and relocate to `com.blockworlds.collections.lib.bstats`.

**Already Available (no new deps):**
- **Gson** - Bundled with Paper; use `compileOnly` for IDE support only
- **Adventure API** - ActionBar, Title, BossBar for notifications
- **Brigadier** - Existing command framework for admin commands
- **SLF4J logging** - Via `plugin.getLogger()` for operational audit trail

**Not Recommended:** Jackson (overkill), UnifiedMetrics/Prometheus (premature complexity), custom metrics server.

### Feature Landscape

**Table Stakes (must have for v1.1):**
| Feature | Complexity | Notes |
|---------|------------|-------|
| JSON export/import | MEDIUM | UUID-keyed, versioned format |
| Progress actionbar | LOW | "Forest Specimens: 3/8 collected" |
| Completion celebration | MEDIUM | Title + sound on collection complete |
| Admin inspect command | LOW | `/collections inspect <player>` |
| bStats integration | LOW | Standard charts + custom metrics |
| Offline player commands | MEDIUM | UUID lookup for offline targets |
| Configurable notifications | LOW | Enable/disable per type |

**Differentiators (nice to have):**
- Boss bar tracking (`/collections track <collection>`)
- Near-completion alerts ("Only 1 more!")
- PlaceholderAPI integration
- Audit logging for admin commands
- Undo/rollback for admin operations

**Anti-Features (explicitly avoid):**
- Real-time cross-server sync (use shared MySQL instead)
- Automatic scheduled exports (operator responsibility)
- Direct SQL manipulation commands
- Blocking/modal notifications
- High-cardinality metrics (per-player tags)

### Architecture Integration

**New Components:**
| Component | Layer | Purpose |
|-----------|-------|---------|
| NotificationManager | manager/ | Progress notifications, rate limiting |
| MetricsManager | manager/ | Counter aggregation, bStats integration |
| DataMigrationManager | manager/ | Export/import orchestration, validation |
| ExportFormat | model/ | JSON portable data transfer object |

**Modified Components:**
- `Storage` interface - Add `exportPlayer()`, `exportAll()`, `importPlayer()` signatures
- `SQLiteStorage` / `MySQLStorage` - Implement export/import with streaming
- `CollectionsCommand` - Add export, import, metrics subcommands; fix complete to call `markComplete()`
- `ConfigManager` - Add notification message templates and toggles
- `ItemUseListener` - Hook for progress notification after journal add

**Data Flow (Export):**
1. Admin runs `/collections export [player]`
2. CollectionsCommand delegates to DataMigrationManager
3. DataMigrationManager calls Storage.exportPlayer() with streaming cursor
4. Storage queries database, streams PlayerProgress records (not full load)
5. DataMigrationManager serializes incrementally to JSON file
6. Success message with item count sent to admin

**Data Flow (Notification):**
1. Player adds item to journal (ItemUseListener)
2. PlayerDataManager.addItem() returns true (newly added)
3. NotificationManager.sendProgress(player, collection, current, total)
4. Displays via configured style (actionbar default)

### Critical Pitfalls

**1. Export Memory Exhaustion (HIGH)**
- **Problem:** Loading all player data into memory causes OOM on large servers
- **Prevention:** Streaming export with JDBC cursor (`setFetchSize(100)`), incremental JSON writing, chunked export option

**2. Notification Spam During Batch Operations (HIGH)**
- **Problem:** Admin `/collections complete` triggers 20+ notifications per item
- **Prevention:** Add `suppressNotifications` parameter to `addItem()`, batch operations send single summary, rate-limit to 1 per 500ms

**3. Cache Coherency on Import (HIGH)**
- **Problem:** Import writes to database but PlayerDataManager cache is stale, overwrites imported data on next save
- **Prevention:** Check `isLoaded(uuid)` before import, invalidate cache for affected players, refuse import for online players or require kick

**4. High Cardinality Metrics (HIGH)**
- **Problem:** Tagging metrics with player UUID or collection ID creates millions of time series
- **Prevention:** NEVER tag by player UUID; use bounded tags only (collection_tier, operation_type); max 1000 label combinations

**5. Force-Complete Without Confirmation (HIGH)**
- **Problem:** Admin typo affects wrong player, no undo available
- **Prevention:** Confirmation step for destructive operations, audit logging, implement `/collections undo`

---

## Recommended Phase Structure

Based on dependency analysis, risk assessment, and complexity, the recommended implementation order is:

### Phase 1: Notifications (Low Risk, High Visibility)
**Rationale:** Immediate UX improvement, touches minimal existing code, can be disabled via config. Sets pattern for other features.

**Deliverables:**
- NotificationManager with rate limiting
- Progress actionbar on item collection
- Completion celebration (title + sound)
- Config toggles for all notification types
- Near-completion alerts

**Pitfalls to Avoid:**
- Spam during batch operations (add `suppressNotifications`)
- Action bar conflicts with ActionBarPromptTask (priority system)
- Progress count desync (pass counts as parameters, don't re-query)

**Research Needed:** None - well-documented Adventure API patterns

### Phase 2: Admin Command Enhancements (Low Risk, Quick Wins)
**Rationale:** Already 90% implemented. High value for server operators.

**Deliverables:**
- Fix `/collections complete` to call `markComplete()`
- Add `--with-rewards` flag
- Add `/collections inspect <player>`
- Offline player UUID resolution
- Audit logging for all admin operations
- Confirmation for destructive actions

**Pitfalls to Avoid:**
- Force-complete without confirmation
- Offline player UUID resolution failures (Mojang API with cache)
- Concurrent admin operations on same player (operation locking)

**Research Needed:** None - existing command patterns

### Phase 3: Metrics Collection (Low Risk, Foundation for Observability)
**Rationale:** Needed before external integrations. Provides operator visibility.

**Deliverables:**
- MetricsManager with atomic counters
- bStats integration with custom charts
- `/collections metrics` command
- Performance timing logs for slow operations

**Pitfalls to Avoid:**
- High cardinality tags (bounded labels only)
- Metrics overhead in hot path (lock-free counters)
- Metrics not reset on reload (use gauges for current-state)

**Research Needed:** None - bStats has extensive documentation

### Phase 4: Export/Import (Medium Risk, Most Complex)
**Rationale:** Requires careful format design, validation, and error handling. Save for last to allow format review time.

**Deliverables:**
- ExportFormat JSON schema with versioning
- DataMigrationManager with streaming
- `/collections export [player]` and `/collections export all`
- `/collections import <file>` with validation
- Conflict resolution (skip duplicates, merge new)

**Pitfalls to Avoid:**
- Memory exhaustion (streaming with cursors)
- Import validation bypass (strict field validation)
- Import race with active players (check isLoaded, invalidate cache)
- Multi-server export inconsistency (flush pending saves first)

**Research Needed:** Streaming JSON patterns may benefit from validation

---

## Risk Areas

| Area | Risk Level | Mitigation |
|------|------------|------------|
| Export memory on large datasets | HIGH | Streaming with JDBC cursors, chunked export |
| Import cache coherency | HIGH | Cache invalidation, refuse online player import |
| Notification spam | HIGH | Rate limiting, suppressNotifications param |
| Metrics cardinality | HIGH | Bounded label policy, pre-register metrics |
| Admin operation safety | MEDIUM | Confirmation steps, audit logging, undo |
| Multi-server sync | MEDIUM | Flush saves before export, document limitations |
| Action bar conflicts | MEDIUM | Priority system for multiple action bar users |

---

## Open Questions

1. **Export format specification:** Should format support incremental/differential exports for large datasets, or full export only?

2. **Offline player notification:** Should notifications queue for delivery on next login, or simply not notify offline players?

3. **bStats plugin ID:** Need to register plugin at bstats.org to obtain plugin ID before metrics can ship.

4. **Undo scope:** Should `/collections undo` support multiple levels of undo, or just last operation? Memory cost vs utility tradeoff.

5. **Multi-server import coordination:** For MySQL multi-server deployments, should import broadcast cache invalidation via plugin messaging channel?

---

## Confidence Assessment

| Area | Confidence | Rationale |
|------|------------|-----------|
| Stack | HIGH | Verified bundled libraries, bStats version confirmed on Maven Central |
| Features | HIGH | Based on existing codebase patterns, established plugin conventions |
| Architecture | HIGH | Direct codebase analysis, clear extension points identified |
| Pitfalls | HIGH | Verified against community patterns, existing async flows analyzed |
| Integration | HIGH | All hook points exist, no fundamental architecture changes needed |

**Gaps:**
- Prometheus exporter patterns for Minecraft are less documented (deferred to v1.2)
- Multi-server cache invalidation via plugin messaging not fully specified

---

## Roadmap Implications Summary

| Phase | Effort | Risk | Research Flag |
|-------|--------|------|---------------|
| 1. Notifications | 2-3 days | LOW | Skip - standard patterns |
| 2. Admin Commands | 1-2 days | LOW | Skip - extend existing |
| 3. Metrics | 2-3 days | LOW | Skip - bStats documented |
| 4. Export/Import | 4-5 days | MEDIUM | Consider - streaming JSON |

**Total Estimated Effort:** 9-13 days

**Recommended Approach:** Implement phases sequentially. Phases 1-3 can proceed with confidence. Phase 4 benefits from early format design review before implementation begins.

---

## Sources

### Stack Research
- [SpigotMC Wiki: Included Libraries](https://www.spigotmc.org/wiki/included-libraries-in-spigot/) - Gson bundled confirmation
- [bStats Getting Started](https://bstats.org/getting-started) - Integration guide
- [Maven Central: bstats-bukkit](https://central.sonatype.com/artifact/org.bstats/bstats-bukkit) - Version 3.1.0

### Features Research
- [PaperMC Boss Bars](https://docs.papermc.io/adventure/bossbar/) - Adventure API documentation
- [bStats Custom Charts](https://bstats.org/docs/custom-charts) - Custom metrics patterns
- [Advanced Achievements Plugin](https://modrinth.com/plugin/advanced-achievements-updated) - Notification patterns reference

### Architecture Research
- Codebase analysis: Collections.java, PlayerDataManager.java, Storage.java, CollectionsCommand.java
- Existing documentation: .planning/codebase/ARCHITECTURE.md

### Pitfalls Research
- [HuskSync](https://github.com/WiIIiam278/HuskSync) - Multi-server sync patterns
- [Fixing JSON OOM with Streaming](https://blog.jakubholy.net/fixing-json-oom-with-streaming-and-mapdb/) - Streaming export patterns
- [UnifiedMetrics](https://github.com/Cubxity/UnifiedMetrics) - Minecraft Prometheus patterns
- [Micrometer Best Practices](https://www.josedacruz.com/2025/06/08/mastering-micrometer-in-spring-boot-metrics-prometheus-observability-explained/) - Cardinality guidance
