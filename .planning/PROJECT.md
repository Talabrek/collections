# Collections Plugin Audit

## What This Is

A comprehensive quality audit of the Collections plugin — an EQ2-style collectibles system for Paper 1.21.4. The plugin allows players to find, collect, and complete themed collections of items that spawn in the world. This audit will identify bugs, performance issues, and correctness problems, then fix and verify everything before network deployment.

## Core Value

Every player interaction must work correctly — collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## Requirements

### Validated

- ✓ Collectible spawning system — existing
- ✓ Player progress tracking — existing
- ✓ GUI-based collection browser — existing
- ✓ Tier-based visibility with goggles — existing
- ✓ Alternative drop sources (mobs, blocks, fishing, loot) — existing
- ✓ Collection completion rewards — existing
- ✓ SQLite persistence with HikariCP — existing
- ✓ Folia-compatible scheduling — existing

### Active

- [ ] All identified bugs from codebase audit fixed
- [ ] Performance bottlenecks addressed for network scale
- [ ] Race conditions eliminated
- [ ] Memory leaks resolved
- [ ] Data integrity verified (no lost progress)
- [ ] GUI interactions handle all edge cases
- [ ] Chunk load/unload correctly manages entity state
- [ ] All default collections properly deployed

### Out of Scope

- New features — audit only, no feature additions
- MySQL/PostgreSQL migration — SQLite sufficient for now
- UI redesign — functionality focus only
- Refactoring for code style — only fix functional issues

## Context

**Deployment Target:** Multi-server network
- Requires robust data handling
- Performance must scale with player count
- Cannot lose player progress across server restarts

**Current State:** Core flow works, looking for edge cases and subtle bugs

**Existing Codebase Audit (from .planning/codebase/CONCERNS.md):**

Known issues to investigate:
- Race condition in PlayerDataManager.getProgress() — returns null for recently joined players
- Cooldown map memory leak — unbounded growth if cleanup not called
- O(players × collectibles) particle task iteration
- Linear search for collectible by entity (no index)
- Database writes not batched
- Grid search creates thousands of Location objects
- Chunk load/unload entity recreation is fragile
- GUI state management during mutations
- Async database exception handling may swallow errors

Tech debt:
- Dead stub file at wrong package path
- Inconsistent null returns (should use Optional)
- saveDefaultCollections only saves one file
- Duplicated spawn condition parsing
- Duplicated surface location finding

Security considerations:
- Command execution in rewards (admin-defined, acceptable)
- No input validation on collection IDs

## Constraints

- **Tech stack**: Paper 1.21.4, Java 21, SQLite — no changes
- **Compatibility**: Must remain Folia-compatible
- **Data**: Cannot break existing player data format
- **Testing**: Changes should be manually verifiable on dev server

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Full remediation approach | User wants issues found AND fixed | — Pending |
| Network deployment target | Multi-server requires extra scrutiny on data/concurrency | — Pending |

---
*Last updated: 2026-01-20 after initialization*
