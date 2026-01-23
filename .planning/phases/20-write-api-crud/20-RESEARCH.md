# Phase 20: Write API + CRUD - Research

**Researched:** 2026-01-23
**Domain:** REST API write operations (POST/PUT/DELETE) with YAML validation for Minecraft plugin admin panel
**Confidence:** HIGH

## Summary

Phase 20 implements write operations for the Collections admin web panel. This builds on Phase 19's read-only API by adding POST, PUT, and DELETE endpoints for collection management, plus YAML validation with clear error messages.

The primary technical challenges are:
1. **YAML Validation:** Validating user-submitted YAML against the collection schema, returning line/field-specific errors
2. **Thread Safety:** All write operations must use MainThreadBridge since CollectionManager is not thread-safe
3. **File I/O:** Writing YAML files to `plugins/Collections/collections/` directory
4. **Hot Reload:** Triggering `CollectionManager.reload()` after changes without server restart
5. **Frontend Forms:** Building form UI for collection editing with validation feedback

**Primary recommendation:** Use Bukkit's YamlConfiguration for parsing/validation (already used for loading), return structured error responses with field paths, and implement a dedicated CRUD controller that mirrors the collection YAML schema as request DTOs.

---

## Standard Stack

### Core (Already in Place from Phases 18-19)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Javalin | 6.7.0 | REST API framework | Already integrated, POST/PUT/DELETE supported |
| YamlConfiguration | Paper bundled | YAML parsing/saving | Already used by CollectionManager |
| Gson | Paper bundled | JSON serialization | Already configured |
| MainThreadBridge | Custom | Thread-safe Bukkit access | Already implemented |

### Frontend (Vanilla JS - No New Dependencies)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vanilla JS | ES6+ | Form handling, validation display | No build step, matches Phase 19 |
| CSS | CSS3 | Form styling | Extends existing admin.css |

### No Additional Dependencies
Phase 20 requires NO new dependencies - all functionality can be achieved with existing stack.

---

## Architecture Patterns

### Backend: Request DTO Pattern for Write Operations

Create DTOs that mirror the YAML structure for incoming JSON requests. These are separate from the read DTOs (CollectionDetail, etc.) to allow different validation rules.

**Structure:**
```
web/api/dto/
├── CollectionSummary.java    # Existing - list response
├── CollectionDetail.java     # Existing - detail response
├── ItemSummary.java          # Existing - item response
├── RewardSummary.java        # Existing - reward response
├── CollectionCreateRequest.java  # NEW - create request body
├── CollectionUpdateRequest.java  # NEW - update request body
├── ValidationErrorResponse.java  # NEW - validation errors
```

**Request DTO Example:**
```java
// Used for both create (POST) and update (PUT) requests
public record CollectionRequest(
    String id,
    String name,
    String description,
    String tier,
    String icon,
    List<ItemRequest> items,
    RewardRequest rewards,
    List<String> zones,
    List<String> requires
) {}

public record ItemRequest(
    String id,
    String name,
    String material,
    List<String> lore,
    int weight,
    boolean soulbound
) {}

public record RewardRequest(
    int experience,
    List<String> commands,
    String message,
    boolean fireworks
) {}
```

### Backend: CRUD Controller Pattern

Extend CollectionsController with write endpoints, or create a dedicated CollectionsCrudController.

**Pattern (extending existing controller):**
```java
public void register(Javalin app) {
    // Existing read endpoints
    app.get("/api/collections", this::listCollections);
    app.get("/api/collections/{id}", this::getCollection);

    // NEW: Write endpoints
    app.post("/api/collections", this::createCollection);
    app.put("/api/collections/{id}", this::updateCollection);
    app.delete("/api/collections/{id}", this::deleteCollection);

    // NEW: Reload endpoint
    app.post("/api/reload", this::reloadCollections);
}
```

### Backend: Validation Error Response Pattern

Use RFC 7807-inspired error format for validation errors.

