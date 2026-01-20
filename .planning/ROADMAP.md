# Roadmap: Collections Plugin Audit

## Overview

This audit systematically hardens the Collections plugin for multi-server network deployment. Work is ordered by risk: critical data integrity issues first (player quit saves, exception handling), then concurrency and GUI safety, followed by memory and entity management, and finally performance optimization and MySQL support. Each phase builds on the stability established by previous phases.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Data Integrity Hardening** - Fix critical data loss vectors (saves, exceptions, SQLite config)
- [x] **Phase 2: Concurrency Safety** - Eliminate race conditions in player data access
- [ ] **Phase 3: GUI Safety** - Prevent click exploits and state corruption
- [ ] **Phase 4: Memory Management** - Fix leaks and cleanup on quit/disable
- [ ] **Phase 5: Entity Management** - Correct chunk load/unload entity handling
- [ ] **Phase 6: Performance Optimization** - Optimize particle systems and database operations
- [ ] **Phase 7: Code Quality** - Remove dead code, extract utilities, add validation
- [ ] **Phase 8: MySQL Implementation** - Add MySQL storage backend for multi-server
- [ ] **Phase 9: Testing & Verification** - Add tests and verify all changes work

## Phase Details

### Phase 1: Data Integrity Hardening
**Goal**: Player data cannot be lost due to quit timing, exceptions, or database issues
**Depends on**: Nothing (first phase)
**Requirements**: DATA-01, DATA-02, DATA-03, DATA-04
**Success Criteria** (what must be TRUE):
  1. Player quitting during save operation does not lose progress
  2. All async database errors are logged at SEVERE level with full stack traces
  3. SQLite busy errors do not occur under normal concurrent access
  4. Partial saves cannot leave player data in inconsistent state
**Plans**: 3 plans

Plans:
- [x] 01-01-PLAN.md — Player quit save blocking and timeout handling (DATA-01)
- [x] 01-02-PLAN.md — SQLite WAL mode, busy timeout, and transaction wrapping (DATA-02, DATA-03)
- [x] 01-03-PLAN.md — CompletableFuture exception handler audit (DATA-04)

### Phase 2: Concurrency Safety
**Goal**: Player data access is race-free from join to quit
**Depends on**: Phase 1 (data layer must be stable)
**Requirements**: CONC-01, CONC-02, CONC-03, CONC-04, CONC-05
**Success Criteria** (what must be TRUE):
  1. Player interacting immediately after join sees correct progress (no null)
  2. GUIs and commands block until player data is loaded
  3. All scheduler usage is Folia-compatible
  4. Concurrent map operations use atomic methods
  5. PlayerProgress internal state is thread-safe
**Plans**: 3 plans

Plans:
- [x] 02-01-PLAN.md — Fix getProgress() race condition and add load-blocking gate (CONC-01, CONC-02)
- [x] 02-02-PLAN.md — Migrate BukkitScheduler to Folia-compatible schedulers (CONC-03)
- [x] 02-03-PLAN.md — Make PlayerProgress internal state thread-safe (CONC-04, CONC-05)

### Phase 3: GUI Safety
**Goal**: GUI interactions cannot duplicate items or corrupt state
**Depends on**: Phase 2 (data access must be race-free)
**Requirements**: GUI-01, GUI-02, GUI-03, GUI-04, GUI-05
**Success Criteria** (what must be TRUE):
  1. Shift-click, number keys, drag, and double-click cannot extract or move items
  2. Reward claiming checks current progress, not stale GUI state
  3. Inventory full during reward claim drops items or queues them (no loss)
  4. Concurrent collection while GUI open does not cause double rewards
  5. GUI displays consistent state after async operations complete
**Plans**: 3 plans

Plans:
- [x] 03-01-PLAN.md — Cancel all click types in InventoryClickEvent handlers (GUI-01)
- [x] 03-02-PLAN.md — Re-fetch progress before mutations and handle inventory full (GUI-02, GUI-03)
- [ ] 03-03-PLAN.md — Add state versioning and double-claim prevention (GUI-04, GUI-05)

### Phase 4: Memory Management
**Goal**: Plugin does not leak memory during extended operation
**Depends on**: Phase 2 (scheduler and cleanup paths must be correct)
**Requirements**: MEM-01, MEM-02, MEM-03, MEM-04
**Success Criteria** (what must be TRUE):
  1. Cooldown map does not grow unbounded (verified after 100+ quit/joins)
  2. All scheduled tasks are cancelled in onDisable()
  3. No per-player data remains in memory after quit
  4. No Player object references stored (UUIDs only)
