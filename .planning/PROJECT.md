# Collections Plugin

## What This Is

A comprehensive EQ2-style collectibles system for Paper 1.21.4 servers. Players find, collect, and complete themed collections of items that spawn in the world. The plugin provides real-time progress notifications, admin tools for managing player data, bStats analytics, PlaceholderAPI integration, and data export/import for server migration.

## Core Value

Every player interaction must work correctly — collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## Current State

**v1.2 Enhanced Collection UX — Shipped 2026-01-23**

The plugin now provides an enhanced collection experience with:
- EPIC and LEGENDARY collectible tiers with distinct particles and visibility rules
- Boss bar radar showing nearby collectibles with direction indicators
- Full collection grid preview when adding items with Yes/No confirmation
- Smooth GUI transitions with highlighted just-added items
- Milestone notifications at 25%, 50%, 75% progress with escalating celebrations
- 20,371 lines of Java with comprehensive test coverage

**v1.1 Operational Features — Shipped 2026-01-22**

Production-ready for multi-server network deployment with:
- Progress notifications with configurable actionbar/chat/title styles
- Admin commands for inspecting and force-completing any player's progress
- bStats community metrics with custom charts
- PlaceholderAPI expansion for player and server statistics
- Streaming JSON export/import for server migration
- MySQL support for multi-server networks

## Requirements

### Validated

- ✓ Collectible spawning system — v1.0
- ✓ Player progress tracking — v1.0
- ✓ GUI-based collection browser — v1.0
- ✓ Tier-based visibility with goggles — v1.0
- ✓ Alternative drop sources (mobs, blocks, fishing, loot) — v1.0
- ✓ Collection completion rewards — v1.0
- ✓ SQLite persistence with HikariCP — v1.0
- ✓ Folia-compatible scheduling — v1.0
- ✓ MySQL storage for multi-server networks — v1.0
- ✓ All identified bugs from codebase audit fixed — v1.0
- ✓ Performance bottlenecks addressed for network scale — v1.0
- ✓ Race conditions eliminated — v1.0
- ✓ Memory leaks resolved — v1.0
- ✓ Data integrity verified (no lost progress) — v1.0
- ✓ GUI interactions handle all edge cases — v1.0
- ✓ Chunk load/unload correctly manages entity state — v1.0
- ✓ All default collections properly deployed — v1.0
- ✓ Progress notification system — v1.1
- ✓ Completion notification with sound — v1.1
- ✓ Admin force-complete command — v1.1
- ✓ Admin inspect command — v1.1
- ✓ Offline player command support — v1.1
- ✓ Admin action audit logging — v1.1
- ✓ bStats community metrics — v1.1
- ✓ PlaceholderAPI player stats — v1.1
- ✓ PlaceholderAPI server stats — v1.1
- ✓ Metrics counter persistence — v1.1
- ✓ Streaming JSON export — v1.1
- ✓ JSON import with validation — v1.1
- ✓ Import dry-run mode — v1.1
- ✓ Import cache invalidation — v1.1
- ✓ Tier visibility fix (EPIC/LEGENDARY with helmet visibility) — v1.2
- ✓ Boss bar radar for nearby collectibles — v1.2
- ✓ Full collection grid preview on add — v1.2
- ✓ Add confirmation UI with Yes/No — v1.2
- ✓ Show collection after adding item with highlight — v1.2
- ✓ Milestone notifications at 25/50/75% progress — v1.2

### Active (v1.3+)

- [ ] Prometheus metrics endpoint (OBS-01)
- [ ] Per-collection completion rate tracking (OBS-02)
- [ ] Spawn heatmap data export (OBS-03)
- [ ] Batch admin operations (ADMIN-06)
- [ ] Confirmation prompts for destructive operations (ADMIN-07)
- [ ] Undo recent admin action (ADMIN-08)

### Out of Scope

- Real-time cross-server sync — MySQL shared state sufficient; Redis adds complexity
- Web dashboard — Out of scope for plugin; use bStats dashboard
- Discord integration — Better handled by dedicated Discord plugins
- PostgreSQL support — MySQL sufficient for network deployment

## Context

**Deployment Target:** Multi-server network
- MySQL for shared state across servers
- Performance scales with 50+ concurrent players
- Full operational tooling for network administrators
- Data export/import for server migration

**Codebase:** 20,371 lines of Java, 73 files, 120 unit tests

## Constraints

- **Tech stack**: Paper 1.21.4, Java 21, SQLite/MySQL — no changes
- **Compatibility**: Must remain Folia-compatible
- **Data**: Cannot break existing player data format
- **Testing**: Changes should be manually verifiable on dev server

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Full remediation approach | User wants issues found AND fixed | ✓ All 33 v1.0 issues fixed |
| Network deployment target | Multi-server requires extra scrutiny | ✓ MySQL + thread safety |
| 5-second quit save timeout | Safety margin for data persistence | ✓ Implemented |
| SQLite WAL mode | Concurrent readers during writes | ✓ Implemented |
| ConcurrentHashMap for progress | Thread-safe without explicit locks | ✓ Implemented |
| Factory pattern for storage | Clean backend switching | ✓ Implemented |
| Actionbar for progress notifications | Non-intrusive feedback | ✓ Implemented |
| Title for completion notifications | Celebratory impact | ✓ Implemented |
| Offline data not cached | Avoid memory leaks | ✓ Implemented |
| bStats relocated | Avoid plugin conflicts | ✓ Implemented |
| AtomicLong for counters | Thread-safe async access | ✓ Implemented |
| Streaming JSON export | Avoid OOM on large datasets | ✓ Implemented |
| Validation-first import | No partial writes | ✓ Implemented |
| EPIC/LEGENDARY particles | Distinct visuals per tier | ✓ SOUL_FIRE_FLAME / DRAGON_BREATH |
| Boss bar radar | Real-time collectible detection | ✓ With direction indicators |
| Milestone bitmask | Once-per-milestone triggering | ✓ Database-persisted byte field |
| GUI transition pattern | Smooth add → view flow | ✓ Unregister source before open |

## Milestones

See `.planning/MILESTONES.md` for shipped milestones.
See `.planning/milestones/` for archived milestone details.

---
*Last updated: 2026-01-23 after v1.2 milestone completion*
