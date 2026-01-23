---
phase: 22-visual-builder-enhancements
plan: 03
subsystem: web-panel
tags: [minimessage, preview, ui, javascript]
depends_on:
  requires: [22-02]
  provides: [minimessage-preview, live-text-preview]
  affects: []
tech-stack:
  added: [minimessage-js]
  patterns: [live-preview, parser-library]
key-files:
  created: []
  modified:
    - src/main/resources/web/index.html
    - src/main/resources/web/js/app.js
    - src/main/resources/web/css/admin.css
decisions: []
metrics:
  duration: 4 min
  completed: 2026-01-23
---

# Phase 22 Plan 03: MiniMessage Live Preview Summary

Live preview rendering for MiniMessage-formatted text using minimessage-js library.

## One-liner

MiniMessage live preview using minimessage-js for name/lore/message fields with graceful error handling.

## What Was Built

### Task 1: Add minimessage-js CDN and preview elements
- Added minimessage-js CDN script to index.html (after SortableJS, before app.js)
- Added preview div after form-name input with MiniMessage help text
- Added preview div after form-reward-message input with help text
- Added CSS for minimessage-preview styling with dark theme and Minecraft monospace font

### Task 2: Implement MiniMessage preview rendering
- Added miniMessageParser variable and initMiniMessageParser() function
- Added renderMiniMessage(text, previewElement) for parsing and rendering
- Added attachMiniMessagePreview(inputId, previewId) for static fields
- Added attachLorePreview(textarea, preview) for multiline item lore
- Initialize parser and attach previews on DOMContentLoaded
- Updated addItemRow() to include lore preview element and attach handler
- Updated convertBrowserItemToFormRow() to include lore preview
- Added lore preview CSS with dotted line separators between lines

## Technical Details

### MiniMessage Library Integration
- CDN: `https://unpkg.com/minimessage-js@^1.1/dist/minimessage.umd.js`
- Parser initialization: `MiniMessage.miniMessage()`
- Rendering: `miniMessageParser.toHTML(component, element)`

### Preview Behavior
- Empty text: Preview hidden (empty content)
- Valid MiniMessage: Rendered with colors/gradients/styles
- Invalid syntax: Shows plain text with error border color
- No parser available: Falls back to plain text display

### Supported Formatting
- Colors: `<red>`, `<gold>`, `<#FF5555>`
- Gradients: `<gradient:#FF0000:#00FF00>`
- Styles: `<bold>`, `<italic>`, `<underlined>`
- Combinations: `<gold><bold>Text</bold></gold>`

## Commits

| Hash | Description |
|------|-------------|
| 9339421 | feat(22-03): add minimessage-js CDN and preview elements |
| 4a491f1 | feat(22-03): implement MiniMessage preview rendering |

## Files Modified

- `src/main/resources/web/index.html` - CDN script and preview elements
- `src/main/resources/web/js/app.js` - MiniMessage functions and initialization
- `src/main/resources/web/css/admin.css` - Preview styling and lore line separators

## Deviations from Plan

None - plan executed exactly as written.

## Verification Status

Task 3 is a human verification checkpoint. User must manually test:
1. Type `<gold>Golden Collection` in Name field - verify gold text preview
2. Type `<gradient:#FF0000:#00FF00>Rainbow` - verify gradient preview
3. Type unclosed tag `<bold>Test` - verify graceful fallback
4. Type `<red>Congrats!</red>` in Completion Message - verify red text
5. Add item, type `<italic>Magical lore` in Lore - verify italic preview

## Must-Have Truth Verification

| Truth | Status |
|-------|--------|
| MiniMessage formatted text shows live preview as it will appear in-game | Implementation complete |
| Preview updates as admin types in name/lore/message fields | Implementation complete |
| Invalid MiniMessage syntax shows graceful fallback (plain text) | Implementation complete |
| Colors, gradients, and text styles render correctly in preview | Implementation complete |

## Next Phase Readiness

Phase 22 (Visual Builder Enhancements) is complete after human verification of this plan.
All 5 VBE requirements are now implemented:
- VBE-01: MiniMessage live preview (this plan)
- VBE-02: Collection templates (22-01)
- VBE-03: Item weight validation (22-02)
- VBE-04: Percentage-based weight adjustment (22-02)
- VBE-05: Drag-and-drop item browser (21-03)
