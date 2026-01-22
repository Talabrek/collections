# Requirements: Collections Plugin v1.2

**Defined:** 2026-01-23
**Core Value:** Every player interaction must work correctly — collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## v1.2 Requirements

Requirements for v1.2 Enhanced Collection UX. Each maps to roadmap phases.

### Visibility

- [ ] **VIS-01**: Uncommon collectibles completely invisible without normal collector's helmet
- [ ] **VIS-02**: Rare+ collectibles completely invisible without upgraded collector's helmet
- [ ] **VIS-03**: Common collectibles always visible regardless of helmet

### Radar

- [ ] **RADAR-01**: Boss bar displays nearby collectibles when wearing collector's helmet
- [ ] **RADAR-02**: Normal helmet radar detects common and uncommon collectibles
- [ ] **RADAR-03**: Upgraded helmet radar detects all collectible tiers
- [ ] **RADAR-04**: Boss bar radar hidden when not wearing any collector's helmet

### Add Flow UX

- [ ] **UX-01**: Right-clicking collectible opens full collection grid showing all slots
- [ ] **UX-02**: Add screen displays progress summary (X/Y collected)
- [ ] **UX-03**: Add screen includes Yes/No confirmation buttons
- [ ] **UX-04**: After confirming add, GUI transitions to show the collection

### Notifications

- [ ] **NOTIF-01**: Player receives notification at 25% collection progress
- [ ] **NOTIF-02**: Player receives notification at 50% collection progress
- [ ] **NOTIF-03**: Player receives notification at 75% collection progress

## Future Requirements (v1.3+)

Deferred to future milestone. Not in current roadmap.

### Observability

- **OBS-01**: Prometheus metrics endpoint
- **OBS-02**: Per-collection completion rate tracking
- **OBS-03**: Spawn heatmap data export

### Admin UX

- **ADMIN-06**: Batch admin operations
- **ADMIN-07**: Confirmation prompts for destructive operations
- **ADMIN-08**: Undo recent admin action

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Real-time cross-server sync | MySQL shared state sufficient; Redis adds complexity |
| Web dashboard | Out of scope for plugin; use bStats dashboard |
| Discord integration | Better handled by dedicated Discord plugins |
| PostgreSQL support | MySQL sufficient for network deployment |
| Active collection tracking boss bar | v1.2 uses radar instead; tracking could be v1.3+ |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| VIS-01 | TBD | Pending |
| VIS-02 | TBD | Pending |
| VIS-03 | TBD | Pending |
| RADAR-01 | TBD | Pending |
| RADAR-02 | TBD | Pending |
| RADAR-03 | TBD | Pending |
| RADAR-04 | TBD | Pending |
| UX-01 | TBD | Pending |
| UX-02 | TBD | Pending |
| UX-03 | TBD | Pending |
| UX-04 | TBD | Pending |
| NOTIF-01 | TBD | Pending |
| NOTIF-02 | TBD | Pending |
| NOTIF-03 | TBD | Pending |

**Coverage:**
- v1.2 requirements: 14 total
- Mapped to phases: 0
- Unmapped: 14 (pending roadmap)

---
*Requirements defined: 2026-01-23*
*Last updated: 2026-01-23 after initial definition*