**Pattern:**
```java
public record ValidationErrorResponse(
    String type,           // "validation_error"
    String title,          // "Validation failed"
    int status,            // 400
    List<FieldError> errors
) {}

public record FieldError(
    String field,          // "items[0].material"
    String message,        // "Invalid material: INVALID_MAT"
    String code            // "invalid_enum"
) {}
```

**Example response:**
```json
{
    "type": "validation_error",
    "title": "Collection validation failed",
    "status": 400,
    "errors": [
        {
            "field": "tier",
            "message": "Invalid tier: SUPER. Must be one of: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY",
            "code": "invalid_enum"
        },
        {
            "field": "items[0].material",
            "message": "Unknown material: INVALID_BLOCK",
            "code": "invalid_material"
        }
    ]
}
```

### Backend: YAML Validation Pattern

Validate in two phases:
1. **Syntax:** YamlConfiguration.loadConfiguration() throws on invalid YAML syntax
2. **Schema:** Custom validation for Materials, Tiers, required fields, etc.

**Pattern:**
```java
public class CollectionValidator {

    private final List<FieldError> errors = new ArrayList<>();

    public ValidationResult validate(CollectionRequest request) {
        errors.clear();

        // Required fields
        if (request.id() == null || request.id().isBlank()) {
            errors.add(new FieldError("id", "Collection ID is required", "required"));
        } else if (!isValidId(request.id())) {
            errors.add(new FieldError("id", "ID must be lowercase alphanumeric with underscores", "invalid_format"));
        }

        if (request.name() == null || request.name().isBlank()) {
            errors.add(new FieldError("name", "Collection name is required", "required"));
        }

        // Enum validation
        if (request.tier() != null) {
            try {
                CollectibleTier.valueOf(request.tier().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("tier",
                    "Invalid tier: " + request.tier() + ". Must be one of: " +
                    Arrays.toString(CollectibleTier.values()), "invalid_enum"));
            }
        }

        // Material validation
        if (request.icon() != null) {
            try {
                Material.valueOf(request.icon().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("icon", "Unknown material: " + request.icon(), "invalid_material"));
            }
        }

        // Items validation
        if (request.items() == null || request.items().isEmpty()) {
            errors.add(new FieldError("items", "Collection must have at least one item", "required"));
        } else {
            for (int i = 0; i < request.items().size(); i++) {
                validateItem(request.items().get(i), "items[" + i + "]");
            }
        }

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    private void validateItem(ItemRequest item, String path) {
        if (item.id() == null || item.id().isBlank()) {
            errors.add(new FieldError(path + ".id", "Item ID is required", "required"));
        }
        if (item.name() == null || item.name().isBlank()) {
            errors.add(new FieldError(path + ".name", "Item name is required", "required"));
        }
        if (item.material() != null) {
            try {
                Material.valueOf(item.material().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError(path + ".material",
                    "Unknown material: " + item.material(), "invalid_material"));
            }
        }
        if (item.weight() <= 0) {
            errors.add(new FieldError(path + ".weight",
                "Weight must be positive, got: " + item.weight(), "invalid_value"));
        }
    }
}
```

### Backend: File Writing Pattern

Convert request DTO to YamlConfiguration and save to file.

**Pattern:**
```java
public void saveCollection(CollectionRequest request, File file) throws IOException {
    YamlConfiguration yaml = new YamlConfiguration();

    yaml.set("id", request.id());
    yaml.set("name", request.name());
    yaml.set("description", request.description());
    yaml.set("tier", request.tier().toUpperCase());
    yaml.set("icon", request.icon().toUpperCase());
    yaml.set("zones", request.zones());
    yaml.set("requires", request.requires());

    // Items section
    for (ItemRequest item : request.items()) {
        String itemPath = "items." + item.id();
        yaml.set(itemPath + ".name", item.name());
        yaml.set(itemPath + ".material", item.material().toUpperCase());
        yaml.set(itemPath + ".lore", item.lore());
        yaml.set(itemPath + ".weight", item.weight());
        yaml.set(itemPath + ".soulbound", item.soulbound());
    }

    // Rewards section
    if (request.rewards() != null) {
        yaml.set("rewards.experience", request.rewards().experience());
        yaml.set("rewards.commands", request.rewards().commands());
        yaml.set("rewards.message", request.rewards().message());
        yaml.set("rewards.fireworks", request.rewards().fireworks());
    }

    yaml.save(file);
}
```

