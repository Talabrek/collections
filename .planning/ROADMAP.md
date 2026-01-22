# Roadmap: Collections Plugin v1.2

## Overview

v1.2 Enhanced Collection UX focuses on fixing tier visibility bugs and improving the player experience with a collectible radar, streamlined add flow, and milestone notifications. Four phases deliver visibility fixes, boss bar radar, UX improvements, and progress notifications.

## Progress

| Phase | Name | Status | Plans |
|-------|------|--------|-------|
| 14 | Tier Visibility | Complete | 1/1 |
| 15 | Collectible Radar | Complete | 1/1 |
| 16 | Add Flow UX | Complete | 2/2 |
| 17 | Milestone Notifications | Pending | 0/? |

---

## Phase 14: Tier Visibility

**Goal:** Collectibles respect tier visibility rules based on equipped collector's helmet.

**Dependencies:** None (standalone bug fix)

**Requirements:**
- VIS-01: Uncommon collectibles completely invisible without normal collector's helmet
- VIS-02: Rare+ collectibles completely invisible without upgraded collector's helmet
- VIS-03: Common collectibles always visible regardless of helmet

**Success Criteria:**
1. Player without any helmet can see common collectibles but NOT uncommon/rare/epic/legendary
2. Player with normal collector's helmet can see common and uncommon, but NOT rare/epic/legendary
3. Player with upgraded collector's helmet can see all tiers (common through legendary)
4. Visibility changes immediately when helmet is equipped or removed

**Plans:** 1 plan

Plans:
- [x] 14-01-PLAN.md -- Extend tier enum and fix visibility mapping

---

## Phase 15: Collectible Radar

**Goal:** Players wearing collector's helmet see a boss bar radar showing nearby collectibles.

**Dependencies:** Phase 14 (visibility logic informs radar detection)

**Requirements:**
- RADAR-01: Boss bar displays nearby collectibles when wearing collector's helmet
- RADAR-02: Normal helmet radar detects common and uncommon collectibles
- RADAR-03: Upgraded helmet radar detects all collectible tiers
- RADAR-04: Boss bar radar hidden when not wearing any collector's helmet

**Success Criteria:**
1. Player with no helmet sees no radar boss bar
2. Player with normal helmet sees boss bar showing count/direction of common+uncommon collectibles within range
3. Player with upgraded helmet sees boss bar showing count/direction of all collectibles within range
4. Boss bar appears when helmet is equipped and disappears when removed
5. Radar updates as player moves and collectibles enter/exit range

**Plans:** 1 plan

Plans:
- [x] 15-01-PLAN.md -- Implement RadarManager, RadarTask, and helmet integration

---

## Phase 16: Add Flow UX

**Goal:** Right-clicking a collectible shows a full collection grid with confirmation before adding.

**Dependencies:** None (standalone UX enhancement)

**Requirements:**
- UX-01: Right-clicking collectible opens full collection grid showing all slots
- UX-02: Add screen displays progress summary (X/Y collected)
- UX-03: Add screen includes Yes/No confirmation buttons
- UX-04: After confirming add, GUI transitions to show the collection

**Success Criteria:**
1. Right-clicking collectible opens GUI showing full collection grid with all slots visible
2. Collection grid shows which items are already collected (filled slots) and which are missing
3. Progress summary (e.g., "3/8 Collected") is displayed in the add screen
4. Yes button adds item and transitions to collection view; No button cancels and closes GUI
5. After adding, player sees the updated collection view with newly added item highlighted

**Plans:** 2 plans

Plans:
- [x] 16-01-PLAN.md -- Create AddPreviewGUI with collection grid and progress preview
- [x] 16-02-PLAN.md -- Implement confirm flow with GUI transition and highlight

---

## Phase 17: Milestone Notifications

**Goal:** Players receive celebratory notifications at 25%, 50%, and 75% collection progress.

**Dependencies:** Phase 16 (UX flow may trigger notifications)

**Requirements:**
- NOTIF-01: Player receives notification at 25% collection progress
- NOTIF-02: Player receives notification at 50% collection progress
- NOTIF-03: Player receives notification at 75% collection progress

**Success Criteria:**
1. Player receives distinct notification when reaching 25% of a collection
2. Player receives distinct notification when reaching 50% of a collection
3. Player receives distinct notification when reaching 75% of a collection
4. Notifications only trigger once per milestone per collection (not on every item after threshold)
5. Notification style is celebratory and includes collection name and milestone reached

**Plans:** TBD during plan-phase

---

## Dependency Graph

```
Phase 14 (Visibility) ──> Phase 15 (Radar)
                              │
Phase 16 (Add Flow UX) ──────>│
                              v
                         Phase 17 (Notifications)
```

**Execution order:** 14 -> 15 (depends on 14), 16 (parallel with 14-15), 17 (after 15 and 16)

**Recommended sequence:** 14 -> 16 -> 15 -> 17 (fix visibility, then UX, then radar, then notifications)

---

## Coverage Summary

| Category | Requirements | Phase |
|----------|--------------|-------|
| Visibility | VIS-01, VIS-02, VIS-03 | 14 |
| Radar | RADAR-01, RADAR-02, RADAR-03, RADAR-04 | 15 |
| Add Flow UX | UX-01, UX-02, UX-03, UX-04 | 16 |
| Notifications | NOTIF-01, NOTIF-02, NOTIF-03 | 17 |

**Total:** 14 requirements mapped to 4 phases
**Coverage:** 14/14 (100%)

---
*Roadmap created: 2026-01-23*
*Milestone: v1.2 Enhanced Collection UX*
