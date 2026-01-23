# Requirements: Collections Plugin v1.3

**Defined:** 2026-01-23
**Core Value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## v1.3 Requirements

Requirements for the Web Control Panel milestone. Each maps to roadmap phases.

### Web Infrastructure

- [x] **WEB-01**: Plugin starts embedded Javalin web server on configurable port
- [x] **WEB-02**: Web server applies classloader fix for Bukkit compatibility
- [x] **WEB-03**: Web server stops gracefully on plugin disable (no port binding errors on reload)
- [x] **WEB-04**: Javalin/Jetty dependencies are relocated in shadowJar to avoid conflicts
- [x] **WEB-05**: Static files (HTML/JS/CSS) are served from plugin JAR

### Authentication

- [x] **AUTH-01**: Web panel requires password from config.yml to access
- [x] **AUTH-02**: Password is hashed in config (not stored in plaintext)

### Collection CRUD

- [x] **CRUD-01**: Admin can view list of all collections in web panel
- [x] **CRUD-02**: Admin can view details of a single collection
- [x] **CRUD-03**: Admin can create a new collection from scratch
- [x] **CRUD-04**: Admin can edit any field of an existing collection
- [x] **CRUD-05**: Admin can delete a collection (with confirmation prompt)
- [x] **CRUD-06**: YAML validation prevents saving invalid configurations
- [x] **CRUD-07**: Validation errors display clear messages with field locations

### Visual Builder

- [ ] **VB-01**: Admin can search/filter all Minecraft items by name
- [ ] **VB-02**: Admin can browse items in visual grid with icons
- [ ] **VB-03**: Admin can drag items from browser into collection slots
- [ ] **VB-04**: Admin can reorder items within collection via drag-drop
- [ ] **VB-05**: Admin can remove items from collection via drag-drop or button
- [ ] **VB-06**: Form fields exist for: tier, biomes, dimensions, y-level range
- [ ] **VB-07**: Item entry form has: material, name, lore lines, weight

### Visual Builder Enhancements

- [ ] **VBE-01**: MiniMessage formatted text shows live preview as it will appear in-game
- [ ] **VBE-02**: Collection templates available (forest, ocean, nether, cave, end, desert)
- [ ] **VBE-03**: Weight sum validation warns if item weights don't total 100%
- [ ] **VBE-04**: Admin can set percentage chance for an item and weights auto-adjust
- [ ] **VBE-05**: Visual display shows percentage drop chance for each item in collection

### Server Integration

- [x] **INT-01**: "Reload" button applies changes to running server without restart
- [x] **INT-02**: Connection status indicator shows if web panel is connected to server
- [x] **INT-03**: All Bukkit API calls from web handlers execute on main thread

### Documentation

- [ ] **DOC-01**: GitHub README updated with web panel feature documentation
- [ ] **DOC-02**: README includes setup instructions (port, password, first run)
- [ ] **DOC-03**: README includes screenshots of web panel interface

## Future Requirements (v1.4+)

Deferred to future releases. Tracked but not in current roadmap.

### Observability

- **OBS-01**: Prometheus metrics endpoint
- **OBS-02**: Per-collection completion rate tracking
- **OBS-03**: Spawn heatmap data export

### Admin Enhancements

- **ADMIN-06**: Batch admin operations
- **ADMIN-07**: Confirmation prompts for destructive operations
- **ADMIN-08**: Undo recent admin action

### Web Panel Enhancements

- **WEB-06**: Rate limiting on authentication (lock after failed attempts)
- **WEB-07**: HTTPS support
- **WEB-08**: Multi-user accounts with roles
- **WEB-09**: Undo/redo stack for edits
- **WEB-10**: Version history for collections

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Real-time collaborative editing | Massive complexity (CRDT/OT), unlikely use case for server configs |
| Multi-server dashboard | v1.3 focus is single server; network support is separate milestone |
| Player data management in web UI | Already have admin commands; web UI adds security risk |
| World map visualization for zones | Massive scope, requires world rendering infrastructure |
| Full plugin settings editor | config.yml is simpler, risk of breaking core functionality |
| OAuth/SSO integration | Enterprise feature, overkill for admin panel |
| WebSocket live sync | Overkill for config editing; reload button is sufficient |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| WEB-01 | Phase 18 | Complete |
| WEB-02 | Phase 18 | Complete |
| WEB-03 | Phase 18 | Complete |
| WEB-04 | Phase 18 | Complete |
| WEB-05 | Phase 18 | Complete |
| AUTH-01 | Phase 18 | Complete |
| AUTH-02 | Phase 18 | Complete |
| INT-03 | Phase 18 | Complete |
| CRUD-01 | Phase 19 | Complete |
| CRUD-02 | Phase 19 | Complete |
| INT-02 | Phase 19 | Complete |
| CRUD-03 | Phase 20 | Complete |
| CRUD-04 | Phase 20 | Complete |
| CRUD-05 | Phase 20 | Complete |
| CRUD-06 | Phase 20 | Complete |
| CRUD-07 | Phase 20 | Complete |
| INT-01 | Phase 20 | Complete |
| VB-01 | Phase 21 | Pending |
| VB-02 | Phase 21 | Pending |
| VB-03 | Phase 21 | Pending |
| VB-04 | Phase 21 | Pending |
| VB-05 | Phase 21 | Pending |
| VB-06 | Phase 21 | Pending |
| VB-07 | Phase 21 | Pending |
| VBE-01 | Phase 22 | Pending |
| VBE-02 | Phase 22 | Pending |
| VBE-03 | Phase 22 | Pending |
| VBE-04 | Phase 22 | Pending |
| VBE-05 | Phase 22 | Pending |
| DOC-01 | Phase 23 | Pending |
| DOC-02 | Phase 23 | Pending |
| DOC-03 | Phase 23 | Pending |

**Coverage:**
- v1.3 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0

---
*Requirements defined: 2026-01-23*
*Last updated: 2026-01-23 after Phase 20 complete*