### Backend: Thread-Safe CRUD Operations

All operations that touch files or CollectionManager must go through MainThreadBridge.

**Pattern:**
```java
private void createCollection(Context ctx) {
    CollectionRequest request = ctx.bodyAsClass(CollectionRequest.class);

    // Validate (can run on Jetty thread - no Bukkit API)
    ValidationResult result = validator.validate(request);
    if (!result.isValid()) {
        ctx.status(400);
        ctx.json(new ValidationErrorResponse("validation_error",
            "Collection validation failed", 400, result.errors()));
        return;
    }

    try {
        // File I/O and reload on main thread
        mainThreadBridge.runSyncAndWait(() -> {
            // Check if ID already exists
            if (plugin.getCollectionManager().hasCollection(request.id())) {
                throw new IllegalStateException("Collection already exists: " + request.id());
            }

            // Save to file
            File file = new File(getCollectionsFolder(), request.id() + ".yml");
            saveCollection(request, file);

            // Reload collections
            plugin.getCollectionManager().loadCollections();
        }, TIMEOUT_MS);

        ctx.status(201);
        ctx.json(Map.of("success", true, "id", request.id()));

    } catch (MainThreadBridge.MainThreadException e) {
        if (e.getCause() instanceof IllegalStateException) {
            throw new ConflictResponse(e.getCause().getMessage());
        }
        throw new InternalServerErrorResponse("Server busy");
    }
}
```

### Frontend: Form-Based Edit Pattern

Use structured forms with validation feedback, not raw YAML editing.

**Pattern (form HTML):**
```html
<form id="collection-form">
    <div class="form-group">
        <label for="name">Name</label>
        <input type="text" id="name" name="name" required>
        <span class="error-message" data-field="name"></span>
    </div>

    <div class="form-group">
        <label for="tier">Tier</label>
        <select id="tier" name="tier">
            <option value="COMMON">Common</option>
            <option value="UNCOMMON">Uncommon</option>
            <option value="RARE">Rare</option>
            <option value="EPIC">Epic</option>
            <option value="LEGENDARY">Legendary</option>
        </select>
        <span class="error-message" data-field="tier"></span>
    </div>

    <!-- Items list with add/remove -->
    <div class="items-section">
        <h3>Items</h3>
        <div id="items-list"></div>
        <button type="button" id="add-item" class="btn-secondary">+ Add Item</button>
    </div>

    <button type="submit" class="btn-primary">Save Collection</button>
</form>
```

**Pattern (JavaScript form handling):**
```javascript
async function saveCollection(formData) {
    const response = await fetch('/api/collections/' + formData.id, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData)
    });

    if (response.status === 400) {
        const errors = await response.json();
        displayValidationErrors(errors);
        return false;
    }

    if (!response.ok) {
        showError('Failed to save collection');
        return false;
    }

    showSuccess('Collection saved!');
    return true;
}

function displayValidationErrors(errorResponse) {
    // Clear previous errors
    document.querySelectorAll('.error-message').forEach(el => el.textContent = '');
    document.querySelectorAll('.form-group').forEach(el => el.classList.remove('has-error'));

    // Display field-specific errors
    for (const error of errorResponse.errors) {
        const errorEl = document.querySelector(`[data-field="${error.field}"]`);
        if (errorEl) {
            errorEl.textContent = error.message;
            errorEl.closest('.form-group')?.classList.add('has-error');
        }
    }
}
```

### Frontend: Delete Confirmation Pattern

