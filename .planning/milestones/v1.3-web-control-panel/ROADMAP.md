# Milestone v1.3: Web Control Panel

**Status:** ✅ SHIPPED 2026-01-24
**Phases:** 18-23
**Total Plans:** 15

## Overview

Enable visual collection management through an embedded web admin panel with full CRUD operations, drag-drop item builder, and live reload capability.

## Phases

### Phase 18: Web Infrastructure
**Goal**: Plugin hosts an embedded web server that serves static files and requires authentication
**Depends on**: Phase 17 (v1.2 complete)
**Requirements**: WEB-01, WEB-02, WEB-03, WEB-04, WEB-05, AUTH-01, AUTH-02, INT-03
**Plans**: 3 plans

Plans:
- [x] 18-01-PLAN.md - Add Javalin dependencies and shadowJar relocations
- [x] 18-02-PLAN.md - WebPanelManager with classloader fix and lifecycle
- [x] 18-03-PLAN.md - HTTP Basic auth, static files, and status endpoint

**Success Criteria:**
1. Admin can access web panel at configured port after plugin starts
2. Web panel displays login page when accessed without credentials
3. Admin can log in with password from config.yml
4. Plugin reload does not cause port binding errors
5. Password is stored as hash in config.yml (not plaintext)

### Phase 19: Read-Only API
**Goal**: Admin can view all collections and their details through the web panel
**Depends on**: Phase 18
**Requirements**: CRUD-01, CRUD-02, INT-02
**Plans**: 2 plans

Plans:
- [x] 19-01-PLAN.md - Backend API: DTO records and CollectionsController endpoints
- [x] 19-02-PLAN.md - Frontend: Collection list, detail view, and connection heartbeat

**Success Criteria:**
1. Web panel displays list of all collections with names and item counts
2. Admin can click a collection to view its full details (items, zones, rewards)
3. Connection status indicator shows green when server is reachable
4. Collection details load within 2 seconds

### Phase 20: Write API + CRUD
**Goal**: Admin can create, edit, and delete collections through the web panel
**Depends on**: Phase 19
**Requirements**: CRUD-03, CRUD-04, CRUD-05, CRUD-06, CRUD-07, INT-01
**Plans**: 3 plans

Plans:
- [x] 20-01-PLAN.md - Request DTOs, CollectionValidator, and CollectionYamlWriter
- [x] 20-02-PLAN.md - CRUD endpoints in CollectionsController with reload
- [x] 20-03-PLAN.md - Frontend forms, delete modal, and validation feedback

**Success Criteria:**
1. Admin can create a new collection with basic fields (name, tier, zones)
2. Admin can edit any field of an existing collection
3. Admin can delete a collection after confirmation prompt
4. Invalid YAML syntax shows clear error message with field location
5. Reload button applies changes to running server without restart

### Phase 21: Visual Builder
**Goal**: Admin can build collections by dragging items from a visual browser
**Depends on**: Phase 20
**Requirements**: VB-01, VB-02, VB-03, VB-04, VB-05, VB-06, VB-07
**Plans**: 3 plans

Plans:
- [x] 21-01-PLAN.md - Materials API endpoint and spawn condition form fields
- [x] 21-02-PLAN.md - Two-panel visual builder layout and CSS styling
- [x] 21-03-PLAN.md - Item browser JavaScript and SortableJS drag-drop integration

**Success Criteria:**
1. Admin can search/filter items by name and see visual grid with icons
2. Admin can drag items from browser into collection slots
3. Admin can reorder items within collection via drag-drop
4. Admin can remove items from collection via button or drag-out
5. Form fields exist for all collection properties (tier, biomes, dimensions, y-levels)

### Phase 22: Visual Builder Enhancements
**Goal**: Visual builder provides templates, weight validation, and MiniMessage preview
**Depends on**: Phase 21
**Requirements**: VBE-01, VBE-02, VBE-03, VBE-04, VBE-05
**Plans**: 3 plans

Plans:
- [x] 22-01-PLAN.md - Collection templates UI and data
- [x] 22-02-PLAN.md - Weight validation and percentage display/adjustment
- [x] 22-03-PLAN.md - MiniMessage live preview with minimessage-js

**Success Criteria:**
1. MiniMessage formatted text shows live preview as it will appear in-game
2. Admin can start new collection from template (forest, ocean, nether, cave, end, desert)
3. Weight sum validation warns if item weights do not total 100%
4. Admin can set percentage chance and weights auto-adjust other items
5. Visual display shows percentage drop chance for each item in collection

### Phase 23: Documentation
**Goal**: GitHub README documents web panel setup and usage
**Depends on**: Phase 22
**Requirements**: DOC-01, DOC-02, DOC-03
**Plans**: 1 plan

Plans:
- [x] 23-01-PLAN.md — Update README with web panel documentation and screenshots

**Success Criteria:**
1. README includes web panel feature section with capabilities overview
2. README includes setup instructions (port configuration, password, first run)
3. README includes screenshots of web panel interface

---

## Milestone Summary

**Key Decisions:**

| Phase | Decision | Rationale |
|-------|----------|-----------|
| 18-01 | Relocate all Javalin/Jetty transitive deps | Avoid conflicts with plugins like Dynmap |
| 18-02 | Classloader context swap for Javalin instantiation | Required for ServiceLoader compatibility in Bukkit |
| 18-02 | Web panel stops FIRST on disable | Release port for clean reload |
| 18-03 | HTTP Basic Auth for API routes | Simple, browser-native authentication |
| 19-01 | 2000ms timeout for MainThreadBridge calls | Ensures API responses complete within requirements |
| 19-02 | Hash-based routing (#collection/{id}) | Enables back/forward navigation without server round-trips |
| 20-01 | Java records with nullable types for request DTOs | Records provide immutable transfer, nullable handles missing JSON |
| 20-02 | RuntimeException message prefix for main thread error translation | Clean error propagation from main thread to HTTP response codes |
| 21-01 | Material enum filtering done without main thread bridge | Material.values() is static, safe on web thread |
| 21-03 | Display first 200 materials, search narrows results | Performance optimization for 1400+ materials |
| 21-03 | Clone-on-drag from browser | Browser items remain selectable after drag |
| 22-01 | 6 template types with pre-configured biomes, dimensions, and items | Reduces setup time from minutes to seconds |
| 22-02 | Integer weights summing to 100 | Simplifies percentage calculation and avoids floating point issues |
| 22-03 | Static MiniMessage.toHTML() for rendering | toHTML is static method on class, not instance method |
| 23-01 | Screenshot placeholder instead of actual screenshots | User chose skip, placeholder allows future enhancement |

**Issues Resolved:**

- All 32 v1.3 requirements satisfied
- Cross-phase integration verified (8/8 API routes wired)
- All 6 E2E user flows complete

**Issues Deferred:**

- Actual web panel screenshots (placeholder in README)
- Rate limiting on authentication (WEB-06)
- HTTPS support (WEB-07)

**Technical Debt Incurred:**

- Phase 22 missing VERIFICATION.md (documentation gap only, all requirements satisfied)
- Screenshots placeholder in README.md (non-blocking)

---

_For current project status, see .planning/ROADMAP.md_
_Archived: 2026-01-24_
