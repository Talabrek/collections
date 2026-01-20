# Requirements: Collections Plugin Audit

**Defined:** 2026-01-20
**Core Value:** Every player interaction must work correctly — collecting items, tracking progress, and claiming rewards cannot lose data or behave unexpectedly.

## v1 Requirements

Requirements for this audit cycle. Each maps to roadmap phases.

### Data Integrity

- [x] **DATA-01**: Player quit saves must block with timeout to prevent data loss
- [x] **DATA-02**: SQLite must use WAL mode and busy_timeout for concurrent access
- [x] **DATA-03**: Player data saves must be wrapped in transactions
- [x] **DATA-04**: All CompletableFuture chains must have exception handlers that log at SEVERE level
- [ ] **DATA-05**: Implement MySQL storage option for multi-server networks
- [ ] **DATA-06**: Add configuration to switch between SQLite and MySQL storage backends

### Concurrency

- [x] **CONC-01**: Fix race condition where getProgress() returns null for recently joined players
- [x] **CONC-02**: Verify player data load completes before any feature access
- [x] **CONC-03**: Migrate all BukkitScheduler usage to Folia-compatible schedulers
- [x] **CONC-04**: Ensure all ConcurrentHashMap operations use atomic methods (computeIfAbsent, etc.)
- [x] **CONC-05**: Verify PlayerProgress internal HashMap thread safety

### GUI

- [x] **GUI-01**: Cancel all click types in GUIs (shift-click, number keys, drag, double-click)
- [x] **GUI-02**: Re-fetch player progress before any mutation in GUI handlers
- [x] **GUI-03**: Handle inventory full edge case when claiming rewards
- [x] **GUI-04**: Prevent reward claiming if progress changed since GUI opened
- [x] **GUI-05**: Verify GUI state consistency after async operations

### Performance

- [ ] **PERF-01**: Optimize particle task from O(players x collectibles) to chunk-based lookup
- [ ] **PERF-02**: Add Map<UUID, UUID> index for entityId -> collectibleId lookup
- [ ] **PERF-03**: Implement batch inserts for database writes
- [ ] **PERF-04**: Replace pre-allocated grid points with lazy iteration in AdaptiveSpawnFinder

### Memory

- [ ] **MEM-01**: Fix cooldown map memory leak (verify cleanup on all quit paths)
- [ ] **MEM-02**: Verify all scheduled tasks cancelled in onDisable()
- [ ] **MEM-03**: Verify all per-player Maps cleaned on PlayerQuitEvent
- [ ] **MEM-04**: Audit for Player object retention (should use UUID instead)

### Entity Management

- [ ] **ENT-01**: Verify chunk load/unload correctly recreates/tracks entity state
- [ ] **ENT-02**: Handle entities despawning without notification
- [ ] **ENT-03**: Ensure collectible tracking map stays in sync with world entities

### Code Quality

- [ ] **CODE-01**: Remove dead stub file at com.example.collections package
- [ ] **CODE-02**: Fix saveDefaultCollections() to extract all collection YAML files
- [ ] **CODE-03**: Add alphanumeric validation for collection/item IDs
- [ ] **CODE-04**: Extract duplicated spawn condition parsing to shared utility
- [ ] **CODE-05**: Extract duplicated surface location finding to LocationUtils

### Testing

- [ ] **TEST-01**: Add unit tests for SpawnConditions validation
- [ ] **TEST-02**: Add unit tests for PlayerDataManager lifecycle
- [ ] **TEST-03**: Verify existing tests pass after all changes

## v2 Requirements

Deferred to future releases. Tracked but not in current roadmap.

### Features

- **FEAT-01**: Add data export/import command for server migration
- **FEAT-02**: Add progress notification system ("1/5 collected")
- **FEAT-03**: Add admin force-complete command

### Observability

- **OBS-01**: Add metrics collection for spawn success rates
- **OBS-02**: Add performance monitoring integration

## Out of Scope

Explicitly excluded from this audit.

| Feature | Reason |
|---------|--------|
| New gameplay features | Audit only, no feature additions |
| UI redesign | Functionality focus only |
| Refactoring for style | Only fix functional issues |
| PostgreSQL support | MySQL sufficient for network deployment |
| Redis caching layer | Adds complexity, MySQL sufficient for v1 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-03 | Phase 1 | Complete |
| DATA-04 | Phase 1 | Complete |
| DATA-05 | Phase 8 | Pending |
| DATA-06 | Phase 8 | Pending |
| CONC-01 | Phase 2 | Complete |
| CONC-02 | Phase 2 | Complete |
| CONC-03 | Phase 2 | Complete |
| CONC-04 | Phase 2 | Complete |
| CONC-05 | Phase 2 | Complete |
| GUI-01 | Phase 3 | Complete |
| GUI-02 | Phase 3 | Complete |
| GUI-03 | Phase 3 | Complete |
| GUI-04 | Phase 3 | Complete |
| GUI-05 | Phase 3 | Complete |
| PERF-01 | Phase 6 | Pending |
| PERF-02 | Phase 6 | Pending |
| PERF-03 | Phase 6 | Pending |
| PERF-04 | Phase 6 | Pending |
| MEM-01 | Phase 4 | Pending |
| MEM-02 | Phase 4 | Pending |
| MEM-03 | Phase 4 | Pending |
| MEM-04 | Phase 4 | Pending |
| ENT-01 | Phase 5 | Pending |
| ENT-02 | Phase 5 | Pending |
| ENT-03 | Phase 5 | Pending |
| CODE-01 | Phase 7 | Pending |
| CODE-02 | Phase 7 | Pending |
| CODE-03 | Phase 7 | Pending |
| CODE-04 | Phase 7 | Pending |
| CODE-05 | Phase 7 | Pending |
| TEST-01 | Phase 9 | Pending |
| TEST-02 | Phase 9 | Pending |
| TEST-03 | Phase 9 | Pending |

**Coverage:**
- v1 requirements: 33 total
- Mapped to phases: 33
- Unmapped: 0

---
*Requirements defined: 2026-01-20*
*Last updated: 2026-01-21 after Phase 3 completion*