**Pattern:**
```javascript
async function deleteCollection(id) {
    const confirmed = await showConfirmDialog(
        'Delete Collection',
        `Are you sure you want to delete "${id}"? This cannot be undone.`
    );

    if (!confirmed) return;

    const response = await fetch('/api/collections/' + encodeURIComponent(id), {
        method: 'DELETE'
    });

    if (!response.ok) {
        showError('Failed to delete collection');
        return;
    }

    showSuccess('Collection deleted');
    window.location.hash = ''; // Return to list
}
```

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| YAML parsing/writing | Custom string manipulation | YamlConfiguration.save() | Handles escaping, formatting, edge cases |
| Material validation | List of string constants | Material.valueOf() | Auto-updates with Minecraft versions |
| Thread synchronization | Raw CompletableFuture | MainThreadBridge | Already handles timeouts, exceptions |
| HTTP error responses | Manual ctx.status() + string | Javalin exception responses | Consistent JSON formatting |
| Form validation display | Manual DOM manipulation | Error element + data-field | Scalable, declarative pattern |
| Delete confirmation | window.confirm() | Custom modal dialog | Better UX, consistent styling |

**Key insight:** Bukkit's YamlConfiguration is the authoritative YAML handler for this plugin. It already handles all the parsing complexity in CollectionManager.loadCollections() - reuse the same library for writing.

---

## Common Pitfalls

### Pitfall 1: File Operations on Jetty Thread
**What goes wrong:** File I/O from Jetty threads can cause race conditions with CollectionManager
**Why it happens:** Jetty serves requests on its own thread pool; CollectionManager expects single-threaded access
**How to avoid:** Wrap ALL file operations AND CollectionManager.reload() in MainThreadBridge.runSyncAndWait()
**Warning signs:** Intermittent "file in use" errors, corrupted YAML files

### Pitfall 2: Overwriting Without Checking Existence
**What goes wrong:** Creating a collection with existing ID overwrites the existing file
**Why it happens:** Not checking hasCollection() before write
**How to avoid:** POST (create) should return 409 Conflict if ID exists; PUT (update) should return 404 if ID doesn't exist
**Warning signs:** Silent data loss, no feedback on create

### Pitfall 3: Validation After File Write
**What goes wrong:** Invalid data written to file, crashes on reload
**Why it happens:** Writing first, validating during reload
**How to avoid:** Validate ALL fields BEFORE any file I/O; validation runs on Jetty thread (faster)
**Warning signs:** Server crashes on reload, partially written files

### Pitfall 4: Exposing File System Paths in Errors
**What goes wrong:** Security risk - exposes server directory structure
**Why it happens:** Using exception.getMessage() directly in API response
**How to avoid:** Catch IOException, return generic "Failed to save" message; log full error server-side
**Warning signs:** Error messages containing absolute paths

### Pitfall 5: Path Traversal in Collection ID
**What goes wrong:** Attacker creates collection with ID "../../../config" to overwrite files
**Why it happens:** Using user-provided ID directly in file path
**How to avoid:** Validate ID format (alphanumeric + underscore only), reject slashes and dots
**Warning signs:** Files appearing outside collections/ folder

### Pitfall 6: Not Reloading After Write
**What goes wrong:** Web panel shows new collection, but server doesn't recognize it
**Why it happens:** Forgetting to call CollectionManager.loadCollections() after file write
**How to avoid:** Always reload after successful write; return success only after reload completes
**Warning signs:** "Collection not found" in-game after creating via web

### Pitfall 7: Delete Without Checking Spawned Entities
**What goes wrong:** Orphaned collectible entities in world
**Why it happens:** Deleting collection definition while items are spawned
**How to avoid:** Accept this limitation for now (entities despawn naturally); future enhancement could clean up
**Warning signs:** Players collecting items for non-existent collections

---

## Code Examples

### Backend: Create Collection Endpoint

