# Requirements: Collections Plugin v1.1

**Defined:** 2026-01-22
**Core Value:** Every player interaction must work correctly - collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## v1.1 Requirements

Requirements for operational features milestone. Each maps to roadmap phases.

### Notifications

- [ ] **NOTIF-01**: Player sees actionbar message when collecting a new item ("1/5 in Forest Collection")
- [ ] **NOTIF-02**: Player sees title/subtitle when completing a collection
- [ ] **NOTIF-03**: Player hears sound effect on collection completion
- [ ] **NOTIF-04**: Notification style is configurable in config.yml (actionbar/chat/title)
- [ ] **NOTIF-05**: Duplicate collection attempts do not spam notifications

### Admin Commands

- [ ] **ADMIN-01**: Admin can force-complete a collection for any player
- [ ] **ADMIN-02**: Force-complete optionally grants collection rewards
- [ ] **ADMIN-03**: Admin can inspect any player's collection progress
- [ ] **ADMIN-04**: Admin commands work on offline players (by name or UUID)
- [ ] **ADMIN-05**: Admin actions are logged with timestamp and executor

### Export/Import

- [ ] **EXPORT-01**: Admin can export a single player's data to JSON file
- [ ] **EXPORT-02**: Admin can export all player data to JSON file (streaming)
- [ ] **EXPORT-03**: Admin can import player data from JSON file
- [ ] **EXPORT-04**: Import validates JSON structure before applying
- [ ] **EXPORT-05**: Import supports dry-run mode (preview without applying)
- [ ] **EXPORT-06**: Import handles cache invalidation for online players

### Metrics

- [ ] **METRICS-01**: Plugin reports to bStats community metrics
- [ ] **METRICS-02**: Internal counters track collections completed
- [ ] **METRICS-03**: Internal counters track items collected
- [ ] **METRICS-04**: Internal counters track spawn success/failure rates
- [ ] **METRICS-05**: PlaceholderAPI integration for player stats
- [ ] **METRICS-06**: PlaceholderAPI integration for server-wide stats

## Future Requirements (v1.2+)

### Advanced Observability

- **OBS-01**: Prometheus metrics endpoint
- **OBS-02**: Per-collection completion rate tracking
- **OBS-03**: Spawn heatmap data export

### Advanced Notifications

- **NOTIF-06**: Boss bar for active collection tracking
- **NOTIF-07**: Milestone notifications (25%, 50%, 75%)

### Advanced Admin

- **ADMIN-06**: Batch operations (complete all collections for player)
- **ADMIN-07**: Confirmation prompts for destructive operations
- **ADMIN-08**: Undo recent admin action

## Out of Scope

| Feature | Reason |
|---------|--------|
| Real-time cross-server sync | MySQL shared state sufficient; Redis adds complexity |
| Web dashboard | Out of scope for plugin; use bStats dashboard |
| Discord integration | Better handled by dedicated Discord plugins |
| Prometheus endpoint | Deferred to v1.2 for operators with monitoring infra |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| NOTIF-01 | Phase 10 | Pending |
| NOTIF-02 | Phase 10 | Pending |
| NOTIF-03 | Phase 10 | Pending |
| NOTIF-04 | Phase 10 | Pending |
| NOTIF-05 | Phase 10 | Pending |
| ADMIN-01 | Phase 11 | Pending |
| ADMIN-02 | Phase 11 | Pending |
| ADMIN-03 | Phase 11 | Pending |
| ADMIN-04 | Phase 11 | Pending |
| ADMIN-05 | Phase 11 | Pending |
| EXPORT-01 | Phase 13 | Pending |
| EXPORT-02 | Phase 13 | Pending |
| EXPORT-03 | Phase 13 | Pending |
| EXPORT-04 | Phase 13 | Pending |
| EXPORT-05 | Phase 13 | Pending |
| EXPORT-06 | Phase 13 | Pending |
| METRICS-01 | Phase 12 | Pending |
| METRICS-02 | Phase 12 | Pending |
| METRICS-03 | Phase 12 | Pending |
| METRICS-04 | Phase 12 | Pending |
| METRICS-05 | Phase 12 | Pending |
| METRICS-06 | Phase 12 | Pending |

**Coverage:**
- v1.1 requirements: 22 total
- Mapped to phases: 22
- Unmapped: 0

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-22 after roadmap creation*
