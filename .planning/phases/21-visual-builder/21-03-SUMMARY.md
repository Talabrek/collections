---
phase: 21-visual-builder
plan: 03
subsystem: ui
tags: [javascript, sortablejs, drag-drop, visual-builder, material-browser]

# Dependency graph
requires:
  - phase: 21-01
    provides: GET /api/materials endpoint with material names
  - phase: 21-02
    provides: SortableJS CDN, two-panel layout HTML, visual builder CSS
provides:
  - JavaScript item browser with material fetch and search
  - SortableJS drag-drop integration (browser to collection, reordering)
  - Visual item selection without manual text entry
  - Real-time search with emoji icons for materials
affects: [future collection management features, item form enhancements]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SortableJS clone-on-drag pattern for item browser"
    - "Debounced search input (150ms) for performance"
    - "Material emoji mapping for visual identification"

key-files:
  created: []
  modified:
    - src/main/resources/web/js/app.js

key-decisions:
  - "Browser displays first 200 materials, search narrows results (performance optimization)"
  - "150ms debounce on search input to avoid flicker on fast typing"
  - "Clone-on-drag from browser (pull: 'clone') keeps original in browser"
  - "Drag handle required for collection reordering (handle: '.drag-handle')"
  - "Materials cached in allMaterials array to avoid redundant API calls"

patterns-established:
  - "SortableJS group pattern: 'collection-items' connects browser and collection"
  - "onAdd callback converts browser item to full form row with all fields"
  - "Drop zone visual feedback via dragenter/dragleave/drop listeners"

# Metrics
duration: 3min
completed: 2026-01-23
---

# Phase 21 Plan 03: Visual Builder JavaScript Summary

**Complete visual item builder with material search, drag-drop from browser to collection, reordering, and emoji icons for ~1400 materials**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-23T09:21:06Z
- **Completed:** 2026-01-23T09:24:32Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments
- Admin can search/filter materials by name in real-time
- Admin can drag items from browser grid into collection (clone-on-drag)
- Admin can reorder items within collection via drag-drop
- Dragged items automatically convert to full form rows with all fields
- Material browser displays emoji icons for visual identification

## Task Commits

1. **Tasks 1-3: Item browser and drag-drop** - `5a13cb9` (feat)

Combined commit includes all three tasks:
- Task 1: Item browser fetch and render
- Task 2: SortableJS drag-drop integration
- Task 3: Polish and edge cases

## Files Created/Modified
- `src/main/resources/web/js/app.js` - Added item browser, SortableJS integration, search, and drag-drop handlers

## Decisions Made

**FORM-02: Display first 200 materials, search narrows results**
- Rationale: Performance optimization - rendering all 1400+ materials at once would slow initial load
- Search effectively narrows results to relevant materials

**SEARCH-01: 150ms debounce on search input**
- Rationale: Prevents flicker and excessive re-renders during fast typing
- Balances responsiveness with performance

**DRAG-01: Clone-on-drag from browser**
- Rationale: Browser items should remain selectable, not disappear after drag
- Implemented via SortableJS `pull: 'clone'` on browser sortable

**DRAG-02: Drag handle for collection reordering**
- Rationale: Prevents accidental reordering when clicking form fields
- Hamburger icon (&#9776;) provides clear affordance

**CACHE-01: Cache materials in allMaterials array**
- Rationale: Avoid redundant API calls when switching between collections
- initItemBrowser() checks cache before fetching

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

Visual builder is fully functional:
- Material browser loads and searches correctly
- Drag-drop from browser to collection works
- Reordering within collection works
- Item removal works
- All form fields populated correctly

Ready for:
- Phase 22: Advanced form features (conditional fields, bulk actions)
- Phase 23: Testing and polish

No blockers.

---
*Phase: 21-visual-builder*
*Completed: 2026-01-23*