**Plans**: TBD

Plans:
- [ ] 04-01: Audit and fix cooldown map cleanup
- [ ] 04-02: Verify task cancellation in onDisable and player quit
- [ ] 04-03: Audit all per-player maps and Player object references

### Phase 5: Entity Management
**Goal**: Collectible entities are correctly tracked across chunk events
**Depends on**: Phase 4 (memory cleanup must be correct)
**Requirements**: ENT-01, ENT-02, ENT-03
**Success Criteria** (what must be TRUE):
  1. Chunk unload removes entities from tracking map
  2. Chunk load recreates collectibles that were previously spawned
  3. Entity despawn (any cause) correctly updates tracking map
  4. No orphaned tracking entries (entity gone but tracked)
  5. No orphaned entities (entity exists but not tracked)
**Plans**: TBD

Plans:
- [ ] 05-01: Audit chunk load/unload handlers and entity tracking sync
- [ ] 05-02: Handle entity despawn without notification

### Phase 6: Performance Optimization
**Goal**: Plugin performs well at network scale (50+ concurrent players)
**Depends on**: Phase 5 (correctness must be established first)
**Requirements**: PERF-01, PERF-02, PERF-03, PERF-04
**Success Criteria** (what must be TRUE):
  1. Particle task scales with loaded chunks, not O(players x collectibles)
  2. Entity ID to collectible lookup is O(1), not O(n)
  3. Bulk database operations use batch inserts
  4. Spawn finder does not allocate thousands of temporary Location objects
**Plans**: TBD

Plans:
- [ ] 06-01: Optimize particle iteration to chunk-based lookup
- [ ] 06-02: Add entity UUID to collectible ID index
- [ ] 06-03: Implement batch database inserts
- [ ] 06-04: Replace grid point allocation with lazy iteration

### Phase 7: Code Quality
**Goal**: Codebase is clean and maintainable
**Depends on**: Phase 6 (core functionality complete)
**Requirements**: CODE-01, CODE-02, CODE-03, CODE-04, CODE-05
**Success Criteria** (what must be TRUE):
  1. No dead code or stub files in the repository
  2. All collection YAML files extracted on first run
  3. Invalid collection/item IDs rejected with clear error message
  4. Spawn condition parsing exists in one location
  5. Surface location finding exists in one location
**Plans**: TBD

Plans:
- [ ] 07-01: Remove dead stub file
- [ ] 07-02: Fix saveDefaultCollections to extract all YAML files
- [ ] 07-03: Add ID validation and extract duplicated utilities

### Phase 8: MySQL Implementation
**Goal**: Plugin supports MySQL for multi-server network deployment
**Depends on**: Phase 1 (SQLite layer must be stable as reference)
**Requirements**: DATA-05, DATA-06
**Success Criteria** (what must be TRUE):
  1. MySQL storage option works identically to SQLite for all operations
  2. Configuration clearly switches between SQLite and MySQL
  3. Connection pool handles network-scale load (50+ concurrent)
  4. Existing SQLite data can be migrated to MySQL (documented process)
**Plans**: TBD

Plans:
- [ ] 08-01: Design storage abstraction layer
- [ ] 08-02: Implement MySQL storage backend
- [ ] 08-03: Add configuration and connection pooling
- [ ] 08-04: Document migration process

### Phase 9: Testing & Verification
**Goal**: All changes are verified working
**Depends on**: All previous phases
**Requirements**: TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. SpawnConditions unit tests pass
  2. PlayerDataManager lifecycle unit tests pass
  3. All existing tests pass
  4. Manual verification checklist completed on dev server
**Plans**: TBD

Plans:
- [ ] 09-01: Add SpawnConditions and PlayerDataManager unit tests
- [ ] 09-02: Run full test suite and fix any failures
- [ ] 09-03: Manual verification on dev server

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Data Integrity Hardening | 3/3 | Complete | 2026-01-21 |
| 2. Concurrency Safety | 3/3 | Complete | 2026-01-21 |
| 3. GUI Safety | 2/3 | In progress | - |
| 4. Memory Management | 0/3 | Not started | - |
| 5. Entity Management | 0/2 | Not started | - |
| 6. Performance Optimization | 0/4 | Not started | - |
| 7. Code Quality | 0/3 | Not started | - |
| 8. MySQL Implementation | 0/4 | Not started | - |
| 9. Testing & Verification | 0/3 | Not started | - |
