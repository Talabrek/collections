---
phase: 20
plan: 02
subsystem: web-api
tags: [crud, endpoints, validation, yaml-writer]
requires: [20-01]
provides: [collection-crud-api, reload-endpoint]
affects: [20-03]
tech-stack:
  added: []
  patterns: ["error translation via RuntimeException messages", "path traversal prevention"]
key-files:
  created: []
  modified:
    - src/main/java/com/blockworlds/collections/web/api/CollectionsController.java
decisions:
  - id: CRUD-01
    decision: Use RuntimeException message prefix convention for main thread error translation
    rationale: Allows clean error propagation from main thread Runnable to HTTP response codes without checked exceptions
metrics:
  duration: 5 min
  completed: 2026-01-23
---

# Phase 20 Plan 02: CRUD Endpoints Summary

Full CRUD capability on /api/collections with validation, file persistence, and hot reload.

## What Was Built

### CRUD Endpoints

- **POST /api/collections**: Creates new collection
  - Parses CollectionRequest from JSON body
  - Validates via CollectionValidator (returns 400 with ValidationErrorResponse if invalid)
  - Checks for duplicate ID (returns 409 Conflict if exists)
  - Writes YAML file via CollectionYamlWriter
  - Reloads CollectionManager
  - Returns 201 Created with `{"success": true, "id": "..."}`

- **PUT /api/collections/{id}**: Updates existing collection
  - Validates body ID matches path (or body ID can be null)
  - Validates via CollectionValidator
  - Checks collection exists (returns 404 if not)
  - Overwrites YAML file
  - Reloads CollectionManager
  - Returns 200 OK with `{"success": true, "id": "..."}`

- **DELETE /api/collections/{id}**: Removes collection
  - Validates ID format via isValidCollectionId (path traversal prevention)
  - Checks collection exists (returns 404 if not)
  - Deletes YAML file
  - Reloads CollectionManager
  - Returns 204 No Content

- **POST /api/reload**: Hot reload collections
  - Triggers CollectionManager.loadCollections()
  - Returns 200 OK with `{"success": true, "collectionCount": N}`

### Security Measures

- **Path traversal prevention**: isValidCollectionId() ensures IDs match `^[a-z0-9_]+$`
- **ID validation**: Rejects malicious patterns like `../../../config`
- **Thread safety**: All file I/O via mainThreadBridge.runSyncAndWait()

### Error Handling

- handleMainThreadError() translates RuntimeException messages to HTTP responses:
  - `CONFLICT:{id}` -> 409 ConflictResponse
  - `NOT_FOUND:{id}` -> 404 NotFoundResponse
  - `IO_ERROR` -> 500 InternalServerErrorResponse
  - Timeout/other -> 500 "Server busy"

## Commits

| Hash | Message |
|------|---------|
| 5c88e41 | feat(20-02): add CRUD endpoints to CollectionsController |

## Verification

- [x] `./gradlew classes` compiles successfully
- [x] CollectionsController imports CollectionValidator, CollectionYamlWriter
- [x] All four new routes registered in register() method
- [x] Thread safety: All file I/O wrapped in mainThreadBridge.runSyncAndWait()
- [x] POST /api/collections returns 201 on success
- [x] PUT /api/collections/{id} returns 200 on success
- [x] DELETE /api/collections/{id} returns 204 on success
- [x] POST /api/reload returns collection count
- [x] Validation errors return 400 with ValidationErrorResponse
- [x] Duplicate ID returns 409 Conflict
- [x] Not found returns 404
- [x] Path traversal attempt returns 400

## Deviations from Plan

None - plan executed exactly as written. Tasks 1 and 2 were combined into a single commit since the isValidCollectionId helper was naturally part of the endpoint implementation.

## Next Phase Readiness

Ready for 20-03 (Frontend Forms):
- All CRUD endpoints operational
- Validation returns field-specific errors for form display
- Reload endpoint enables live testing
