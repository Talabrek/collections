---
phase: 20
plan: 01
subsystem: web-api
tags: [dto, validation, yaml, serialization]
requires: [19-02]
provides: [request-dtos, field-validation, yaml-writer]
affects: [20-02, 20-03]
tech-stack:
  added: []
  patterns: ["record DTOs", "field-level validation", "path notation errors"]
key-files:
  created:
    - src/main/java/com/blockworlds/collections/web/api/dto/CollectionRequest.java
    - src/main/java/com/blockworlds/collections/web/api/dto/ItemRequest.java
    - src/main/java/com/blockworlds/collections/web/api/dto/RewardRequest.java
    - src/main/java/com/blockworlds/collections/web/api/dto/FieldError.java
    - src/main/java/com/blockworlds/collections/web/api/dto/ValidationErrorResponse.java
    - src/main/java/com/blockworlds/collections/web/api/CollectionValidator.java
    - src/main/java/com/blockworlds/collections/web/api/CollectionYamlWriter.java
  modified: []
decisions:
  - id: DTO-01
    decision: Use Java records for request DTOs with nullable types for optional fields
    rationale: Records provide immutable data transfer, nullable allows Gson to handle missing JSON fields
  - id: VAL-01
    decision: Use isValid instead of valid for ValidationResult record field
    rationale: Avoids conflict with static valid() factory method in Java records
  - id: YAML-01
    decision: Only write optional fields when they have values
    rationale: Produces cleaner YAML files matching existing collection file style
metrics:
  duration: 6 min
  completed: 2026-01-23
---

# Phase 20 Plan 01: Request DTOs and Validation Summary

Request DTOs with field-level validation and YAML serialization for collection write operations.

## What Was Built

### Request DTOs (5 records)
- **CollectionRequest**: Main DTO accepting id, name, description, tier, icon, items, rewards, zones, requires
- **ItemRequest**: Nested DTO for collection item data (id, name, material, lore, weight, soulbound)
- **RewardRequest**: Nested DTO for reward configuration (experience, commands, message, fireworks)
- **FieldError**: Structured field error with path notation (e.g., "items[0].material")
- **ValidationErrorResponse**: RFC 7807 inspired response with type, title, status, errors list

### CollectionValidator
- Validates id format: `^[a-z0-9_]+$` (prevents path traversal)
- Validates name required, non-blank
- Validates tier against CollectibleTier enum (case-insensitive)
- Validates icon against Material enum (case-insensitive)
- Validates items array: at least 1 item required
- Validates nested items with path notation: `items[0].material`, `items[1].name`
- Returns `ValidationResult(isValid, errors)` record

### CollectionYamlWriter
- Converts CollectionRequest to YamlConfiguration
- Writes items as nested map: `items.{item_id}.name`
- Handles null/empty optional fields gracefully
- Produces YAML compatible with `CollectionManager.loadCollectionFile()`
- Uses `yaml.save(file)` for atomic file writes

## Commits

| Hash | Message |
|------|---------|
| 6401fed | feat(20-01): add request DTOs for collection write operations |
| a7ba80d | feat(20-01): add CollectionValidator with field-level error reporting |
| 3a49fe8 | feat(20-01): add CollectionYamlWriter for YAML file serialization |

## Verification

- [x] `./gradlew classes` compiles all new code
- [x] DTO records have correct field types and nullability
- [x] CollectionValidator produces FieldError with proper field paths
- [x] CollectionYamlWriter uses YamlConfiguration correctly

## Deviations from Plan

None - plan executed exactly as written.

## Next Phase Readiness

Ready for 20-02 (CRUD Endpoints):
- CollectionRequest DTO ready for POST/PUT body parsing
- CollectionValidator ready for request validation
- CollectionYamlWriter ready for file persistence
- ValidationErrorResponse ready for 400 error responses