```java
// Source: Based on Javalin documentation + existing CollectionsController pattern
private void createCollection(Context ctx) {
    CollectionRequest request;
    try {
        request = ctx.bodyAsClass(CollectionRequest.class);
    } catch (Exception e) {
        throw new BadRequestResponse("Invalid JSON: " + e.getMessage());
    }

    // Validate request
    ValidationResult validation = validator.validate(request);
    if (!validation.isValid()) {
        ctx.status(400);
        ctx.json(new ValidationErrorResponse(
            "validation_error",
            "Collection validation failed",
            400,
            validation.errors()
        ));
        return;
    }

    try {
        // Execute on main thread
        mainThreadBridge.runSyncAndWait(() -> {
            // Check for duplicate ID
            if (plugin.getCollectionManager().hasCollection(request.id())) {
                throw new IllegalStateException("CONFLICT:" + request.id());
            }

            // Write file
            File collectionsDir = new File(plugin.getDataFolder(), "collections");
            File file = new File(collectionsDir, request.id() + ".yml");

            try {
                saveCollectionToYaml(request, file);
            } catch (IOException e) {
                throw new RuntimeException("IO_ERROR");
            }

            // Reload to activate
            plugin.getCollectionManager().loadCollections();
        }, TIMEOUT_MS);

        ctx.status(201);
        ctx.json(Map.of("success", true, "id", request.id()));

    } catch (MainThreadBridge.MainThreadException e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : "";
        if (message.startsWith("CONFLICT:")) {
            throw new ConflictResponse("Collection already exists: " + message.substring(9));
        }
        if (message.equals("IO_ERROR")) {
            throw new InternalServerErrorResponse("Failed to save collection");
        }
        throw new InternalServerErrorResponse("Server busy");
    }
}
```

### Backend: Update Collection Endpoint

```java
private void updateCollection(Context ctx) {
    String id = ctx.pathParam("id");
    CollectionRequest request;
    try {
        request = ctx.bodyAsClass(CollectionRequest.class);
    } catch (Exception e) {
        throw new BadRequestResponse("Invalid JSON");
    }

    // Ensure path ID matches body ID (or body ID is null/same)
    if (request.id() != null && !request.id().equals(id)) {
        throw new BadRequestResponse("Collection ID in URL must match body");
    }

    // Validate
    ValidationResult validation = validator.validate(request);
    if (!validation.isValid()) {
        ctx.status(400);
        ctx.json(new ValidationErrorResponse(
            "validation_error", "Validation failed", 400, validation.errors()));
        return;
    }

    try {
        mainThreadBridge.runSyncAndWait(() -> {
            // Check exists
            if (!plugin.getCollectionManager().hasCollection(id)) {
                throw new IllegalStateException("NOT_FOUND");
            }

            // Overwrite file
            File file = new File(plugin.getDataFolder(), "collections/" + id + ".yml");
            try {
                saveCollectionToYaml(request, file);
            } catch (IOException e) {
                throw new RuntimeException("IO_ERROR");
            }

            // Reload
            plugin.getCollectionManager().loadCollections();
        }, TIMEOUT_MS);

        ctx.json(Map.of("success", true, "id", id));

    } catch (MainThreadBridge.MainThreadException e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : "";
        if (message.equals("NOT_FOUND")) {
            throw new NotFoundResponse("Collection not found: " + id);
        }
        throw new InternalServerErrorResponse("Server busy");
    }
}
```

### Backend: Delete Collection Endpoint

```java
private void deleteCollection(Context ctx) {
    String id = ctx.pathParam("id");

    // Validate ID format (security)
    if (!isValidCollectionId(id)) {
        throw new BadRequestResponse("Invalid collection ID format");
    }

    try {
        mainThreadBridge.runSyncAndWait(() -> {
            // Check exists
            if (!plugin.getCollectionManager().hasCollection(id)) {
                throw new IllegalStateException("NOT_FOUND");
            }

            // Delete file
            File file = new File(plugin.getDataFolder(), "collections/" + id + ".yml");
            if (!file.delete()) {
                throw new RuntimeException("DELETE_FAILED");
            }

            // Reload
            plugin.getCollectionManager().loadCollections();
        }, TIMEOUT_MS);

        ctx.status(204); // No content

    } catch (MainThreadBridge.MainThreadException e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : "";
        if (message.equals("NOT_FOUND")) {
            throw new NotFoundResponse("Collection not found: " + id);
        }
        throw new InternalServerErrorResponse("Failed to delete collection");
    }
}

private boolean isValidCollectionId(String id) {
    // Only alphanumeric and underscore allowed
    return id != null && id.matches("^[a-z0-9_]+$");
}
```

### Backend: Reload Endpoint

