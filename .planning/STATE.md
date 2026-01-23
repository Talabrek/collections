# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-23)

**Core value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.
**Current focus:** v1.3 Web Control Panel - Phase 20 complete

## Current Position

Milestone: v1.3 Web Control Panel
Phase: 21 of 23 (Visual Builder)
Plan: 3 of 3 complete
Status: Phase complete
Last activity: 2026-01-23 - Completed Phase 21 (Visual Builder)

Progress: [####################] 100% (Phase 21 complete)

## Shipped Milestones

| Milestone | Phases | Plans | Date |
|-----------|--------|-------|------|
| v1.0 Quality Audit | 1-9 | 24 | 2026-01-22 |
| v1.1 Operational Features | 10-13 | 14 | 2026-01-22 |
| v1.2 Enhanced Collection UX | 14-17 | 6 | 2026-01-23 |

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

**v1.2 Velocity:**
- Total plans completed: 6
- Average duration: 5.7 min
- Total execution time: ~34 min

**v1.3 Velocity (in progress):**
- Plans completed: 11
- Duration: 54 min (6 + 5 + 6 + 5 + 5 + 6 + 5 + 5 + 6 + 2 + 3)

## Accumulated Context

### Key Decisions

Major decisions are logged in PROJECT.md Key Decisions table.
Full decision history archived in `.planning/milestones/`.

| Phase | Decision | Rationale |
|-------|----------|-----------|
| 18-01 | WEB-04: Relocate all Javalin/Jetty transitive deps | Avoid conflicts with plugins like Dynmap |
| 18-02 | WEB-02: Classloader context swap for Javalin instantiation | Required for ServiceLoader compatibility in Bukkit |
| 18-02 | WEB-03: Web panel stops FIRST on disable | Release port for clean reload |
| 18-03 | AUTH-01: HTTP Basic Auth for API routes | Simple, browser-native authentication |
| 18-03 | WEB-05: Static files at root, API at /api/ | Clean separation of concerns |
| 19-01 | API-01: 2000ms timeout for MainThreadBridge calls | Ensures API responses complete within requirements |
| 19-02 | UI-01: Hash-based routing (#collection/{id}) | Enables back/forward navigation without server round-trips |
| 19-02 | UI-02: 30-second heartbeat interval | Balances responsiveness with minimal server load |
| 20-01 | DTO-01: Java records with nullable types for request DTOs | Records provide immutable transfer, nullable handles missing JSON |
| 20-01 | VAL-01: Use isValid instead of valid for ValidationResult | Avoids Java record accessor/static method name conflict |
| 20-02 | CRUD-01: RuntimeException message prefix for main thread error translation | Clean error propagation from main thread to HTTP response codes |
| 21-01 | API-02: Material enum filtering done without main thread bridge | Material.values() is static, safe on web thread |
| 21-01 | FORM-01: Spawn conditions default to Overworld, Y -64 to 320 | Minecraft 1.21 world bounds |
| 21-03 | FORM-02: Display first 200 materials, search narrows results | Performance optimization for 1400+ materials |
| 21-03 | SEARCH-01: 150ms debounce on search input | Prevents flicker and excessive re-renders |
| 21-03 | DRAG-01: Clone-on-drag from browser | Browser items remain selectable after drag |
| 21-03 | DRAG-02: Drag handle for collection reordering | Prevents accidental reordering |
| 21-03 | CACHE-01: Cache materials in allMaterials array | Avoid redundant API calls |

### Known Issues

- Pre-existing MockBukkit test failure (IncompatibleClassChangeError) - does not affect functionality, only test suite. Requires MockBukkit update for Paper 1.21.4.
- Pre-existing flaky test (testAddItemOffline_UsesCache) - race condition in test, not implementation
- ParticleTask missing EPIC/LEGENDARY switch cases (cosmetic only - visibility works, particles don't display for these tiers)

### Pending Todos

None for v1.3 yet.

## Session Continuity

Last session: 2026-01-23
Stopped at: Phase 21 complete
Resume with: `/gsd:plan-phase 22` to plan Visual Builder Enhancements phase

---
*Updated: 2026-01-23 after 21-03 complete*
