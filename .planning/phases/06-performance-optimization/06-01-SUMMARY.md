---
phase: 06-performance-optimization
plan: 01
subsystem: performance
tags: [particles, spatial-indexing, chunk-lookup, ParticleBuilder]

# Dependency graph
requires:
  - phase: 05-entity-management
    provides: Entity tracking and lifecycle management
provides:
  - Chunk-based spatial index for O(1) collectible lookups
  - Player-centric particle iteration with chunk radius
  - ParticleBuilder API usage for efficient particle spawning
affects: [07-testing, 08-multi-server, 09-production]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Bit-packed chunk coordinates (long key from chunkX/chunkZ)
    - ConcurrentHashMap.newKeySet() for thread-safe chunk sets
    - Player-first iteration with spatial culling

key-files:
  created: []
  modified:
    - src/main/java/com/blockworlds/collections/manager/SpawnManager.java
    - src/main/java/com/blockworlds/collections/task/ParticleTask.java

key-decisions:
  - "Bit-packed chunk key: high 32 bits = chunkX, low 32 bits = chunkZ"
  - "Keep unspawned collectibles in chunk index for chunk load lookup"
  - "Only unindex on permanent removal (despawn with removeFromDatabase=true)"
  - "PARTICLE_CHUNK_RADIUS = 2 (32 blocks, matching typical particle visibility)"

patterns-established:
  - "Chunk-based spatial index pattern for O(1) area lookups"
  - "Player-first iteration with chunk radius culling"
  - "ParticleBuilder API for targeted particle delivery"

# Metrics
duration: 8min
completed: 2026-01-21
---

# Phase 6 Plan 1: Particle Task Optimization Summary

**Chunk-based spatial index reduces particle task complexity from O(players x collectibles) to O(players x nearby_chunks x avg_per_chunk)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-01-21T15:00:00Z
- **Completed:** 2026-01-21T15:08:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added chunk-based spatial index (collectiblesByChunk) to SpawnManager for O(1) lookups
- Implemented chunkKey() bit-packing for efficient chunk coordinate storage
- Rewrote ParticleTask to iterate players first with chunk-radius culling
- Converted all particle spawning to Paper's ParticleBuilder API

## Task Commits

Each task was committed atomically:

1. **Task 1: Add chunk-based spatial index to SpawnManager** - `612e33c` (perf)
2. **Task 2: Rewrite ParticleTask to use chunk lookups and ParticleBuilder** - `63ad49a` (perf)

## Files Created/Modified
- `src/main/java/com/blockworlds/collections/manager/SpawnManager.java` - Added collectiblesByChunk Map, chunkKey(), indexCollectible(), unindexCollectible(), getCollectiblesNearChunk()
- `src/main/java/com/blockworlds/collections/task/ParticleTask.java` - Rewrote spawnParticles() with player-first iteration, ParticleBuilder API

## Decisions Made
- **Bit-packed chunk key:** Standard pattern using high 32 bits for chunkX, low 32 bits for chunkZ provides unique long key per chunk
- **Unspawned collectibles stay indexed:** Allows chunk load to find them via getCollectiblesNearChunk without re-indexing
- **Unindex only on permanent removal:** despawnCollectible with removeFromDatabase=true triggers unindex; temporary unspawn does not
- **PARTICLE_CHUNK_RADIUS = 2:** 5x5 chunk grid (25 lookups) covers 80x80 block area, sufficient for 32-block particle distance

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Particle optimization complete (PERF-01 satisfied)
- Ready for spawn finder optimization (06-02) or batch database operations
- Chunk index can be reused for future spatial queries

---
*Phase: 06-performance-optimization*
*Completed: 2026-01-21*
