# Collections Plugin Audit

## What This Is

A comprehensive quality audit of the Collections plugin — an EQ2-style collectibles system for Paper 1.21.4. The plugin allows players to find, collect, and complete themed collections of items that spawn in the world. This audit identified bugs, performance issues, and correctness problems, then fixed and verified everything for network deployment.

## Core Value

Every player interaction must work correctly — collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## Current State

**v1.0 — Shipped 2026-01-22**

The plugin is production-ready for multi-server network deployment:
- Data integrity hardened with blocking saves, WAL mode, and transaction wrapping
- Race conditions eliminated with Folia-compatible schedulers and thread-safe collections
- GUI exploits prevented with comprehensive click cancellation and state versioning
- Memory leaks fixed with proper cleanup on quit and disable
- Entity tracking synchronized with dual-index and chunk-based spatial index
- Performance optimized for 50+ concurrent players with lazy iteration and batch inserts
- MySQL support added via StorageFactory pattern for multi-server networks
- 104 unit tests with 99% pass rate

## Current Milestone: v1.1 Operational Features

**Goal:** Add operator tooling, player feedback, and observability for production networks.

**Target features:**
- Data export/import commands for server migration
- Progress notification system ("1/5 collected")
- Admin force-complete command
- Metrics collection and observability

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

### Active (v1.1)

- [ ] Data export/import command for server migration
- [ ] Progress notification system ("1/5 collected")
- [ ] Admin force-complete command
- [ ] Metrics collection for spawn success rates
- [ ] Performance monitoring integration

### Deferred (v2+)

(None currently)

### Out of Scope

- New gameplay features — audit only, no feature additions
- UI redesign — functionality focus only
- Refactoring for code style — only fix functional issues
- PostgreSQL support — MySQL sufficient for network deployment
- Redis caching layer — adds complexity, MySQL sufficient

## Context

**Deployment Target:** Multi-server network
- Robust data handling with MySQL for shared state
- Performance scales with player count (50+ concurrent)
- Cannot lose player progress across server restarts

## Constraints

- **Tech stack**: Paper 1.21.4, Java 21, SQLite/MySQL — no changes
- **Compatibility**: Must remain Folia-compatible
- **Data**: Cannot break existing player data format
- **Testing**: Changes should be manually verifiable on dev server

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Full remediation approach | User wants issues found AND fixed | ✓ All 33 issues fixed |
| Network deployment target | Multi-server requires extra scrutiny on data/concurrency | ✓ MySQL + thread safety |
| 5-second quit save timeout | Safety margin for data persistence | ✓ Implemented |
| SQLite WAL mode | Concurrent readers during writes | ✓ Implemented |
| ConcurrentHashMap for progress | Thread-safe without explicit locks | ✓ Implemented |
| Factory pattern for storage | Clean backend switching | ✓ Implemented |

## Milestones

See `.planning/MILESTONES.md` for shipped milestones.
See `.planning/milestones/` for archived milestone details.

---
*Last updated: 2026-01-22 after v1.1 milestone started*
