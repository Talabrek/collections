---
phase: 16-add-flow-ux
plan: 01
subsystem: ui
tags: [gui, inventory, minecraft-bukkit, adventure-api]

# Dependency graph
requires:
  - phase: 14-tier-visibility
    provides: Collectible tier system, GUIManager utilities
provides:
  - AddPreviewGUI with 54-slot layout showing collection context
  - Collection grid display (21 items in rows 1-3)
  - Progress preview showing before/after adding
  - ADD_PREVIEW GUIType enum value
affects: [16-02-confirmation-transition, phase-17]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AddPreviewGUI pattern: 54-slot GUI with collection grid for add preview"
    - "Progress preview: Show current and 'after adding' progress bars"

key-files:
  created:
    - src/main/java/com/blockworlds/collections/gui/AddPreviewGUI.java
  modified:
    - src/main/java/com/blockworlds/collections/gui/GUIType.java
    - src/main/java/com/blockworlds/collections/listener/ItemUseListener.java

key-decisions:
  - "54-slot layout with 21 item slots in rows 1-3 (matching CollectionDetailGUI)"
  - "Info slot at position 40 (row 4 center) for progress preview"
  - "Yes button at slot 47, No button at slot 51, item display at slot 49"
  - "confirmAdd() left as stub for Plan 02 to implement full transition logic"

patterns-established:
  - "Add preview GUI pattern: Show collection context before consuming item"
  - "Reuse GUIManager utilities: createFiller, createConfirmButton, createCancelButton, createProgressBar"

# Metrics
duration: 4min
completed: 2026-01-23
---

# Phase 16 Plan 01: Add Preview GUI Summary

**54-slot AddPreviewGUI showing full collection grid with progress preview before adding items**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-23T00:00:00Z
- **Completed:** 2026-01-23T00:04:00Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- Created AddPreviewGUI with 54-slot layout showing full collection grid
- Collection grid displays 21 items (rows 1-3) with collected/uncollected states
- Item being added highlighted with green name and glowing enchantment effect
- Progress preview shows current X/Y and "after adding" progress bars
- Yes/No buttons positioned at slots 47 and 51
- Wired ItemUseListener to open AddPreviewGUI on collectible right-click

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AddPreviewGUI with collection grid and progress preview** - `5771278` (feat)
2. **Task 2: Wire ItemUseListener to open AddPreviewGUI** - `12a418b` (feat)
3. **Task 3: Build verification and manual test prep** - (verification only, no commit needed)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/gui/AddPreviewGUI.java` - New 54-slot preview GUI with collection grid, progress preview, confirm/cancel buttons
- `src/main/java/com/blockworlds/collections/gui/GUIType.java` - Added ADD_PREVIEW enum value
- `src/main/java/com/blockworlds/collections/listener/ItemUseListener.java` - Opens AddPreviewGUI instead of ConfirmAddGUI on right-click

## Decisions Made
- Used same ITEM_SLOTS layout as CollectionDetailGUI (slots 10-16, 19-25, 28-34) for consistency
- Added getter methods to AddPreviewGUI (getItemToAdd, getCollection, getCollectionItem, getPlayer) for Plan 02 to use
- confirmAdd() left as placeholder - sends "item-added-placeholder" message for now; Plan 02 will implement full transition

## Deviations from Plan
None - plan executed exactly as written.

## Issues Encountered
None - all tasks completed successfully.

## User Setup Required
None - no external service configuration required.

## Manual Test Checklist
For human verification (created per Task 3):
1. Right-click a collectible item
2. Verify 54-slot GUI opens with collection grid
3. Verify item being added shows with green name and glowing
4. Verify already-collected items show with gold name
5. Verify uncollected items show as ??? or with name if has progress
6. Verify progress bar shows current and "after adding" progress
7. Verify Yes and No buttons are visible at slots 47 and 51
8. Verify clicking No closes GUI and shows cancel message

## Next Phase Readiness
- AddPreviewGUI ready for Plan 02 to implement confirmAdd() with transition logic
- Full add logic will need to move from ConfirmAddGUI to AddPreviewGUI
- ConfirmAddGUI kept as reference (not deleted)

---
*Phase: 16-add-flow-ux*
*Completed: 2026-01-23*