```java
// POST /api/reload - Triggers full collection reload
private void reloadCollections(Context ctx) {
    try {
        mainThreadBridge.runSyncAndWait(() -> {
            plugin.getCollectionManager().loadCollections();
        }, TIMEOUT_MS);

        // Return updated collection count
        int count = plugin.getCollectionManager().getCollectionCount();
        ctx.json(Map.of("success", true, "collectionCount", count));

    } catch (MainThreadBridge.MainThreadException e) {
        throw new InternalServerErrorResponse("Reload failed");
    }
}
```

### Frontend: Collection Edit Form JavaScript

```javascript
// Source: Standard fetch API patterns
let currentCollection = null;

async function loadCollectionForEdit(id) {
    showLoading('edit-form');

    try {
        const response = await fetch('/api/collections/' + encodeURIComponent(id));
        if (!response.ok) throw new Error('Failed to load');

        currentCollection = await response.json();
        populateForm(currentCollection);
    } catch (error) {
        showError('Failed to load collection');
    }
}

function populateForm(collection) {
    document.getElementById('form-id').value = collection.id;
    document.getElementById('form-name').value = collection.name;
    document.getElementById('form-description').value = collection.description || '';
    document.getElementById('form-tier').value = collection.tier;
    document.getElementById('form-icon').value = collection.icon;

    // Populate items
    const itemsContainer = document.getElementById('items-container');
    itemsContainer.innerHTML = '';
    collection.items.forEach((item, index) => {
        addItemRow(item, index);
    });

    // Populate zones and requirements
    document.getElementById('form-zones').value = collection.zones.join(', ');
    document.getElementById('form-requires').value = collection.requires.join(', ');
}

function collectFormData() {
    const items = [];
    document.querySelectorAll('.item-row').forEach(row => {
        items.push({
            id: row.querySelector('[name="item-id"]').value,
            name: row.querySelector('[name="item-name"]').value,
            material: row.querySelector('[name="item-material"]').value,
            weight: parseInt(row.querySelector('[name="item-weight"]').value) || 10,
            soulbound: row.querySelector('[name="item-soulbound"]').checked,
            lore: row.querySelector('[name="item-lore"]').value.split('\n').filter(l => l.trim())
        });
    });

    return {
        id: document.getElementById('form-id').value,
        name: document.getElementById('form-name').value,
        description: document.getElementById('form-description').value,
        tier: document.getElementById('form-tier').value,
        icon: document.getElementById('form-icon').value,
        items: items,
        rewards: {
            experience: parseInt(document.getElementById('form-reward-xp').value) || 0,
            fireworks: document.getElementById('form-reward-fireworks').checked,
            commands: document.getElementById('form-reward-commands').value.split('\n').filter(l => l.trim()),
            message: document.getElementById('form-reward-message').value
        },
        zones: document.getElementById('form-zones').value.split(',').map(z => z.trim()).filter(z => z),
        requires: document.getElementById('form-requires').value.split(',').map(r => r.trim()).filter(r => r)
    };
}

async function handleSubmit(event) {
    event.preventDefault();
    clearValidationErrors();

    const formData = collectFormData();
    const isNew = !currentCollection || !currentCollection.id;
    const url = isNew ? '/api/collections' : '/api/collections/' + encodeURIComponent(formData.id);
    const method = isNew ? 'POST' : 'PUT';

    try {
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(formData)
        });

        if (response.status === 400) {
            const errorData = await response.json();
            displayValidationErrors(errorData.errors);
            return;
        }

        if (response.status === 409) {
            displayValidationErrors([{ field: 'id', message: 'Collection ID already exists' }]);
            return;
        }

        if (!response.ok) {
            throw new Error('Server error');
        }

        showSuccess(isNew ? 'Collection created!' : 'Collection saved!');
        window.location.hash = '#collection/' + encodeURIComponent(formData.id);

    } catch (error) {
        showError('Failed to save collection. Please try again.');
    }
}
```

### Frontend: Validation Error Display CSS

