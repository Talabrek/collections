# Requirements: Collections Plugin v1.3

**Defined:** 2026-01-23
**Core Value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## v1.3 Requirements

Requirements for the Web Control Panel milestone. Each maps to roadmap phases.

### Web Infrastructure

- [ ] **WEB-01**: Plugin starts embedded Javalin web server on configurable port
- [ ] **WEB-02**: Web server applies classloader fix for Bukkit compatibility
- [ ] **WEB-03**: Web server stops gracefully on plugin disable (no port binding errors on reload)
- [ ] **WEB-04**: Javalin/Jetty dependencies are relocated in shadowJar to avoid conflicts
- [ ] **WEB-05**: Static files (HTML/JS/CSS) are served from plugin JAR

### Authentication

- [ ] **AUTH-01**: Web panel requires password from config.yml to access
- [ ] **AUTH-02**: Password is hashed in config (not stored in plaintext)

### Collection CRUD

- [ ] **CRUD-01**: Admin can view list of all collections in web panel
- [ ] **CRUD-02**: Admin can view details of a single collection
- [ ] **CRUD-03**: Admin can create a new collection from scratch
- [ ] **CRUD-04**: Admin can edit any field of an existing collection
- [ ] **CRUD-05**: Admin can delete a collection (with confirmation prompt)
- [ ] **CRUD-06**: YAML validation prevents saving invalid configurations
- [ ] **CRUD-07**: Validation errors display clear messages with field locations

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

- [ ] **INT-01**: "Reload" button applies changes to running server without restart
- [ ] **INT-02**: Connection status indicator shows if web panel is connected to server
- [ ] **INT-03**: All Bukkit API calls from web handlers execute on main thread

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
| WEB-01 | TBD | Pending |
| WEB-02 | TBD | Pending |
| WEB-03 | TBD | Pending |
| WEB-04 | TBD | Pending |
| WEB-05 | TBD | Pending |
| AUTH-01 | TBD | Pending |
| AUTH-02 | TBD | Pending |
| CRUD-01 | TBD | Pending |
| CRUD-02 | TBD | Pending |
| CRUD-03 | TBD | Pending |
| CRUD-04 | TBD | Pending |
| CRUD-05 | TBD | Pending |
| CRUD-06 | TBD | Pending |
| CRUD-07 | TBD | Pending |
| VB-01 | TBD | Pending |
| VB-02 | TBD | Pending |
| VB-03 | TBD | Pending |
| VB-04 | TBD | Pending |
| VB-05 | TBD | Pending |
| VB-06 | TBD | Pending |
| VB-07 | TBD | Pending |
| VBE-01 | TBD | Pending |
| VBE-02 | TBD | Pending |
| VBE-03 | TBD | Pending |
| VBE-04 | TBD | Pending |
| VBE-05 | TBD | Pending |
| INT-01 | TBD | Pending |
| INT-02 | TBD | Pending |
| INT-03 | TBD | Pending |
| DOC-01 | TBD | Pending |
| DOC-02 | TBD | Pending |
| DOC-03 | TBD | Pending |

**Coverage:**
- v1.3 requirements: 32 total
- Mapped to phases: 0
- Unmapped: 32

---
*Requirements defined: 2026-01-23*
*Last updated: 2026-01-23 after initial definition*
