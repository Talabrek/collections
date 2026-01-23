# Roadmap: Collections Plugin

## Milestones

- [x] **v1.0 Quality Audit** - Phases 1-9 (shipped 2026-01-22)
- [x] **v1.1 Operational Features** - Phases 10-13 (shipped 2026-01-22)
- [x] **v1.2 Enhanced Collection UX** - Phases 14-17 (shipped 2026-01-23)
- [ ] **v1.3 Web Control Panel** - Phases 18-23 (in progress)

## Phases

<details>
<summary>v1.0 Quality Audit (Phases 1-9) - SHIPPED 2026-01-22</summary>

See `.planning/milestones/v1.0-quality-audit/` for archived details.

</details>

<details>
<summary>v1.1 Operational Features (Phases 10-13) - SHIPPED 2026-01-22</summary>

See `.planning/milestones/v1.1-operational-features/` for archived details.

</details>

<details>
<summary>v1.2 Enhanced Collection UX (Phases 14-17) - SHIPPED 2026-01-23</summary>

See `.planning/milestones/v1.2-enhanced-ux/` for archived details.

</details>

### v1.3 Web Control Panel (In Progress)

**Milestone Goal:** Enable visual collection management through an embedded web admin panel with full CRUD operations, drag-drop item builder, and live reload capability.

- [ ] **Phase 18: Web Infrastructure** - Embedded Javalin server with auth and static file serving
- [ ] **Phase 19: Read-Only API** - Collection listing and detail viewing endpoints
- [ ] **Phase 20: Write API + CRUD** - Create, edit, delete operations with reload
- [ ] **Phase 21: Visual Builder** - Drag-drop item browser and collection editor
- [ ] **Phase 22: Visual Builder Enhancements** - MiniMessage preview, templates, weight validation
- [ ] **Phase 23: Documentation** - README update with setup instructions and screenshots

## Phase Details

### Phase 18: Web Infrastructure
**Goal**: Plugin hosts an embedded web server that serves static files and requires authentication
**Depends on**: Phase 17 (v1.2 complete)
**Requirements**: WEB-01, WEB-02, WEB-03, WEB-04, WEB-05, AUTH-01, AUTH-02, INT-03
**Success Criteria** (what must be TRUE):
  1. Admin can access web panel at configured port after plugin starts
  2. Web panel displays login page when accessed without credentials
  3. Admin can log in with password from config.yml
  4. Plugin reload does not cause port binding errors
  5. Password is stored as hash in config.yml (not plaintext)
**Plans**: TBD

Plans:
- [ ] 18-01: TBD
- [ ] 18-02: TBD
- [ ] 18-03: TBD

### Phase 19: Read-Only API
**Goal**: Admin can view all collections and their details through the web panel
**Depends on**: Phase 18
**Requirements**: CRUD-01, CRUD-02, INT-02
**Success Criteria** (what must be TRUE):
  1. Web panel displays list of all collections with names and item counts
  2. Admin can click a collection to view its full details (items, zones, rewards)
  3. Connection status indicator shows green when server is reachable
  4. Collection details load within 2 seconds
**Plans**: TBD

Plans:
- [ ] 19-01: TBD
- [ ] 19-02: TBD

### Phase 20: Write API + CRUD
**Goal**: Admin can create, edit, and delete collections through the web panel
**Depends on**: Phase 19
**Requirements**: CRUD-03, CRUD-04, CRUD-05, CRUD-06, CRUD-07, INT-01
**Success Criteria** (what must be TRUE):
  1. Admin can create a new collection with basic fields (name, tier, zones)
  2. Admin can edit any field of an existing collection
  3. Admin can delete a collection after confirmation prompt
  4. Invalid YAML syntax shows clear error message with field location
  5. Reload button applies changes to running server without restart
**Plans**: TBD

Plans:
- [ ] 20-01: TBD
- [ ] 20-02: TBD
- [ ] 20-03: TBD

### Phase 21: Visual Builder
**Goal**: Admin can build collections by dragging items from a visual browser
**Depends on**: Phase 20
**Requirements**: VB-01, VB-02, VB-03, VB-04, VB-05, VB-06, VB-07
**Success Criteria** (what must be TRUE):
  1. Admin can search/filter items by name and see visual grid with icons
  2. Admin can drag items from browser into collection slots
  3. Admin can reorder items within collection via drag-drop
  4. Admin can remove items from collection via button or drag-out
  5. Form fields exist for all collection properties (tier, biomes, dimensions, y-levels)
**Plans**: TBD

Plans:
- [ ] 21-01: TBD
- [ ] 21-02: TBD
- [ ] 21-03: TBD

### Phase 22: Visual Builder Enhancements
**Goal**: Visual builder provides templates, weight validation, and MiniMessage preview
**Depends on**: Phase 21
**Requirements**: VBE-01, VBE-02, VBE-03, VBE-04, VBE-05
**Success Criteria** (what must be TRUE):
  1. MiniMessage formatted text shows live preview as it will appear in-game
  2. Admin can start new collection from template (forest, ocean, nether, cave, end, desert)
  3. Weight sum validation warns if item weights do not total 100%
  4. Admin can set percentage chance and weights auto-adjust other items
  5. Visual display shows percentage drop chance for each item in collection
**Plans**: TBD

Plans:
- [ ] 22-01: TBD
- [ ] 22-02: TBD

### Phase 23: Documentation
**Goal**: GitHub README documents web panel setup and usage
**Depends on**: Phase 22
**Requirements**: DOC-01, DOC-02, DOC-03
**Success Criteria** (what must be TRUE):
  1. README includes web panel feature section with capabilities overview
  2. README includes setup instructions (port configuration, password, first run)
  3. README includes screenshots of web panel interface
**Plans**: TBD

Plans:
- [ ] 23-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 18 -> 19 -> 20 -> 21 -> 22 -> 23

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 18. Web Infrastructure | v1.3 | 0/TBD | Not started | - |
| 19. Read-Only API | v1.3 | 0/TBD | Not started | - |
| 20. Write API + CRUD | v1.3 | 0/TBD | Not started | - |
| 21. Visual Builder | v1.3 | 0/TBD | Not started | - |
| 22. Visual Builder Enhancements | v1.3 | 0/TBD | Not started | - |
| 23. Documentation | v1.3 | 0/TBD | Not started | - |

---
*Roadmap created: 2026-01-23*
*Last updated: 2026-01-23*
