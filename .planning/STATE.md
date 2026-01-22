# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-22)

**Core value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.
**Current focus:** v1.2 Enhanced Collection UX

## Current Position

Milestone: v1.2 Enhanced Collection UX
Phase: 17 - Milestone Notifications (in progress)
Plan: 01 of 3 complete
Status: In progress
Last activity: 2026-01-22 - Completed 17-01-PLAN.md

Progress: [███████████████░░░░░] 3/4 phases (Plan 17-01 complete)

## Shipped Milestones

| Milestone | Phases | Plans | Date |
|-----------|--------|-------|------|
| v1.0 Quality Audit | 1-9 | 24 | 2026-01-22 |
| v1.1 Operational Features | 10-13 | 14 | 2026-01-22 |

See `.planning/MILESTONES.md` for summary.
See `.planning/milestones/` for archived details.

## Performance Metrics

**v1.0 Velocity:**
- Total plans completed: 24
- Average duration: 5.1 min
- Total execution time: 122 min

**v1.1 Velocity:**
- Total plans completed: 14
- Average duration: 5.4 min
- Total execution time: ~76 min

**v1.2 Velocity (in progress):**
- Plans completed: 5
- Phase 14-01 duration: 4 min
- Phase 16-01 duration: 4 min
- Phase 16-02 duration: 4 min
- Phase 15-01 duration: 9 min
- Phase 17-01 duration: 7 min

## Accumulated Context

### Key Decisions

Major decisions are logged in PROJECT.md Key Decisions table.
Full decision history archived in `.planning/milestones/`.

**v1.2 Decisions:**
- EPIC tier uses SOUL_FIRE_FLAME particle and DARK_PURPLE color
- LEGENDARY tier uses DRAGON_BREATH particle and GOLD color
- Tier hierarchy: COMMON < UNCOMMON < RARE < EPIC < LEGENDARY < EVENT
- AddPreviewGUI uses 54-slot layout with 21 item slots matching CollectionDetailGUI
- Yes button at slot 47, No button at slot 51, item display at slot 49
- Highlighted items use glowing effect + "Just added!" lore line
- GUI transition: unregister source GUI before creating/opening destination GUI
- Radar uses BossBar API with tier-based colors (WHITE=common through PINK=event)
- Radar shows direction indicator [^] [<] [>] to nearest collectible
- Progress bar fixed at 1.0, color indicates highest tier detected
- Milestone bitmask: bit 0=25%, bit 1=50%, bit 2=75% (byte field in CollectionProgress)
- Migration uses try/catch for backward compatibility with existing databases

### Known Issues

- Pre-existing MockBukkit test failure (IncompatibleClassChangeError) - does not affect functionality, only test suite. Requires MockBukkit update for Paper 1.21.4.
- Pre-existing flaky test (testAddItemOffline_UsesCache) - race condition in test, not implementation

### Pending Todos

- Phase 17-02 (Milestone Detection Logic)
- Phase 17-03 (Milestone Notification Display)

## Session Continuity

Last session: 2026-01-22
Stopped at: Completed 17-01-PLAN.md
Resume with: Continue Phase 17 plans (17-02, 17-03)

---
*Updated: 2026-01-22 after 17-01-PLAN.md completion*