```css
/* Form validation styles */
.form-group {
    margin-bottom: 1rem;
}

.form-group.has-error input,
.form-group.has-error select,
.form-group.has-error textarea {
    border-color: #ff4444;
    background-color: rgba(255, 68, 68, 0.1);
}

.error-message {
    display: block;
    color: #ff4444;
    font-size: 0.85rem;
    margin-top: 0.25rem;
    min-height: 1.2em;
}

.error-message:empty {
    display: none;
}

/* Delete confirmation modal */
.modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.7);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}

.modal-content {
    background: var(--bg-secondary);
    padding: 2rem;
    border-radius: 8px;
    max-width: 400px;
    text-align: center;
}

.modal-actions {
    display: flex;
    gap: 1rem;
    justify-content: center;
    margin-top: 1.5rem;
}

.btn-danger {
    background: #ff4444;
    color: white;
}

.btn-danger:hover {
    background: #ff2222;
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Raw string YAML manipulation | YamlConfiguration.save() | Always | Handles escaping, formatting |
| Manual error message strings | Structured FieldError objects | Modern APIs | Machine-readable for frontend |
| Page reload after submit | SPA with partial updates | HTML5 era | Better UX, no flash |
| window.confirm() for delete | Custom modal dialog | Modern UX | Consistent branding |

**Notes:**
- Javalin 6.x uses HttpResponseException subclasses (BadRequestResponse, etc.) - not custom exception types
- ctx.bodyAsClass() requires Gson adapter registration (already done in Phase 18)

---

## Open Questions

1. **YAML Comments Preservation**
   - What we know: YamlConfiguration.save() does not preserve comments from original file
   - What's unclear: Should we preserve user comments in collection files?
   - Recommendation: Accept limitation for Phase 20; users editing via web panel lose comments

2. **Partial Updates (PATCH)**
   - What we know: PUT replaces entire collection, no PATCH support planned
   - What's unclear: Should we support updating single fields?
   - Recommendation: Defer to future phase; full replacement is simpler and sufficient for initial release

3. **Concurrent Edits**
   - What we know: No optimistic locking mechanism
   - What's unclear: What happens if two admins edit simultaneously?
   - Recommendation: Last-write-wins for Phase 20; add versioning/timestamps in future

4. **Spawned Entity Cleanup on Delete**
   - What we know: Deleting a collection leaves entities in world
   - What's unclear: Should delete remove spawned collectibles?
   - Recommendation: Accept natural despawn for Phase 20; add cleanup in future

---

## Sources

### Primary (HIGH confidence)
- [Javalin Documentation](https://javalin.io/documentation) - POST/PUT/DELETE handlers, bodyAsClass(), response exceptions
- Existing codebase: CollectionsController.java, CollectionManager.java, MainThreadBridge.java
- Existing codebase: Collection.java model record with validation
- [Bukkit Configuration API](https://bukkit.fandom.com/wiki/Configuration_API_Reference) - YamlConfiguration.save()
- [Spigot YamlConfiguration Javadocs](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/configuration/file/YamlConfiguration.html)

### Secondary (MEDIUM confidence)
- [RFC 7807 Problem Details](https://blog.restcase.com/rest-api-error-handling-problem-details-response/) - Error response format
- [Validation Error Structuring](https://medium.com/@k3nn7/structuring-validation-errors-in-rest-apis-40c15fbb7bc3) - Field error patterns
- [Javalin CRUD Examples](https://github.com/brunolellis/javalin-java-example) - REST API patterns

### Tertiary (LOW confidence)
- [SnakeYAML Error Handling](https://www.baeldung.com/java-snake-yaml) - Line number extraction (not needed with YamlConfiguration)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - No new dependencies, all verified against existing codebase
- CRUD endpoints: HIGH - Standard Javalin patterns, well-documented
- YAML validation: HIGH - Using existing YamlConfiguration, known behavior
- Thread safety: HIGH - MainThreadBridge already proven in Phase 19
- Frontend forms: MEDIUM - Standard patterns but more complex than Phase 19 read-only
- Error response format: MEDIUM - Following industry standards but custom implementation

**Research date:** 2026-01-23
**Valid until:** 2026-03-23 (60 days - stable patterns, no major changes expected)
