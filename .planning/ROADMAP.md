# Roadmap: Collections Plugin

## Current Milestone: v1.1 Operational Features

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 10 | Progress Notifications | Players receive immediate feedback when collecting items and completing collections | NOTIF-01..05 | 4 |
| 11 | Admin Commands | Server admins can manage player progress without database access | ADMIN-01..05 | 4 |
| 12 | Metrics & Observability | Server operators can monitor plugin health and players can display stats | METRICS-01..06 | 5 |
| 13 | Data Export/Import | Server admins can backup, migrate, and restore player data | EXPORT-01..06 | 4 |

---

### Phase 10: Progress Notifications

**Goal:** Players receive immediate visual and audio feedback when collecting items and completing collections.

**Dependencies:** None (builds on existing ItemUseListener)

**Requirements:** NOTIF-01, NOTIF-02, NOTIF-03, NOTIF-04, NOTIF-05

**Plans:** 3 plans

Plans:
- [ ] 10-01-PLAN.md — NotificationManager + config structure
- [ ] 10-02-PLAN.md — Integration into ConfirmAddGUI
- [ ] 10-03-PLAN.md — Unit tests for notification system

**Success Criteria:**
1. Player sees actionbar progress (e.g., "2/5 Forest Collection") when collecting a new item
2. Player sees title announcement and hears sound when completing any collection
3. Server admin can change notification style (actionbar/chat/title) via config.yml without restart
4. Rapidly collecting the same item does not flood the player with duplicate messages

---

### Phase 11: Admin Commands

**Goal:** Server admins can inspect and modify any player's collection progress through in-game commands.

**Dependencies:** None (uses existing PlayerDataManager)

**Requirements:** ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-04, ADMIN-05

**Success Criteria:**
1. Admin can run `/collections admin complete <player> <collection>` to mark a collection done
2. Admin can inspect any player's progress with `/collections admin inspect <player>` showing completion percentages
3. Commands work for offline players using name or UUID
4. Every admin action appears in server log with timestamp, executor name, and affected player

---

### Phase 12: Metrics & Observability

**Goal:** Server operators can monitor plugin activity via bStats dashboard and players can display stats via PlaceholderAPI.

**Dependencies:** Phase 10 (notification events feed counters), Phase 11 (admin actions feed counters)

**Requirements:** METRICS-01, METRICS-02, METRICS-03, METRICS-04, METRICS-05, METRICS-06

**Success Criteria:**
1. Plugin appears on bStats.org with server count and custom charts after server restart
2. Internal counters track items collected, collections completed, and spawn success/failure rates
3. Player can use `%collections_completed%` placeholder in chat plugins showing their completion count
4. Server can use `%collections_server_total%` placeholder showing server-wide statistics
5. Counter values persist across server restarts

---

### Phase 13: Data Export/Import

**Goal:** Server admins can export player data for backup/migration and import data with validation.

**Dependencies:** Phase 11 (shares admin permission structure)

**Requirements:** EXPORT-01, EXPORT-02, EXPORT-03, EXPORT-04, EXPORT-05, EXPORT-06

**Success Criteria:**
1. Admin can export single player or all players to JSON file in plugins/Collections/exports/
2. Export of large datasets (10k+ players) completes without OutOfMemoryError using streaming
3. Admin can dry-run import to see what would change before committing
4. Online players see their updated progress immediately after import without rejoin

---

## Progress

| Phase | Status | Plans | Completed |
|-------|--------|-------|-----------|
| 10 - Progress Notifications | Complete ✓ | 3 | 3 |
| 11 - Admin Commands | Pending | 0 | 0 |
| 12 - Metrics & Observability | Pending | 0 | 0 |
| 13 - Data Export/Import | Pending | 0 | 0 |

## Completed Milestones

- **v1.0 Quality Audit** (shipped 2026-01-22) — 9 phases, 24 plans
  - See `.planning/milestones/v1.0-ROADMAP.md` for full details

---
*Roadmap created: 2026-01-22*
*Last updated: 2026-01-22 after Phase 10 completion*
